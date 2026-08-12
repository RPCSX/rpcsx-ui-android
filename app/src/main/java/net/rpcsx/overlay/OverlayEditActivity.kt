package net.rpcsx.overlay

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import net.rpcsx.R
import net.rpcsx.RPCSXTheme

class OverlayEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableFullScreenImmersive()
        setContent {
            RPCSXTheme {
                OverlayEditScreen()
            }
        }
    }

    private fun enableFullScreenImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}

@Composable
fun OverlayEditScreen(
    state: OverlayEditorState = rememberOverlayEditorState()
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PadOverlay(ctx, null).apply {
                    layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    applyWindowInsets(this)
                    isEditing = true
                    onSelectedInputChange = { input ->
                        val buttonInfo = (input as? PadOverlayDpad)?.getInfo() ?: (input as? PadOverlayButton)?.getInfo()
                        val inputEnabled = (input as? PadOverlayDpad)?.enabled ?: (input as? PadOverlayButton)?.enabled

                        val mappedInfo = buttonInfo?.let { Triple(
                            it.first, it.second,
                            it.third
                        ) }
                        state.onInputSelected(mappedInfo, inputEnabled)
                    }
                    state.padOverlay = this
                }
            }
        )

        if (!state.isPanelVisible) {
            FloatingActionButton(
                onClick = { state.isPanelVisible = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(painterResource(id = R.drawable.ic_keyboard_arrow_down), contentDescription = "Open Control Panel")
            }
        }

        AnimatedVisibility(
            visible = state.isPanelVisible,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.8f, animationSpec = tween(300)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f, animationSpec = tween(200))
        ) {
            ControlPanel(state = state)
        }

        if (state.showResetDialog) {
            ResetDialog(
                buttonName = state.currentButtonName,
                onConfirm = {
                    state.showResetDialog = false
                    state.padOverlay?.resetButtonConfigs()

                    val buttonInfo = (state.padOverlay?.selectedInput as? PadOverlayDpad)?.getInfo() ?: (state.padOverlay?.selectedInput as? PadOverlayButton)?.getInfo() ?: return@ResetDialog
                    val inputEnabled = (state.padOverlay?.selectedInput as? PadOverlayDpad)?.enabled ?: (state.padOverlay?.selectedInput as? PadOverlayButton)?.enabled ?: return@ResetDialog

                    state.scaleValue = buttonInfo.second.toFloat()
                    state.opacityValue = buttonInfo.third.toFloat()
                    state.isEnabled = inputEnabled
                },
                onDismiss = { state.showResetDialog = false }
            )
        }
    }
}

@Composable
fun ResetDialog(buttonName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ask_if_reset_button, buttonName)) },
        text = { Text(stringResource(R.string.ask_if_reset_button_description, buttonName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

private fun applyWindowInsets(view: View) {
    ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            setMargins(insets.left, insets.top, insets.right, insets.bottom)
        }
        WindowInsetsCompat.CONSUMED
    }
}