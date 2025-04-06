package net.rpcsx.overlay

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import android.os.Vibrator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.VectorDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import net.rpcsx.R
import net.rpcsx.Digital1Flags
import net.rpcsx.Digital2Flags
import net.rpcsx.RPCSX
import net.rpcsx.utils.GeneralSettings
import kotlin.math.min


private const val idleAlpha = (0.3 * 255).toInt()

data class State(
    val digital: IntArray = IntArray(2),
    var leftStickX: Int = 127,
    var leftStickY: Int = 127,
    var rightStickX: Int = 127,
    var rightStickY: Int = 127
)

class PadOverlay(context: Context?, attrs: AttributeSet?) : SurfaceView(context, attrs) {
    private val buttons: Array<PadOverlayButton>
    private val dpad: PadOverlayDpad
    private val triangleSquareCircleCross: PadOverlayDpad
    private val state = State()
    private val leftStick: PadOverlayStick
    private val rightStick: PadOverlayStick
    private val floatingSticks = arrayOf<PadOverlayStick?>(null, null)
    private val sticks = mutableListOf<PadOverlayStick>()
    private val prefs by lazy { context!!.getSharedPreferences("PadOverlayPrefs", Context.MODE_PRIVATE) }
    private var selectedInput: Any? = null
        set(value) {
            field = value
            onSelectedInputChange?.invoke(value!!)
        }
        
    var onSelectedInputChange: ((Any) -> Unit)? = null
    var isEditing = false
    
    private val outlinePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val yellowOutlinePaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    init {
        val metrics = context!!.resources.displayMetrics
        val totalWidth = metrics.widthPixels
        val totalHeight = metrics.heightPixels
        val sizeHint = min(totalHeight, totalWidth)
        val buttonSize = sizeHint / 10

        val btnAreaW = buttonSize * 3
        val btnAreaH = buttonSize * 3

        val btnAreaX = totalWidth - btnAreaW - buttonSize
        val btnAreaY = totalHeight - btnAreaH - buttonSize / 2
        val btnDistance = buttonSize / 8

        val dpadW = buttonSize * 3 - btnDistance / 2
        val dpadH = buttonSize * 3 - btnDistance / 2

        val dpadAreaX = buttonSize
        val dpadAreaY = btnAreaY

        val startSelectSize = (buttonSize * 1.5).toInt()
        val btnStartX = totalWidth / 2 + buttonSize * 2
        val btnStartY = buttonSize / 2
        val btnSelectX = totalWidth / 2 - startSelectSize - buttonSize * 2
        val btnSelectY = btnStartY

        val btnL2X = buttonSize
        val btnL2Y = buttonSize

        val btnL1X = btnL2X
        val btnL1Y = btnL2Y + buttonSize + buttonSize / 2

        val btnR2X = totalWidth - buttonSize * 2
        val btnR2Y = btnL2Y

        val btnR1X = btnR2X
        val btnR1Y = btnR2Y + buttonSize + buttonSize / 2

        val btnHomeX = totalWidth / 2 -  buttonSize / 2
        val btnHomeY = btnStartY + (startSelectSize - buttonSize) / 2

        dpad = createDpad(
            "dpad", dpadAreaX, dpadAreaY, dpadW, dpadH,
            dpadW / 2,
            dpadH / 2 - dpadH / 20,
            0,
            R.drawable.dpad_top,
            Digital1Flags.CELL_PAD_CTRL_UP.bit,
            R.drawable.dpad_left,
            Digital1Flags.CELL_PAD_CTRL_LEFT.bit,
            R.drawable.dpad_right,
            Digital1Flags.CELL_PAD_CTRL_RIGHT.bit,
            R.drawable.dpad_bottom,
            Digital1Flags.CELL_PAD_CTRL_DOWN.bit,
            false
        )

        triangleSquareCircleCross = createDpad(
            "triangleSquareCircleCross", btnAreaX - buttonSize / 2, btnAreaY, buttonSize * 3, buttonSize * 3,
            buttonSize,
            buttonSize,
            1,
            R.drawable.triangle,
            Digital2Flags.CELL_PAD_CTRL_TRIANGLE.bit,
            R.drawable.square,
            Digital2Flags.CELL_PAD_CTRL_SQUARE.bit,
            R.drawable.circle,
            Digital2Flags.CELL_PAD_CTRL_CIRCLE.bit,
            R.drawable.cross,
            Digital2Flags.CELL_PAD_CTRL_CROSS.bit,
            true
        )

        leftStick = PadOverlayStick(
            resources,
            true,
            getBitmap(R.drawable.left_stick_background, buttonSize * 2, buttonSize * 2),
            getBitmap(R.drawable.left_stick, buttonSize * 2, buttonSize * 2)
        )
        rightStick = PadOverlayStick(
            resources,
            false,
            getBitmap(R.drawable.right_stick_background, buttonSize * 2, buttonSize * 2),
            getBitmap(R.drawable.right_stick, buttonSize * 2, buttonSize * 2)
        )

        leftStick.setBounds(0, 0, buttonSize * 2, buttonSize * 2)
        leftStick.alpha = idleAlpha
        rightStick.setBounds(0, 0, buttonSize * 2, buttonSize * 2)
        rightStick.alpha = idleAlpha


        val l3r3Size = (buttonSize * 1.5).toInt()
        val l3 = PadOverlayStick(
            resources,
            true,
            getBitmap(R.drawable.left_stick_background, l3r3Size, l3r3Size),
            getBitmap(R.drawable.l3, l3r3Size, l3r3Size),
            pressDigitalIndex = 0,
            pressBit = Digital1Flags.CELL_PAD_CTRL_L3.bit
        )
        l3.alpha = idleAlpha
        l3.setBounds(
            totalWidth / 2 - buttonSize * 2 - l3r3Size,
            (totalHeight - buttonSize * 2.3).toInt(),
            totalWidth / 2 - buttonSize * 2,
            totalHeight - (buttonSize * 2.3).toInt() + l3r3Size
        )

        val r3 = PadOverlayStick(
            resources,
            false,
            getBitmap(R.drawable.right_stick_background, l3r3Size, l3r3Size),
            getBitmap(R.drawable.r3, l3r3Size, l3r3Size),
            pressDigitalIndex = 0,
            pressBit = Digital1Flags.CELL_PAD_CTRL_R3.bit
        )
        r3.alpha = idleAlpha
        r3.setBounds(
            totalWidth / 2 + buttonSize * 2,
            totalHeight - (buttonSize * 2.3).toInt(),
            totalWidth / 2 + buttonSize * 2 + l3r3Size,
            totalHeight - (buttonSize * 2.3).toInt() + l3r3Size
        )

        sticks += l3
        sticks += r3

        buttons = arrayOf(
            createButton(
                R.drawable.start,
                btnStartX,
                btnStartY,
                startSelectSize,
                startSelectSize,
                Digital1Flags.CELL_PAD_CTRL_START,
                Digital2Flags.None
            ),
            createButton(
                R.drawable.select,
                btnSelectX,
                btnSelectY,
                startSelectSize,
                startSelectSize,
                Digital1Flags.CELL_PAD_CTRL_SELECT,
                Digital2Flags.None
            ),

            createButton(
                R.drawable.ic_rpcsx_foreground,
                btnHomeX,
                btnHomeY,
                buttonSize,
                buttonSize,
                Digital1Flags.CELL_PAD_CTRL_PS,
                Digital2Flags.None
            ),
            createButton(
                R.drawable.l1,
                btnL1X,
                btnL1Y,
                startSelectSize,
                startSelectSize,
                Digital1Flags.None,
                Digital2Flags.CELL_PAD_CTRL_L1
            ),
            createButton(
                R.drawable.l2,
                btnL2X,
                btnL2Y,
                startSelectSize,
                startSelectSize,
                Digital1Flags.None,
                Digital2Flags.CELL_PAD_CTRL_L2
            ),
            createButton(
                R.drawable.r1,
                btnR1X,
                btnR1Y,
                startSelectSize,
                startSelectSize,
                Digital1Flags.None,
                Digital2Flags.CELL_PAD_CTRL_R1
            ),
            createButton(
                R.drawable.r2,
                btnR2X,
                btnR2Y,
                startSelectSize,
                startSelectSize,
                Digital1Flags.None,
                Digital2Flags.CELL_PAD_CTRL_R2
            ),
        )

        setWillNotDraw(false)
        requestFocus()

        setOnTouchListener { _, motionEvent ->
            var hit = false

            val action = motionEvent.actionMasked
            val pointerIndex =
                if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) motionEvent.actionIndex else 0
            val x = motionEvent.getX(pointerIndex).toInt()
            val y = motionEvent.getY(pointerIndex).toInt()

            if (isEditing) {
                when (action) {
                    MotionEvent.ACTION_DOWN -> {
                        buttons.forEach { button ->
                            if (button.contains(x, y)) {
                                selectedInput = button
                                button.startDragging(x, y)
                                hit = true
                            }
                        }
                        if (dpad.contains(x, y)) {
                            selectedInput = dpad
                            dpad.startDragging(x, y)
                            hit = true
                        }
                        if (triangleSquareCircleCross.contains(x, y)) {
                            selectedInput = triangleSquareCircleCross
                            triangleSquareCircleCross.startDragging(x, y)
                            hit = true
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        buttons.forEach { button ->
                            if (button.dragging) {
                                button.updatePosition(x, y)
                                hit = true
                            }
                        }
                        if (dpad.dragging) {
                            dpad.updatePosition(x, y)
                            hit = true
                        }
                        if (triangleSquareCircleCross.contains(x, y)) {
                            triangleSquareCircleCross.updatePosition(x, y)
                            hit = true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        buttons.forEach { button ->
                            button.stopDragging()
                        }
                        dpad.stopDragging()
                        triangleSquareCircleCross.stopDragging()
                    }
                }
                if (hit) invalidate()
                return@setOnTouchListener true
            }
            
            val force =
                action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_MOVE
            if (force || (dpad.contains(x, y) && dpad.enabled)) {
                hit = dpad.onTouch(motionEvent, pointerIndex, state)
            }

            if (force || (!hit && triangleSquareCircleCross.contains(x, y) && triangleSquareCircleCross.enabled)
            ) {
                hit = triangleSquareCircleCross.onTouch(motionEvent, pointerIndex, state)
            }

            buttons.forEach { button ->
                if (force || (!hit && button.contains(x, y) && button.enabled)) {
                    hit = button.onTouch(motionEvent, pointerIndex, state)
                }
            }
        
            if (hit && GeneralSettings["haptic_feedback"] as Boolean? ?: true) {
                val vm = context?.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            }

            if (force || !hit) {
                for (i in sticks.indices) {
                    if (!force && (!sticks[i].contains(x, y) || floatingSticks[i] != null)) {
                        continue
                    }

                    val touchResult = sticks[i].onTouch(motionEvent, pointerIndex, state)
                    hit = if (touchResult < 0) {
                        true
                    } else {
                        touchResult == 1
                    }
                }
            }

            if (force || !hit) {
                for (i in floatingSticks.indices) {
                    val stick = floatingSticks[i] ?: continue
                    val touchResult = stick.onTouch(motionEvent, pointerIndex, state)
                    if (touchResult < 0) {
                        floatingSticks[i] = null
                        hit = true
                    } else {
                        hit = touchResult == 1
                    }
                }
            }

            RPCSX.instance.overlayPadData(
                state.digital[0],
                state.digital[1],
                state.leftStickX,
                state.leftStickY,
                state.rightStickX,
                state.rightStickY
            )

            if (!hit && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)) {
                val xInFloatingArea = x > buttonSize * 2 && x < totalWidth - buttonSize * 2
                val yInFloatingArea = y > buttonSize && y < totalHeight - buttonSize
                var inFloatingArea = xInFloatingArea && yInFloatingArea
                if (!inFloatingArea && yInFloatingArea) {
                    if (x > buttonSize && x <= buttonSize * 2) {
                        inFloatingArea = true
                    }

                    if (x <= totalWidth - buttonSize && x >= totalWidth - buttonSize * 2) {
                        inFloatingArea = true
                    }
                }

                if (inFloatingArea) {
                    val stickIndex = if (x <= totalWidth / 2) 0 else 1
                    val stick = if (stickIndex == 0) leftStick else rightStick

                    if (floatingSticks[stickIndex] == null && !sticks[stickIndex].isActive()) {
                        floatingSticks[stickIndex] = stick
                        stick.onAdd(motionEvent, pointerIndex)
                        hit = true
                    }
                }
            }

            if (hit || force) {
                invalidate()
            }

            hit || performClick()
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        buttons.forEach { button -> 
            if (button.enabled)
                button.draw(canvas) 
            else
                createOutline(isEditing, button.bounds, canvas, yellowOutlinePaint)
        }
        
        if (dpad.enabled)
            dpad.draw(canvas)
        else
            createOutline(isEditing, dpad.getBounds(), canvas, yellowOutlinePaint)
            
        if (triangleSquareCircleCross.enabled) 
            triangleSquareCircleCross.draw(canvas)
        else
            createOutline(isEditing, triangleSquareCircleCross.getBounds(), canvas, yellowOutlinePaint)
          
        sticks.forEach { it.draw(canvas) }
        floatingSticks.forEach { it?.draw(canvas) }

        if (isEditing && selectedInput != null) {
            val bounds = when (selectedInput) {
                is PadOverlayButton -> (selectedInput as PadOverlayButton).bounds
                is PadOverlayDpad -> (selectedInput as PadOverlayDpad).getBounds()
                else -> null
            }

            bounds?.let {
                createOutline(true, it, canvas, outlinePaint)
            }
        }
    }

    private fun createOutline(shouldApply: Boolean = true, bounds: Rect, canvas: Canvas, outlinePaintColor: Paint) {
        if (shouldApply) {
            val rect = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
            canvas.drawRect(rect, outlinePaintColor)
        }
    }

    private fun getBitmap(resourceId: Int, width: Int, height: Int): Bitmap {
        return when (val drawable = ContextCompat.getDrawable(context, resourceId)) {
            is BitmapDrawable -> {
                BitmapFactory.decodeResource(context.resources, resourceId).scale(width, height)
            }

            is VectorDrawable -> {
                drawable.toBitmap(width, height)
            }

            else -> {
                throw IllegalArgumentException("unexpected drawable type")
            }
        }
    }

    private fun createButton(
        resourceId: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        digital1: Digital1Flags,
        digital2: Digital2Flags
    ): PadOverlayButton {
        val resources = context!!.resources
        val bitmap = getBitmap(resourceId, width, height)
        val result = PadOverlayButton(resources, bitmap, digital1.bit, digital2.bit)
        val scale = GeneralSettings["button_${digital1.bit}_${digital2.bit}_scale"] as Int? ?: 0
        val alpha = GeneralSettings["button_${digital1.bit}_${digital2.bit}_opacity"] as Int? ?: 50
        val savedX = GeneralSettings["button_${digital1.bit}_${digital2.bit}_x"] as Int? ?: x
        val savedY = GeneralSettings["button_${digital1.bit}_${digital2.bit}_y"] as Int? ?: y
        result.setBounds(savedX, savedY, savedX + width, savedY + height)
        result.defaultPosition = Pair(x, y)
        result.defaultSize = Pair(height, width)
        if (scale != 0) result.setScale(scale)
        result.setOpacity(alpha)
        return result
    }

    private fun createDpad(
        inputId: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        buttonWidth: Int,
        buttonHeight: Int,
        digital: Int,
        upResource: Int, upBit: Int,
        leftResource: Int, leftBit: Int,
        rightResource: Int, rightBit: Int,
        downResource: Int, downBit: Int,
        multitouch: Boolean
    ): PadOverlayDpad {
        val upBitmap = getBitmap(upResource, buttonWidth, buttonHeight)
        val leftBitmap = getBitmap(leftResource, buttonHeight, buttonWidth)
        val rightBitmap = getBitmap(rightResource, buttonHeight, buttonWidth)
        val downBitmap = getBitmap(downResource, buttonWidth, buttonHeight)

        val result = PadOverlayDpad(
            resources, buttonWidth, buttonHeight, inputId, Rect(x, y, x + width, y + height), digital,
            upBitmap, upBit,
            leftBitmap, leftBit,
            rightBitmap, rightBit,
            downBitmap, downBit,
            multitouch
        )

        val alpha = GeneralSettings["${inputId}_opacity"] as Int? ?: -1
        result.setOpacity(if (alpha != -1) alpha else 50)
        return result
    }

    fun setButtonScale(value: Int) {
        (selectedInput as? PadOverlayDpad)?.setScale(value)
        ?: (selectedInput as? PadOverlayButton)?.setScale(value)
        invalidate()
    }

    fun setButtonOpacity(value: Int) {
        (selectedInput as? PadOverlayDpad)?.setOpacity(value)
        ?: (selectedInput as? PadOverlayButton)?.setOpacity(value)
        invalidate()
    }

    fun resetButtonConfigs() {
        (selectedInput as? PadOverlayDpad)?.resetConfigs()
        ?: (selectedInput as? PadOverlayButton)?.resetConfigs()
        invalidate()
        onSelectedInputChange?.invoke(selectedInput!!)
    }

    fun moveButtonLeft() {
        val bounds = (selectedInput as? PadOverlayDpad)?.getBounds() 
        ?: (selectedInput as? PadOverlayButton)?.bounds
        if (bounds != null) {
            (selectedInput as? PadOverlayDpad)?.updatePosition(bounds.left - 1, bounds.top, true)
            ?: (selectedInput as? PadOverlayButton)?.updatePosition(bounds.left - 1, bounds.top, true)
            invalidate()
        }
    }

    fun moveButtonRight() {
        val bounds = (selectedInput as? PadOverlayDpad)?.getBounds() 
        ?: (selectedInput as? PadOverlayButton)?.bounds
        if (bounds != null) {
            (selectedInput as? PadOverlayDpad)?.updatePosition(bounds.left + 1, bounds.top, true)
            ?: (selectedInput as? PadOverlayButton)?.updatePosition(bounds.left + 1, bounds.top, true)
            invalidate()
        }
    }

    fun moveButtonUp() {
        val bounds = (selectedInput as? PadOverlayDpad)?.getBounds() 
        ?: (selectedInput as? PadOverlayButton)?.bounds
        if (bounds != null) {
            (selectedInput as? PadOverlayDpad)?.updatePosition(bounds.left, bounds.top - 1, true)
            ?: (selectedInput as? PadOverlayButton)?.updatePosition(bounds.left, bounds.top - 1, true)
            invalidate()
        }
    }

    fun moveButtonDown() {
        val bounds = (selectedInput as? PadOverlayDpad)?.getBounds() 
        ?: (selectedInput as? PadOverlayButton)?.bounds
        if (bounds != null) {
            (selectedInput as? PadOverlayDpad)?.updatePosition(bounds.left, bounds.top + 1, true)
            ?: (selectedInput as? PadOverlayButton)?.updatePosition(bounds.left, bounds.top + 1, true)
            invalidate()
        }
    }

    fun enableButton(value: Boolean) {
        if (selectedInput is PadOverlayDpad) {
            (selectedInput as PadOverlayDpad).enabled = value
        } else if (selectedInput is PadOverlayButton) {
            (selectedInput as PadOverlayButton).enabled = value
        }
        invalidate()
    }
}
