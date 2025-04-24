package net.rpcsx.overlay

import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.content.Context
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import net.rpcsx.RPCSXTheme
import net.rpcsx.R
import kotlin.math.roundToInt

class OverlayEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableFullScreenImmersive(this)
        setContent {
            RPCSXTheme {
                OverlayEditScreen()
            }
        }
    }

    private fun enableFullScreenImmersive(activity: ComponentActivity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
    
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val params = window.attributes
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = params
    }
}

private fun applyInsetsToPadOverlay(padOverlay: PadOverlay) {
    ViewCompat.setOnApplyWindowInsetsListener(padOverlay) { view, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        if (view.layoutParams is MarginLayoutParams) {
            view.updateLayoutParams<MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
                topMargin = insets.top
                bottomMargin = insets.bottom
            }
        }
        WindowInsetsCompat.CONSUMED
    }
}

@Composable
fun OverlayEditScreen() {
    var isPanelVisible by remember { mutableStateOf(true) }
    var scaleValue by remember { mutableStateOf(50f) }
    var opacityValue by remember { mutableStateOf(100f) }
    var isEnabled by remember { mutableStateOf(true) }
    var currentButtonName by remember { mutableStateOf("Unknown") }
    var showResetDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var padOverlay: PadOverlay? by remember { mutableStateOf(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx: Context ->
                PadOverlay(ctx, null).also { padOverlay = it }
            },
            update = { padOverlay = it }
        )

        padOverlay?.layoutParams = MarginLayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        padOverlay?.let { applyInsetsToPadOverlay(it) }
        padOverlay?.isEditing = true

        padOverlay?.onSelectedInputChange = { input ->
            val info = (input as? PadOverlayDpad)?.getInfo() ?: (input as? PadOverlayButton)?.getInfo()
            if (info != null) {
                currentButtonName = info.first.toString()
                scaleValue = info.second.toFloat()
                opacityValue = info.third.toFloat()
            }
            val inputEnabled = (input as? PadOverlayDpad)?.enabled ?: (input as? PadOverlayButton)?.enabled
            if (inputEnabled != null) {
                isEnabled = inputEnabled
            }
        }

        if (!isPanelVisible) {
            FloatingActionButton(
                onClick = { isPanelVisible = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Open Control Panel")
            }
        }

        AnimatedVisibility(
            visible = isPanelVisible,
            enter = fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.8f, animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.8f, animationSpec = tween(200))
        ) {
            ControlPanel(
                scaleValue = scaleValue,
                onScaleChange = { 
                    scaleValue = it 
                    padOverlay?.setButtonScale(it.roundToInt())
                },
                opacityValue = opacityValue,
                onOpacityChange = { 
                    opacityValue = it 
                    padOverlay?.setButtonOpacity(it.roundToInt())
                },
                isEnabled = isEnabled,
                onEnableChange = { 
                    isEnabled = it 
                    padOverlay?.enableButton(isEnabled)
                },
                currentButtonName = currentButtonName,
                onResetClick = { showResetDialog = true },
                onCloseClick = { isPanelVisible = false },
                onMoveUp = { padOverlay?.moveButtonUp() },
                onMoveRight = { padOverlay?.moveButtonRight() },
                onMoveLeft = { padOverlay?.moveButtonLeft() },
                onMoveDown = { padOverlay?.moveButtonDown() }
            )
        }

        if (showResetDialog) {
            ResetDialog(
                buttonName = currentButtonName,
                onConfirm = { 
                    showResetDialog = false
                    padOverlay?.resetButtonConfigs() 
                },
                onDismiss = { showResetDialog = false }
            )
        }
    }
}

@Composable
fun ControlPanel(
    scaleValue: Float,
    onScaleChange: (Float) -> Unit,
    opacityValue: Float,
    onOpacityChange: (Float) -> Unit,
    isEnabled: Boolean,
    onEnableChange: (Boolean) -> Unit,
    currentButtonName: String,
    onResetClick: () -> Unit,
    onCloseClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveRight: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveDown: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    val panelWidth = 336f
    val panelHeight = 200f
    
    var panelOffset by remember { 
        mutableStateOf(
            PointF(
                (screenWidth / 2f - panelWidth / 2f), 
                (screenHeight / 2f - panelHeight / 2f)
            )
        ) 
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(panelOffset.x.toInt(), panelOffset.y.toInt()) }
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), RoundedCornerShape(8.dp))
            .padding(10.dp)
            .width(336.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    panelOffset = PointF(panelOffset.x + dragAmount.x, panelOffset.y + dragAmount.y)
                }
            }
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}, modifier = Modifier.alpha(0f)) {
		    Icon(Icons.Default.Close, contentDescription = "Disabled Button")
                }
                Text(
                    text = "Control Panel",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
		    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onCloseClick) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.error)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(50))
            )
            Spacer(modifier = Modifier.height(5.dp))
            
            Text(
                text = "Editing: $currentButtonName",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = onMoveUp) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move Up",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMoveLeft) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Move Left",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Checkbox(
                        checked = isEnabled,
                        onCheckedChange = onEnableChange,
                        modifier = Modifier.padding(4.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    )

                    IconButton(onClick = onMoveRight) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Move Right",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = onMoveDown) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move Down",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SliderComponent("Scale", scaleValue, onScaleChange)
                    Spacer(modifier = Modifier.height(6.dp))
                    SliderComponent("Opacity", opacityValue, onOpacityChange)
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onResetClick,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors()
                ) {
                    Icon(painter = painterResource(id = R.drawable.ic_restore), contentDescription = "Reset", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderComponent(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}) {
        Text(text = "$label: ${value.roundToInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..100f,
            thumb = {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                )
            },
            modifier = Modifier.padding(horizontal = 16.dp).height(20.dp)
        )
    }
}

@Composable
fun ResetDialog(buttonName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Reset $buttonName") },
        text = { Text(text = "Are you sure you want to reset this button?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

@Preview
@Composable
fun PreviewOverlayEditScreen() {
    OverlayEditScreen()
}
