package net.rpcsx.overlay

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.VectorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import net.rpcsx.Digital1Flags
import net.rpcsx.Digital2Flags
import net.rpcsx.R
import net.rpcsx.RPCSX
import net.rpcsx.utils.GeneralSettings
import net.rpcsx.utils.GeneralSettings.int
import kotlin.math.min

data class State(
    val digital: IntArray = IntArray(2),
    var leftStickX: Int = 127,
    var leftStickY: Int = 127,
    var rightStickX: Int = 127,
    var rightStickY: Int = 127
)

interface PadOverlayItem {
    fun scale(): Int
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

@SuppressLint("ViewConstructor")
class PadOverlay(context: Context, attrs: AttributeSet? = null) : SurfaceView(context, attrs) {

    companion object {
        private const val FADE_DURATION = 500L
        private const val FADE_TIMEOUT = 19_000L
        private const val IDLE_ALPHA = (0.3 * 255).toInt()
    }

    private lateinit var buttons: Array<PadOverlayButton>
    private lateinit var dpad: PadOverlayDpad
    private lateinit var triangleSquareCircleCross: PadOverlayDpad
    private lateinit var editables: Array<PadOverlayItem>

    private val state = State()
    private lateinit var leftStick: PadOverlayStick
    private lateinit var rightStick: PadOverlayStick
    private val floatingSticks = arrayOf<PadOverlayStick?>(null, null)
    private val sticks = mutableListOf<PadOverlayStick>()

    private val vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    var selectedInput: PadOverlayItem? = null
        set(value) {
            field = value
            isGlobalScaling = false
            onSelectedInputChange?.invoke(value)
        }

    var onSelectedInputChange: ((Any?) -> Unit)? = null
    var isEditing = false

    private val fadeHandler = Handler(Looper.getMainLooper())
    private val fadeRunnable = Runnable { fadeOutOverlay() }
    private var isOverlayVisible = true
    private var lastTouchTime = System.currentTimeMillis()

    private val globalScaleSnapshot = mutableMapOf<PadOverlayItem, Int>()
    private var isGlobalScaling = false

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

    private var buttonSize = 0
    private var totalWidth = 0
    private var totalHeight = 0

    init {
        setupLayout()
        setWillNotDraw(false)
        requestFocus()
    }

    private fun setupLayout() {
        val metrics = context.resources.displayMetrics
        totalWidth = metrics.widthPixels
        totalHeight = metrics.heightPixels
        val sizeHint = min(totalHeight, totalWidth)
        buttonSize = sizeHint / 10

        val btnAreaX = totalWidth - (buttonSize * 3) - buttonSize
        val btnAreaY = totalHeight - (buttonSize * 3) - buttonSize / 2
        val startSelectSize = (buttonSize * 1.5).toInt()
        val btnStartY = buttonSize / 2

        initDpads(buttonSize, btnAreaX, btnAreaY)
        initSticks(buttonSize, totalWidth, totalHeight)
        initButtons(buttonSize, startSelectSize, totalWidth, btnStartY)

        editables = arrayOf(*buttons, dpad, triangleSquareCircleCross)
    }

    private fun initDpads(buttonSize: Int, btnAreaX: Int, btnAreaY: Int) {
        val btnDistance = buttonSize / 8
        val dpadW = buttonSize * 3 - btnDistance / 2

        dpad = createDpad(
            "dpad", buttonSize, btnAreaY, dpadW, dpadW,
            dpadW / 2, dpadW / 2 - dpadW / 20, 0,
            R.drawable.dpad_top, Digital1Flags.CELL_PAD_CTRL_UP.bit,
            R.drawable.dpad_left, Digital1Flags.CELL_PAD_CTRL_LEFT.bit,
            R.drawable.dpad_right, Digital1Flags.CELL_PAD_CTRL_RIGHT.bit,
            R.drawable.dpad_bottom, Digital1Flags.CELL_PAD_CTRL_DOWN.bit,
            false
        )

        triangleSquareCircleCross = createDpad(
            "triangleSquareCircleCross", btnAreaX - buttonSize / 2, btnAreaY, buttonSize * 3, buttonSize * 3,
            buttonSize, buttonSize, 1,
            R.drawable.triangle, Digital2Flags.CELL_PAD_CTRL_TRIANGLE.bit,
            R.drawable.square, Digital2Flags.CELL_PAD_CTRL_SQUARE.bit,
            R.drawable.circle, Digital2Flags.CELL_PAD_CTRL_CIRCLE.bit,
            R.drawable.cross, Digital2Flags.CELL_PAD_CTRL_CROSS.bit,
            true
        )
    }

    private fun initSticks(buttonSize: Int, totalWidth: Int, totalHeight: Int) {
        val dStickSize = buttonSize * 2
        leftStick = PadOverlayStick(resources, true, getBitmap(R.drawable.left_stick_background, dStickSize, dStickSize), getBitmap(R.drawable.left_stick, dStickSize, dStickSize))
        rightStick = PadOverlayStick(resources, false, getBitmap(R.drawable.right_stick_background, dStickSize, dStickSize), getBitmap(R.drawable.right_stick, dStickSize, dStickSize))

        leftStick.apply { setBounds(0, 0, dStickSize, dStickSize); alpha = IDLE_ALPHA }
        rightStick.apply { setBounds(0, 0, dStickSize, dStickSize); alpha = IDLE_ALPHA }

        val l3r3Size = (buttonSize * 1.5).toInt()
        val stickYOff = (totalHeight - buttonSize * 2.3).toInt()

        val l3 = PadOverlayStick(resources, true, getBitmap(R.drawable.left_stick_background, l3r3Size, l3r3Size), getBitmap(R.drawable.l3, l3r3Size, l3r3Size), 0, Digital1Flags.CELL_PAD_CTRL_L3.bit).apply {
            alpha = IDLE_ALPHA
            setBounds(totalWidth / 2 - buttonSize * 2 - l3r3Size, stickYOff, totalWidth / 2 - buttonSize * 2, stickYOff + l3r3Size)
        }

        val r3 = PadOverlayStick(resources, false, getBitmap(R.drawable.right_stick_background, l3r3Size, l3r3Size), getBitmap(R.drawable.r3, l3r3Size, l3r3Size), 0, Digital1Flags.CELL_PAD_CTRL_R3.bit).apply {
            alpha = IDLE_ALPHA
            setBounds(totalWidth / 2 + buttonSize * 2, stickYOff, totalWidth / 2 + buttonSize * 2 + l3r3Size, stickYOff + l3r3Size)
        }

        sticks.add(l3)
        sticks.add(r3)
    }

    private fun initButtons(buttonSize: Int, startSelectSize: Int, totalWidth: Int, btnStartY: Int) {
        val btnL1Y = buttonSize + buttonSize + buttonSize / 2
        buttons = arrayOf(
            createButton(R.drawable.start, totalWidth / 2 + buttonSize * 2, btnStartY, startSelectSize, startSelectSize, Digital1Flags.CELL_PAD_CTRL_START, Digital2Flags.None),
            createButton(R.drawable.select, totalWidth / 2 - startSelectSize - buttonSize * 2, btnStartY, startSelectSize, startSelectSize, Digital1Flags.CELL_PAD_CTRL_SELECT, Digital2Flags.None),
            createButton(R.drawable.ic_rpcsx_foreground, totalWidth / 2 - buttonSize / 2, btnStartY + (startSelectSize - buttonSize) / 2, buttonSize, buttonSize, Digital1Flags.CELL_PAD_CTRL_PS, Digital2Flags.None),
            createButton(R.drawable.l1, buttonSize, btnL1Y, startSelectSize, startSelectSize, Digital1Flags.None, Digital2Flags.CELL_PAD_CTRL_L1),
            createButton(R.drawable.l2, buttonSize, buttonSize, startSelectSize, startSelectSize, Digital1Flags.None, Digital2Flags.CELL_PAD_CTRL_L2),
            createButton(R.drawable.r1, totalWidth - buttonSize * 2, btnL1Y, startSelectSize, startSelectSize, Digital1Flags.None, Digital2Flags.CELL_PAD_CTRL_R1),
            createButton(R.drawable.r2, totalWidth - buttonSize * 2, buttonSize, startSelectSize, startSelectSize, Digital1Flags.None, Digital2Flags.CELL_PAD_CTRL_R2)
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEditing) {
            lastTouchTime = System.currentTimeMillis()
            resetFadeTimer()
            if (!isOverlayVisible) fadeInOverlay()
        }

        val action = event.actionMasked
        val pointerIndex = if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) event.actionIndex else 0
        val x = event.getX(pointerIndex).toInt()
        val y = event.getY(pointerIndex).toInt()

        if (isEditing) {
            return handleEditTouch(action, x, y)
        }

        return handleGameplayTouch(event, action, pointerIndex, x, y)
    }

    private fun handleEditTouch(action: Int, x: Int, y: Int): Boolean {
        var hit = false
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                selectedInput = null
                for (editable in editables) {
                    if (editable.contains(x, y)) {
                        selectedInput = editable
                        editable.startDragging(x, y)
                        hit = true
                        break
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (editable in editables) {
                    if (editable.dragging) {
                        editable.updatePosition(x, y)
                        hit = true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                for (editable in editables) editable.stopDragging()
            }
        }
        if (hit) invalidate()
        return true
    }

    private fun handleGameplayTouch(event: MotionEvent, action: Int, pointerIndex: Int, x: Int, y: Int): Boolean {
        var hit = false
        val isForceAction = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_MOVE

        for (editable in editables) {
            if (isForceAction || (!hit && editable.contains(x, y) && editable.enabled)) {
                if (editable.onTouch(event, pointerIndex, state)) hit = true
            }
        }

        if (hit && (GeneralSettings["haptic_feedback"] as? Boolean ?: true)) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }

        hit = processSticks(sticks, event, pointerIndex, x, y, isForceAction, hit, false)
        hit = processSticks(floatingSticks.filterNotNull(), event, pointerIndex, x, y, isForceAction, hit, true)

        RPCSX.instance.overlayPadData(
            state.digital[0], state.digital[1],
            state.leftStickX, state.leftStickY,
            state.rightStickX, state.rightStickY
        )

        if (!hit && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)) {
            val inFloatingArea = isPointInFloatingArea(x, y)
            if (inFloatingArea) {
                val stickIndex = if (x <= totalWidth / 2) 0 else 1
                val stick = if (stickIndex == 0) leftStick else rightStick

                if (floatingSticks[stickIndex] == null && !sticks[stickIndex].isActive()) {
                    floatingSticks[stickIndex] = stick
                    stick.onAdd(event, pointerIndex)
                    hit = true
                }
            }
        }

        if (hit || isForceAction) invalidate()
        if (!hit) performClick()

        return true
    }

    private fun processSticks(
        targetSticks: List<PadOverlayStick>, event: MotionEvent, pointerIndex: Int, x: Int, y: Int, isForceAction: Boolean, initialHit: Boolean, isFloating: Boolean
    ): Boolean {
        var currentHit = initialHit
        for (i in targetSticks.indices) {
            val stick = targetSticks[i]
            if (!isForceAction && (!stick.contains(x, y) || (!isFloating && floatingSticks[i] != null))) continue

            val touchResult = stick.onTouch(event, pointerIndex, state)
            if (touchResult < 0) {
                if (isFloating) floatingSticks[i] = null
                currentHit = true
            } else if (touchResult == 1) {
                currentHit = true
            }
        }
        return currentHit
    }

    private fun isPointInFloatingArea(x: Int, y: Int): Boolean {
        val yInArea = y > buttonSize && y < totalHeight - buttonSize
        if (!yInArea) return false

        val xInArea = x > buttonSize * 2 && x < totalWidth - buttonSize * 2
        return xInArea || (x > buttonSize && x <= buttonSize * 2) || (x <= totalWidth - buttonSize && x >= totalWidth - buttonSize * 2)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        for (editable in editables) {
            if (editable.enabled) editable.draw(canvas)
            else createOutline(isEditing, editable.bounds(), canvas, yellowOutlinePaint)
        }

        for (stick in sticks) stick.draw(canvas)
        for (stick in floatingSticks) stick?.draw(canvas)

        if (isEditing) {
            selectedInput?.let {
                createOutline(true, it.bounds(), canvas, outlinePaint)
            } ?: run {
                for (editable in editables) createOutline(true, editable.bounds(), canvas, outlinePaint)
            }
        }
    }

    private fun createOutline(shouldApply: Boolean, bounds: Rect, canvas: Canvas, paint: Paint) {
        if (shouldApply) canvas.drawRect(bounds, paint)
    }

    private fun getBitmap(resourceId: Int, width: Int, height: Int): Bitmap {
        return when (val drawable = ContextCompat.getDrawable(context, resourceId)) {
            is BitmapDrawable -> BitmapFactory.decodeResource(context.resources, resourceId).scale(width, height)
            is VectorDrawable -> drawable.toBitmap(width, height)
            else -> throw IllegalArgumentException("Unexpected drawable type")
        }
    }

    private fun createButton(resId: Int, x: Int, y: Int, w: Int, h: Int, d1: Digital1Flags, d2: Digital2Flags): PadOverlayButton {
        val bmp = getBitmap(resId, w, h)
        return PadOverlayButton(resources, bmp, d1.bit, d2.bit).apply {
            val scale = GeneralSettings["button_${d1.bit}_${d2.bit}_scale"].int(0)
            val alpha = GeneralSettings["button_${d1.bit}_${d2.bit}_opacity"].int(50)
            val savedX = GeneralSettings["button_${d1.bit}_${d2.bit}_x"].int(x)
            val savedY = GeneralSettings["button_${d1.bit}_${d2.bit}_y"].int(y)

            setBounds(savedX, savedY, savedX + w, savedY + h)
            defaultPosition = Pair(x, y)
            defaultSize = Pair(h, w)
            if (scale != 0) setScale(scale)
            setOpacity(alpha)
        }
    }

    private fun createDpad(
        id: String, x: Int, y: Int, w: Int, h: Int, btnW: Int, btnH: Int, digital: Int,
        upRes: Int, upBit: Int, leftRes: Int, leftBit: Int, rightRes: Int, rightBit: Int, downRes: Int, downBit: Int, multi: Boolean
    ): PadOverlayDpad {
        val result = PadOverlayDpad(
            resources, btnW, btnH, id, Rect(x, y, x + w, y + h), digital,
            getBitmap(upRes, btnW, btnH), upBit, getBitmap(leftRes, btnH, btnW), leftBit,
            getBitmap(rightRes, btnH, btnW), rightBit, getBitmap(downRes, btnW, btnH), downBit, multi
        )
        val alpha = GeneralSettings["${id}_opacity"].int(-1)
        result.setOpacity(if (alpha != -1) alpha else 50)
        return result
    }

    private fun resetFadeTimer() {
        fadeHandler.removeCallbacks(fadeRunnable)
        fadeHandler.postDelayed(fadeRunnable, FADE_TIMEOUT)
    }

    private fun fadeOutOverlay() {
        if (!isOverlayVisible) return
        isOverlayVisible = false
        ObjectAnimator.ofFloat(this, "alpha", 1f, 0f).apply { duration = FADE_DURATION; start() }
    }

    private fun fadeInOverlay() {
        if (isOverlayVisible) return
        isOverlayVisible = true
        ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply { duration = FADE_DURATION; start() }
    }

    fun setButtonScale(value: Int) {
        if (selectedInput != null) {
            selectedInput?.setScale(value)
        } else {
            if (!isGlobalScaling) {
                globalScaleSnapshot.clear()
                editables.forEach { globalScaleSnapshot[it] = it.scale() }
                isGlobalScaling = true
            }

            val multiplier = value / 100f

            editables.forEach { item ->
                val baseScale = globalScaleSnapshot[item] ?: 100
                val newScale = (baseScale * multiplier).toInt()

                item.setScale(newScale.coerceIn(10, 300))
            }
        }
        invalidate()
    }

    fun setButtonOpacity(value: Int) {
        if (selectedInput != null) {
            selectedInput?.setOpacity(value)
        } else {
            editables.forEach { it.setOpacity(value) }
        }
        invalidate()
    }

    fun resetButtonConfigs() {
        selectedInput?.resetConfigs() ?: editables.forEach { it.resetConfigs() }
        invalidate()
    }

    private fun moveSelectedOrAll(dx: Int, dy: Int) {
        selectedInput?.let {
            val bounds = it.bounds()
            it.updatePosition(bounds.left + dx, bounds.top + dy, true)
        } ?: run {
            editables.forEach {
                val bounds = it.bounds()
                it.updatePosition(bounds.left + dx, bounds.top + dy, true)
            }
        }
        invalidate()
    }

    fun moveButtonLeft() = moveSelectedOrAll(-1, 0)
    fun moveButtonRight() = moveSelectedOrAll(1, 0)
    fun moveButtonUp() = moveSelectedOrAll(0, -1)
    fun moveButtonDown() = moveSelectedOrAll(0, 1)

    fun enableButton(value: Boolean) {
        if (selectedInput != null) {
            selectedInput?.enabled = value
        } else {
            editables.forEach { it.enabled = value }
        }
        invalidate()
    }
}