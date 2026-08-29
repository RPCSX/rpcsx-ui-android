package net.rpcsx.overlay

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.core.graphics.drawable.toDrawable
import net.rpcsx.utils.GeneralSettings
import net.rpcsx.utils.GeneralSettings.boolean
import net.rpcsx.utils.GeneralSettings.int
import kotlin.math.roundToInt

private enum class DpadButton(val bit: Int) {
    Top(1 shl 0),
    Left(1 shl 1),
    Right(1 shl 2),
    Bottom(1 shl 3)
}

private class DpadState(var mask: Int = 0) {
    fun isActive(btn: DpadButton): Boolean = (mask and btn.bit) == btn.bit
    fun setBtn(btn: DpadButton) { mask = mask or btn.bit }
    fun clear() { mask = 0 }
}

class PadOverlayDpad(
    resources: Resources,
    private var buttonWidth: Int,
    private var buttonHeight: Int,
    private val inputId: String,
    private var area: Rect,
    private val digitalIndex: Int,
    imgTop: Bitmap,
    private val topBit: Int,
    imgLeft: Bitmap,
    private val leftBit: Int,
    imgRight: Bitmap,
    private val rightBit: Int,
    imgBottom: Bitmap,
    private val bottomBit: Int,
    private val multitouch: Boolean
) : PadOverlayItem {

    companion object {
        private const val BASE_BITMAP_SIZE = 1024f
        private const val MAX_ALPHA = 255
        private const val DEFAULT_OPACITY = 50
        private const val HITBOX_RATIO = 3.5
    }

    private val originalButtonWidth = buttonWidth
    private val originalButtonHeight = buttonHeight

    private val drawableTop = imgTop.toDrawable(resources)
    private val drawableLeft = imgLeft.toDrawable(resources)
    private val drawableRight = imgRight.toDrawable(resources)
    private val drawableBottom = imgBottom.toDrawable(resources)

    private val locked = intArrayOf(-1, -1)
    private val btnState = arrayOf(DpadState(), DpadState())
    private val digitalBits = intArrayOf(0, 0)

    private var offsetX = 0
    private var offsetY = 0

    private val defaultArea = Rect(area)
    private val defaultButtonWidth = buttonWidth
    private val defaultButtonHeight = buttonHeight

    var idleAlpha: Int = MAX_ALPHA
    override var dragging: Boolean = false

    override var enabled: Boolean = GeneralSettings[prefKey("enabled")].boolean(true)
        set(value) {
            field = value
            GeneralSettings.setValue(prefKey("enabled"), value)
        }

    override fun scale() = GeneralSettings[prefKey("scale")].int(measureDefaultScale())

    init {
        loadSavedPosition()
    }

    private fun prefKey(suffix: String): String = "${inputId}_$suffix"

    override fun contains(x: Int, y: Int) = area.contains(x, y)

    override fun startDragging(startX: Int, startY: Int) {
        dragging = true
        offsetX = startX - area.left
        offsetY = startY - area.top
    }

    override fun updatePosition(x: Int, y: Int, force: Boolean) {
        if (!dragging && !force) return

        val newLeft = if (!force) x - offsetX else x
        val newTop = if (!force) y - offsetY else y

        area.set(newLeft, newTop, newLeft + area.width(), newTop + area.height())
        updateBounds()

        GeneralSettings.setValue(prefKey("x"), area.left)
        GeneralSettings.setValue(prefKey("y"), area.top)
    }

    override fun stopDragging() {
        dragging = false
    }

    private fun setScale(percent: Int, centerX: Int, centerY: Int) {
        val scaleFactor = percent / 100f
        val newWidth = (BASE_BITMAP_SIZE * scaleFactor).roundToInt()
        val newHeight = (BASE_BITMAP_SIZE * scaleFactor).roundToInt()

        area.set(
            centerX - newWidth / 2,
            centerY - newHeight / 2,
            centerX + newWidth / 2,
            centerY + newHeight / 2
        )

        val defaultScaleWidth = defaultArea.width() / BASE_BITMAP_SIZE
        val defaultScaleHeight = defaultArea.height() / BASE_BITMAP_SIZE

        buttonWidth = (originalButtonWidth / defaultScaleWidth * scaleFactor).toInt()
        buttonHeight = (originalButtonHeight / defaultScaleHeight * scaleFactor).toInt()
        updateBounds()

        GeneralSettings.setValue(prefKey("x"), area.left)
        GeneralSettings.setValue(prefKey("y"), area.top)
        GeneralSettings.setValue(prefKey("scale"), percent)
    }

    override fun setScale(percent: Int) {
        setScale(percent, area.centerX(), area.centerY())
    }

    override fun setOpacity(percent: Int) {
        idleAlpha = (MAX_ALPHA * percent / 100).coerceIn(0, MAX_ALPHA)
        GeneralSettings.setValue(prefKey("opacity"), percent)
    }

    override fun resetConfigs() {
        area = Rect(defaultArea)
        setOpacity(DEFAULT_OPACITY)
        buttonWidth = defaultButtonWidth
        buttonHeight = defaultButtonHeight
        updateBounds()

        listOf("x", "y", "scale", "opacity").forEach { suffix ->
            GeneralSettings.setValue(prefKey(suffix), null)
        }
    }

    private fun loadSavedPosition() {
        val scale = GeneralSettings[prefKey("scale")].int(-1)
        val x = GeneralSettings[prefKey("x")].int(area.left)
        val y = GeneralSettings[prefKey("y")].int(area.top)

        if (scale != -1) {
            val centerX = x + area.width() / 2
            val centerY = y + area.height() / 2
            setScale(scale, centerX, centerY)
        } else {
            updatePosition(x, y, force = true)
        }
    }

    private fun measureDefaultScale(): Int {
        val widthScale = (defaultArea.width() / BASE_BITMAP_SIZE) * 100
        val heightScale = (defaultArea.height() / BASE_BITMAP_SIZE) * 100
        return minOf(widthScale, heightScale).roundToInt()
    }

    fun getInfo(): Triple<String, Int, Int> {
        val name = if (inputId == "dpad") "Directional Pad" else "Face Buttons"
        val scale = GeneralSettings[prefKey("scale")].int(measureDefaultScale())
        val opacity = GeneralSettings[prefKey("opacity")].int(DEFAULT_OPACITY)

        return Triple(name, scale, opacity)
    }

    private fun updateBounds() {
        val halfBtnWidth = buttonWidth / 2

        drawableTop.setBounds(
            area.centerX() - halfBtnWidth,
            area.top,
            area.centerX() + halfBtnWidth,
            area.top + buttonHeight
        )

        drawableBottom.setBounds(
            area.centerX() - halfBtnWidth,
            area.bottom - buttonHeight,
            area.centerX() + halfBtnWidth,
            area.bottom
        )

        drawableLeft.setBounds(
            area.left,
            area.centerY() - halfBtnWidth,
            area.left + buttonHeight,
            area.centerY() + halfBtnWidth
        )

        drawableRight.setBounds(
            area.right - buttonHeight,
            area.centerY() - halfBtnWidth,
            area.right,
            area.centerY() + halfBtnWidth
        )
    }

    override fun onTouch(event: MotionEvent, pointerIndex: Int, padState: State): Boolean {
        val action = event.actionMasked
        var hit = false

        for (touchIndex in 0..1) {
            if (!multitouch && touchIndex > 0) break

            var activePointerIndex = pointerIndex

            // Validate existing pointer during MOVE action
            if (locked[touchIndex] != -1 && action == MotionEvent.ACTION_MOVE) {
                activePointerIndex = -1
                for (i in 0 until event.pointerCount) {
                    if (locked[touchIndex] == event.getPointerId(i)) {
                        activePointerIndex = i
                        break
                    }
                }
                if (activePointerIndex == -1) continue
            }

            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN ||
                (action == MotionEvent.ACTION_MOVE && locked[touchIndex] != -1)) {

                if (action != MotionEvent.ACTION_MOVE) {
                    if (locked[touchIndex] == -1) {
                        locked[touchIndex] = event.getPointerId(pointerIndex)
                    } else if (locked[touchIndex] != event.getPointerId(pointerIndex)) {
                        continue
                    }
                }

                val x = event.getX(activePointerIndex)
                val y = event.getY(activePointerIndex)

                val distanceWidth = area.width() / HITBOX_RATIO

                val left = (x - area.left) < distanceWidth
                val right = !left && (area.right - x) < distanceWidth
                val top = (y - area.top) < distanceWidth
                val bottom = !top && (area.bottom - y) < distanceWidth

                hit = true
                digitalBits[touchIndex] = 0
                btnState[touchIndex].clear()

                if (top) {
                    btnState[touchIndex].setBtn(DpadButton.Top)
                    digitalBits[touchIndex] = digitalBits[touchIndex] or topBit
                }
                if (left) {
                    btnState[touchIndex].setBtn(DpadButton.Left)
                    digitalBits[touchIndex] = digitalBits[touchIndex] or leftBit
                }
                if (right) {
                    btnState[touchIndex].setBtn(DpadButton.Right)
                    digitalBits[touchIndex] = digitalBits[touchIndex] or rightBit
                }
                if (bottom) {
                    btnState[touchIndex].setBtn(DpadButton.Bottom)
                    digitalBits[touchIndex] = digitalBits[touchIndex] or bottomBit
                }

            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
                if (locked[touchIndex] != -1 && (action == MotionEvent.ACTION_CANCEL || event.getPointerId(pointerIndex) == locked[touchIndex])) {
                    hit = true
                    digitalBits[touchIndex] = 0
                    btnState[touchIndex].clear()
                    locked[touchIndex] = -1
                }
            }

            if (hit) break
        }

        val bitmaskClear = (leftBit or rightBit or topBit or bottomBit).inv()
        padState.digital[digitalIndex] = (padState.digital[digitalIndex] and bitmaskClear) or digitalBits[0] or digitalBits[1]

        return hit || area.contains(event.getX(pointerIndex).toInt(), event.getY(pointerIndex).toInt())
    }

    override fun bounds(): Rect = area

    override fun draw(canvas: Canvas) {
        drawDirectionalButton(canvas, drawableLeft, DpadButton.Left)
        drawDirectionalButton(canvas, drawableRight, DpadButton.Right)
        drawDirectionalButton(canvas, drawableTop, DpadButton.Top)
        drawDirectionalButton(canvas, drawableBottom, DpadButton.Bottom)
    }

    private fun drawDirectionalButton(canvas: Canvas, drawable: Drawable, button: DpadButton) {
        drawable.alpha = if (btnState[0].isActive(button) || btnState[1].isActive(button)) MAX_ALPHA else idleAlpha
        drawable.draw(canvas)
    }
}

