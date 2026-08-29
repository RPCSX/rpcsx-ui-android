
package net.rpcsx.overlay

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import net.rpcsx.Digital1Flags
import net.rpcsx.utils.GeneralSettings
import net.rpcsx.utils.GeneralSettings.boolean
import net.rpcsx.utils.InputBindingPrefs
import kotlin.math.roundToInt

class PadOverlayButton(
    resources: Resources,
    image: Bitmap,
    private val digital1: Int,
    private val digital2: Int
) : PadOverlayItem, BitmapDrawable(resources, image) {

    companion object {
        private const val BASE_BITMAP_SIZE = 1024f
        private const val MAX_ALPHA = 255
        private const val DEFAULT_OPACITY = 50
    }

    private var pressed = false
    private var locked = -1
    private var origAlpha = alpha
    private var offsetX = 0
    private var offsetY = 0

    var defaultSize: Pair<Int, Int> = Pair(-1, -1)
    lateinit var defaultPosition: Pair<Int, Int>

    override var dragging = false

    override var enabled: Boolean = GeneralSettings[prefKey("enabled")].boolean(true)
        set(value) {
            field = value
            GeneralSettings.setValue(prefKey("enabled"), value)
        }

    override fun scale() = GeneralSettings[prefKey("scale")] as? Int ?: measureDefaultScale()

    private fun prefKey(suffix: String): String = "button_${digital1}_${digital2}_$suffix"

    override fun bounds(): Rect = bounds
    override fun draw(canvas: Canvas) = super.draw(canvas)
    override fun contains(x: Int, y: Int) = bounds.contains(x, y)

    override fun onTouch(event: MotionEvent, pointerIndex: Int, padState: State): Boolean {
        val action = event.actionMasked
        var hit = false

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (locked == -1) {
                    locked = event.getPointerId(pointerIndex)
                    pressed = true
                    origAlpha = alpha
                    alpha = MAX_ALPHA
                    hit = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (locked != -1 && (action == MotionEvent.ACTION_CANCEL || event.getPointerId(pointerIndex) == locked)) {
                    pressed = false
                    locked = -1
                    alpha = origAlpha
                    hit = true
                }
            }
        }

        if (pressed) {
            padState.digital[0] = padState.digital[0] or digital1
            padState.digital[1] = padState.digital[1] or digital2
        } else {
            padState.digital[0] = padState.digital[0] and digital1.inv()
            padState.digital[1] = padState.digital[1] and digital2.inv()
        }

        return hit
    }

    override fun startDragging(startX: Int, startY: Int) {
        dragging = true
        offsetX = startX - bounds.left
        offsetY = startY - bounds.top
    }

    override fun updatePosition(x: Int, y: Int, force: Boolean) {
        val newLeft: Int
        val newTop: Int

        if (dragging) {
            newLeft = x - offsetX
            newTop = y - offsetY
        } else if (force) {
            newLeft = x
            newTop = y
        } else {
            return
        }

        setBounds(newLeft, newTop, newLeft + bounds.width(), newTop + bounds.height())
        GeneralSettings.setValue(prefKey("x"), newLeft)
        GeneralSettings.setValue(prefKey("y"), newTop)
    }

    override fun stopDragging() {
        dragging = false
    }

    override fun setScale(percent: Int) {
        val scaleFactor = percent / 100f
        val newSize = (BASE_BITMAP_SIZE * scaleFactor).roundToInt()

        setBounds(bounds.left, bounds.top, bounds.left + newSize, bounds.top + newSize)
        GeneralSettings.setValue(prefKey("scale"), percent)
    }

    override fun setOpacity(percent: Int) {
        alpha = (MAX_ALPHA * (percent / 100f)).roundToInt()
        GeneralSettings.setValue(prefKey("opacity"), percent)
    }

    fun measureDefaultScale(): Int {
        if (defaultSize.first <= 0 || defaultSize.second <= 0) return 100

        val widthScale = (defaultSize.second / BASE_BITMAP_SIZE) * 100
        val heightScale = (defaultSize.first / BASE_BITMAP_SIZE) * 100
        return minOf(widthScale, heightScale).roundToInt()
    }

    override fun resetConfigs() {
        setOpacity(DEFAULT_OPACITY)
        setBounds(
            defaultPosition.first,
            defaultPosition.second,
            defaultPosition.first + defaultSize.second,
            defaultPosition.second + defaultSize.first
        )

        listOf("scale", "opacity", "x", "y").forEach { keySuffix ->
            GeneralSettings.setValue(prefKey(keySuffix), null)
        }
    }

    fun getInfo(): Triple<String, Int, Int> {
        val isDigital2 = digital1 == Digital1Flags.None.ordinal
        val digitalIndex = if (isDigital2) 1 else 0
        val keyCode = if (isDigital2) digital2 else digital1

        val name = InputBindingPrefs.rpcsxKeyCodeToString(keyCode, digitalIndex)
        val scale = GeneralSettings[prefKey("scale")] as? Int ?: measureDefaultScale()
        val opacity = GeneralSettings[prefKey("opacity")] as? Int ?: DEFAULT_OPACITY

        return Triple(name, scale, opacity)
    }
}