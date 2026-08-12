package net.rpcsx.overlay

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.core.graphics.drawable.toDrawable
import kotlin.math.hypot

class PadOverlayStick(
    resources: Resources,
    private val isLeft: Boolean,
    bg: Bitmap,
    stick: Bitmap,
    private val pressDigitalIndex: Int = 0,
    private val pressBit: Int = 0
) : BitmapDrawable(resources, stick) {

    companion object {
        const val TOUCH_RESULT_HANDLED = 1
        const val TOUCH_RESULT_IGNORED = 0
        const val TOUCH_RESULT_RELEASED = -1

        private const val CENTER_AXIS_VALUE = 127
        private const val MAX_AXIS_OFFSET = 127
        private const val AXIS_BIAS = 128
    }

    private val bgDrawable = bg.toDrawable(resources)
    private var locked = -1
    private var pressX = -1
    private var pressY = -1
    private var bgOffsetX = 0
    private var bgOffsetY = 0

    fun contains(x: Int, y: Int): Boolean = bounds.contains(x, y)

    fun isActive(): Boolean = locked != -1

    fun onAdd(event: MotionEvent, pointerIndex: Int) {
        locked = event.getPointerId(pointerIndex)
        val x = event.getX(pointerIndex).toInt()
        val y = event.getY(pointerIndex).toInt()

        pressX = x
        pressY = y

        val halfWidth = bounds.width() / 2
        val halfHeight = bounds.height() / 2
        setBounds(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight)
    }

    fun onTouch(event: MotionEvent, pointerIndex: Int, padState: State): Int {
        val action = event.actionMasked

        val isInitialPress = pressBit != 0 && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)
        val isDragging = locked != -1 && action == MotionEvent.ACTION_MOVE

        if (isInitialPress || isDragging) {
            var activePointerIndex = pointerIndex

            if (action != MotionEvent.ACTION_MOVE) {
                if (locked == -1) {
                    locked = event.getPointerId(pointerIndex)
                    pressX = event.getX(pointerIndex).toInt()
                    pressY = event.getY(pointerIndex).toInt()

                    bgOffsetX = bgDrawable.bounds.centerX() - pressX
                    bgOffsetY = bgDrawable.bounds.centerY() - pressY

                    bgDrawable.setBounds(
                        bgDrawable.bounds.left - bgOffsetX,
                        bgDrawable.bounds.top - bgOffsetY,
                        bgDrawable.bounds.right - bgOffsetX,
                        bgDrawable.bounds.bottom - bgOffsetY
                    )
                } else if (locked != event.getPointerId(pointerIndex)) {
                    return TOUCH_RESULT_IGNORED
                }
            } else {
                for (i in 0 until event.pointerCount) {
                    if (locked == event.getPointerId(i)) {
                        activePointerIndex = i
                        break
                    }
                }

                if (activePointerIndex == -1) {
                    return TOUCH_RESULT_IGNORED
                }
            }

            if (pressBit != 0) {
                padState.digital[pressDigitalIndex] = padState.digital[pressDigitalIndex] or pressBit
            }

            val bgCenterX = pressX
            val bgCenterY = pressY

            var relX = event.getX(activePointerIndex) - bgCenterX
            var relY = event.getY(activePointerIndex) - bgCenterY

            val bgR = hypot((bgDrawable.bounds.left - bgCenterX).toFloat(), (bgDrawable.bounds.top - bgCenterY).toFloat())
            val stickR = hypot(relX, relY)

            if (stickR > bgR && stickR > 0f) {
                val scaleFactor = bgR / stickR
                relX *= scaleFactor
                relY *= scaleFactor
            }

            val stickAxisX = ((relX / bgR) * MAX_AXIS_OFFSET + AXIS_BIAS).toInt()
            val stickAxisY = ((relY / bgR) * MAX_AXIS_OFFSET + AXIS_BIAS).toInt()

            updateStickAxis(padState, stickAxisX, stickAxisY)

            val finalX = relX + bgCenterX
            val finalY = relY + bgCenterY

            val halfWidth = bounds.width() / 2
            val halfHeight = bounds.height() / 2

            super.setBounds(
                finalX.toInt() - halfWidth,
                finalY.toInt() - halfHeight,
                finalX.toInt() + halfWidth,
                finalY.toInt() + halfHeight
            )

            return TOUCH_RESULT_HANDLED
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
            if (locked != -1 && (action == MotionEvent.ACTION_CANCEL || event.getPointerId(pointerIndex) == locked)) {
                locked = -1

                bgDrawable.setBounds(
                    bgDrawable.bounds.left + bgOffsetX,
                    bgDrawable.bounds.top + bgOffsetY,
                    bgDrawable.bounds.right + bgOffsetX,
                    bgDrawable.bounds.bottom + bgOffsetY
                )
                bgOffsetX = 0
                bgOffsetY = 0

                if (pressBit != 0) {
                    padState.digital[pressDigitalIndex] = padState.digital[pressDigitalIndex] and pressBit.inv()
                }

                super.setBounds(bgDrawable.bounds)
                updateStickAxis(padState, CENTER_AXIS_VALUE, CENTER_AXIS_VALUE)

                return TOUCH_RESULT_RELEASED
            }
        }

        return TOUCH_RESULT_IGNORED
    }

    private fun updateStickAxis(padState: State, x: Int, y: Int) {
        if (isLeft) {
            padState.leftStickX = x
            padState.leftStickY = y
        } else {
            padState.rightStickX = x
            padState.rightStickY = y
        }
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)
        bgDrawable.setBounds(left, top, right, bottom)
    }

    override fun setAlpha(alpha: Int) {
        super.setAlpha(alpha)
        bgDrawable.alpha = alpha
    }

    override fun draw(canvas: Canvas) {
        bgDrawable.draw(canvas)
        super.draw(canvas)
    }
}