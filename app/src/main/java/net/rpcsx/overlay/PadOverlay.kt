package net.rpcsx.overlay

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import android.os.Vibrator
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.VectorDrawable
import android.animation.ObjectAnimator
import android.util.AttributeSet
import android.view.View
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
import net.rpcsx.utils.GeneralSettings.int
import net.rpcsx.utils.GeneralSettings.boolean
import kotlin.math.min


private const val idleAlpha = (0.3 * 255).toInt()

data class State(
    val digital: IntArray = IntArray(2),
    var leftStickX: Int = 127,
    var leftStickY: Int = 127,
    var rightStickX: Int = 127,
    var rightStickY: Int = 127
)
interface PadOverlayItem {
    fun draw(canvas: Canvas)
    fun updatePosition(x: Int, y: Int, force: Boolean = false)
    fun startDragging(startX: Int, startY: Int)
    fun stopDragging()
    fun setScale(percent: Int)
    fun setOpacity(percent: Int)
    fun resetConfigs()
    fun onTouch(event: MotionEvent, pointerIndex: Int, padState: State): Boolean
    fun contains(x: Int, y: Int): Boolean
    fun bounds(): Rect
    var dragging: Boolean
    var enabled: Boolean
}
class PadOverlay(context: Context?, attrs: AttributeSet?) : SurfaceView(context, attrs) {
    private val buttons: Array<PadOverlayButton>
    private val dpad: PadOverlayDpad
    private val triangleSquareCircleCross: PadOverlayDpad
    private val editables: Array<PadOverlayItem>
    private val state = State()
    private val leftStick: PadOverlayStick
    private val rightStick: PadOverlayStick
    private val floatingSticks = arrayOf<PadOverlayStick?>(null, null)
    private val sticks = mutableListOf<PadOverlayStick>()
    private val prefs by lazy { context!!.getSharedPreferences("PadOverlayPrefs", Context.MODE_PRIVATE) }
    private val vibrator by lazy { 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (context?.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)?.defaultVibrator 
        else context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator 
    }
    private var selectedInput: PadOverlayItem? = null
        set(value) {
            field = value
            onSelectedInputChange?.invoke(value)
        }
        
    var onSelectedInputChange: ((Any?) -> Unit)? = null
    var isEditing = false

    private var fadeHandler: Handler? = null
    private var fadeRunnable: Runnable? = null
    private var isOverlayVisible = true
    private var lastTouchTime = System.currentTimeMillis()

    private val fadeDuration = 500L
    private val fadeTimeout = 19_000L
    
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
        editables = arrayOf(*buttons, dpad, triangleSquareCircleCross)
        setWillNotDraw(false)
        requestFocus()

        setOnTouchListener { _, motionEvent ->
            var hit = false

            if (!isEditing && GeneralSettings["auto_hide_overlay"].boolean(true)) {
                lastTouchTime = System.currentTimeMillis()
                resetFadeTimer()
                if (!isOverlayVisible) {
                    fadeInOverlay()
                }
            }

            val action = motionEvent.actionMasked
            val pointerIndex =
                if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) motionEvent.actionIndex else 0
            val x = motionEvent.getX(pointerIndex).toInt()
            val y = motionEvent.getY(pointerIndex).toInt()

            if (isEditing) {
                when (action) {
                    MotionEvent.ACTION_DOWN -> {
                        editables.forEach { editable ->
                            if (editable.contains(x, y)) {
                                selectedInput = editable
                                editable.startDragging(x, y)
                                hit = true
                            }
                        }
                        if (!hit) {
                            selectedInput = null
                            hit = true
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        editables.forEach { editable ->
                            if (editable.dragging) {
                                editable.updatePosition(x, y)
                                hit = true
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        editables.forEach { editable ->
                            editable.stopDragging()
                        }
                    }
                }
                if (hit) invalidate()
                return@setOnTouchListener true
            }
            
            val force =
                action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_MOVE

            editables.forEach { editable ->
                if (force || (!hit && editable.contains(x, y) && editable.enabled)) {
                    hit = editable.onTouch(motionEvent, pointerIndex, state)
                }
            }
        
            if (hit && GeneralSettings["haptic_feedback"] as Boolean? ?: true) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
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
        editables.forEach { editable ->
            if (editable.enabled)
                editable.draw(canvas)
            else
                createOutline(isEditing, editable.bounds(), canvas, yellowOutlinePaint)
        }
        sticks.forEach { it.draw(canvas) }
        floatingSticks.forEach { it?.draw(canvas) }

        if (isEditing) {
            if (selectedInput != null) {
                createOutline(true, selectedInput!!.bounds(), canvas, outlinePaint)
            } else {
                editables.forEach { editable ->
                    createOutline(true, editable.bounds(), canvas, outlinePaint)
                }
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
        val scale = GeneralSettings["button_${digital1.bit}_${digital2.bit}_scale"].int(0)
        val alpha = GeneralSettings["button_${digital1.bit}_${digital2.bit}_opacity"].int(50)
        val savedX = GeneralSettings["button_${digital1.bit}_${digital2.bit}_x"].int(x)
        val savedY = GeneralSettings["button_${digital1.bit}_${digital2.bit}_y"].int(y)
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

        val alpha = GeneralSettings["${inputId}_opacity"].int(-1)
        result.setOpacity(if (alpha != -1) alpha else 50)
        return result
    }

    private fun resetFadeTimer() {
        fadeHandler?.removeCallbacks(fadeRunnable!!)
        fadeHandler = Handler(Looper.getMainLooper())
        fadeRunnable = Runnable {
            fadeOutOverlay()
        }
        fadeHandler?.postDelayed(fadeRunnable!!, fadeTimeout)
    }

    private fun fadeOutOverlay() {
        if (!isOverlayVisible) return
        isOverlayVisible = false
        ObjectAnimator.ofFloat(this, "alpha", 1f, 0f).apply {
            duration = fadeDuration
            start()
        }
    }

    private fun fadeInOverlay() {
        if (isOverlayVisible) return
        isOverlayVisible = true
        ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
            duration = fadeDuration
            start()
        }
    }

    fun setButtonScale(value: Int) {
        selectedInput!!.setScale(value)
        invalidate()
    }

    fun setButtonOpacity(value: Int) {
        selectedInput!!.setOpacity(value)
        invalidate()
    }

    fun resetButtonConfigs() {
        if (selectedInput != null) {
            selectedInput!!.resetConfigs()
        } else {
            editables.forEach { editable -> editable.resetConfigs() }
        }
        invalidate()
    }

    fun moveButtonLeft() {
        if (selectedInput != null) {
            val bounds = selectedInput!!.bounds()
            selectedInput!!.updatePosition(bounds.left - 1, bounds.top, true)
        } else {
            editables.forEach { editable -> selectedInput = editable; moveButtonLeft() }
            selectedInput = null
        }
        invalidate()
    }

    fun moveButtonRight() {
        if (selectedInput != null) {
            val bounds = selectedInput!!.bounds()
            selectedInput!!.updatePosition(bounds.left + 1, bounds.top, true)
        } else {
            editables.forEach { editable -> selectedInput = editable; moveButtonRight() }
            selectedInput = null
        }
        invalidate()
    }

    fun moveButtonUp() {
        if (selectedInput != null) {
            val bounds = selectedInput!!.bounds()
            selectedInput!!.updatePosition(bounds.left, bounds.top - 1, true)
        } else {
            editables.forEach { editable -> selectedInput = editable; moveButtonUp() }
            selectedInput = null
        }
        invalidate()
    }

    fun moveButtonDown() {
        if (selectedInput !== null) {
            val bounds = selectedInput!!.bounds()
            selectedInput!!.updatePosition(bounds.left, bounds.top + 1, true)
        } else {
            editables.forEach { editable -> selectedInput = editable; moveButtonDown() }
            selectedInput = null
        }
        invalidate()
    }

    fun enableButton(value: Boolean) {
        selectedInput!!.enabled = value //null means enable checkbox is disabled, shouldn't be called when null
        invalidate()
    }
}
