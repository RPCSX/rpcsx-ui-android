package net.rpcsx.overlay

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import kotlin.math.roundToInt
import net.rpcsx.utils.GeneralSettings
import net.rpcsx.utils.GeneralSettings.boolean
import net.rpcsx.utils.GeneralSettings.int

private enum class DpadButton(val bit: Int) {
    Top(1 shl 0), Left(1 shl 1), Right(1 shl 2), Bottom(1 shl 3);
}

private class DpadState(var mask: Int = 0) {
    fun isActive(btn: DpadButton): Boolean {
        return (mask and btn.bit) == btn.bit
    }

    fun setBtn(btn: DpadButton) {
        mask = mask or btn.bit
    }

    fun clear() {
        mask = 0
    }
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
) {
    private val originalButtonWidth = buttonWidth
    private val originalButtonHeight = buttonHeight
    private val drawableTop = imgTop.toDrawable(resources)
    private val drawableLeft = imgLeft.toDrawable(resources)
    private val drawableRight = imgRight.toDrawable(resources)
    private val drawableBottom = imgBottom.toDrawable(resources)
    private val locked = arrayOf(-1, -1)
    private val btnState = arrayOf(DpadState(), DpadState())
    private val digitalBits = arrayOf(0, 0)
    private var offsetX = 0
    private var offsetY = 0

    // stores the default datas
    private val defaultArea = Rect(area)
    private val defaultButtonWidth = buttonWidth
    private val defaultButtonHeight = buttonHeight
    var idleAlpha: Int = 255
    var dragging: Boolean = false

    var enabled: Boolean = GeneralSettings["${inputId}_enabled"].boolean(true)
        set(value) {
            field = value
            GeneralSettings.setValue("${inputId}_enabled", value)
        }

    init {
        loadSavedPosition()
    }

    fun contains(x: Int, y: Int) = area.contains(x, y)

    fun startDragging(x: Int, y: Int) {
        dragging = true
        offsetX = x - area.left
        offsetY = y - area.top
    }

    fun updatePosition(x: Int, y: Int, force: Boolean = false) {
        if (!dragging && !force) return

        val newLeft = if (!force) x - offsetX else x
        val newTop = if (!force) y - offsetY else y
        val newRight = newLeft + area.width()
        val newBottom = newTop + area.height()

        area.set(newLeft, newTop, newRight, newBottom)
        updateBounds()
        
        GeneralSettings.setValue("${inputId}_x", area.left)
        GeneralSettings.setValue("${inputId}_y", area.top)
    }

    fun stopDragging() {
        dragging = false
    }

    fun setScale(percent: Int) {
        val scaleFactor = percent / 100f
        val newWidth = (1024 * scaleFactor).roundToInt()
        val newHeight = (1024 * scaleFactor).roundToInt()
        val centerX = area.centerX()
        val centerY = area.centerY()

        area.set(
            centerX - newWidth / 2,
            centerY - newHeight / 2,
            centerX + newWidth / 2,
            centerY + newHeight / 2
        )

        val defaultScaleWidth = defaultArea.width().toFloat() / 1024
        val defaultScaleHeight = defaultArea.height().toFloat() / 1024

        buttonWidth = (originalButtonWidth / defaultScaleWidth * scaleFactor).toInt()
        buttonHeight = (originalButtonHeight / defaultScaleHeight * scaleFactor).toInt()
        updateBounds()

        GeneralSettings.setValue("${inputId}_x", area.left)
        GeneralSettings.setValue("${inputId}_y", area.top)
        GeneralSettings.setValue("${inputId}_scale", percent)
    }

    fun setOpacity(percent: Int) {
        idleAlpha = (255 * percent / 100).coerceIn(0, 255)
        GeneralSettings.setValue("${inputId}_opacity", percent)
    }

    fun resetConfigs() {
        GeneralSettings.setValue("${inputId}_x", null)
        GeneralSettings.setValue("${inputId}_y", null)
        GeneralSettings.setValue("${inputId}_scale", null)
        GeneralSettings.setValue("${inputId}_opacity", null)
        area = Rect(defaultArea)
        setOpacity(50)
        buttonWidth = defaultButtonWidth
        buttonHeight = defaultButtonHeight
        updateBounds()
    }

    private fun loadSavedPosition() {
        val x = GeneralSettings["${inputId}_x"].int(area.left)
        val y = GeneralSettings["${inputId}_y"].int(area.top)
        val scale = GeneralSettings["${inputId}_scale"].int(-1)
        updatePosition(x, y, force = true)
        if (scale != -1) setScale(scale)
    }

    private fun measureDefaultScale(): Int {
        val widthScale = defaultArea.width().toFloat() / 1024 * 100
        val heightScale = defaultArea.height().toFloat() / 1024 * 100
        return minOf(widthScale, heightScale).roundToInt()
    }

    fun getInfo(): Triple<String, Int, Int> {
        return Triple(
            "Dpad", 
            GeneralSettings["${inputId}_scale"].int(measureDefaultScale()), 
            GeneralSettings["${inputId}_opacity"].int(50)
        )
    }

    private fun updateBounds() {
        drawableTop.setBounds(
            area.centerX() - buttonWidth / 2,
            area.top,
            area.centerX() + buttonWidth / 2,
            area.top + buttonHeight,
        )

        drawableBottom.setBounds(
            area.centerX() - buttonWidth / 2,
            area.bottom - buttonHeight,
            area.centerX() + buttonWidth / 2,
            area.bottom,
        )

        drawableLeft.setBounds(
            area.left,
            area.centerY() - buttonWidth / 2,
            area.left + buttonHeight,
            area.centerY() + buttonWidth / 2,
        )

        drawableRight.setBounds(
            area.right - buttonHeight,
            area.centerY() - buttonWidth / 2,
            area.right,
            area.centerY() + buttonWidth / 2,
        )
    }

    fun onTouch(event: MotionEvent, pointerIndex: Int, padState: State): Boolean {
        val action = event.actionMasked
        var hit = false

        for (touchIndex in 0..1) {
            if (!multitouch && touchIndex > 0) {
                break
            }

            var activePointerIndex = pointerIndex

            if (locked[touchIndex] != -1 && action == MotionEvent.ACTION_MOVE) {
                activePointerIndex = -1
                for (i in 0..<event.pointerCount) {
                    if (locked[touchIndex] == event.getPointerId(i)) {
                        activePointerIndex = i
                        break
                    }
                }

                if (activePointerIndex == -1) {
                    continue
                }
            }

            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN || (action == MotionEvent.ACTION_MOVE && locked[touchIndex] != -1)) {
                if (action != MotionEvent.ACTION_MOVE) {
                    if (locked[touchIndex] == -1) {
                        locked[touchIndex] = event.getPointerId(pointerIndex)
                    } else if (locked[touchIndex] != event.getPointerId(pointerIndex)) {
                        continue
                    }
                }

                val x = event.getX(activePointerIndex)
                val y = event.getY(activePointerIndex)

                val leftDistance = x - area.left
                val topDistance = y - area.top
                val bottomDistance = area.bottom - y
                val rightDistance = area.right - x
                val distanceWidth = area.width() / 3.5

                val left = leftDistance < distanceWidth
                val right = !left && rightDistance < distanceWidth
                val top = topDistance < distanceWidth
                val bottom = !top && bottomDistance < distanceWidth

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
                if (locked[touchIndex] != -1 && (action == MotionEvent.ACTION_CANCEL || event.getPointerId(
                        pointerIndex
                    ) == locked[touchIndex])
                ) {
                    hit = true
                    digitalBits[touchIndex] = 0
                    btnState[touchIndex].clear()
                    locked[touchIndex] = -1
                }
            }

            if (hit) {
                break
            }
        }

        padState.digital[digitalIndex] =
            (padState.digital[digitalIndex] and (leftBit or rightBit or topBit or bottomBit).inv()) or digitalBits[0] or digitalBits[1]

        return hit || area.contains(
            event.getX(pointerIndex).toInt(), event.getY(pointerIndex).toInt()
        )
    }

    fun getBounds(): Rect {
        return area
    }

    fun draw(canvas: Canvas) {
        drawableLeft.alpha =
            if (btnState[0].isActive(DpadButton.Left) || btnState[1].isActive(DpadButton.Left)) 255 else idleAlpha
        drawableLeft.draw(canvas)

        drawableRight.alpha =
            if (btnState[0].isActive(DpadButton.Right) || btnState[1].isActive(DpadButton.Right)) 255 else idleAlpha
        drawableRight.draw(canvas)

        drawableBottom.alpha =
            if (btnState[0].isActive(DpadButton.Bottom) || btnState[1].isActive(DpadButton.Bottom)) 255 else idleAlpha
        drawableBottom.draw(canvas)

        drawableTop.alpha =
            if (btnState[0].isActive(DpadButton.Top) || btnState[1].isActive(DpadButton.Top)) 255 else idleAlpha
        drawableTop.draw(canvas)
    }
}
