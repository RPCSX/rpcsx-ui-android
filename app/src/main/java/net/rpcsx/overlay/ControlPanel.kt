package net.rpcsx.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.rpcsx.R
import kotlin.math.roundToInt

@Composable
fun ControlPanel(state: OverlayEditorState) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    val panelWidth = 336f
    val panelHeight = 200f

    var panelOffset by remember {
        mutableStateOf(
            IntOffset(
                ((screenWidth - panelWidth) / 2f).toInt(),
                ((screenHeight - panelHeight) / 2f).toInt()
            )
        )
    }

    Box(
        modifier = Modifier
            .offset { panelOffset }
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), RoundedCornerShape(8.dp))
            .padding(10.dp)
            .width(336.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    panelOffset = IntOffset(
                        x = panelOffset.x + dragAmount.x.toInt(),
                        y = panelOffset.y + dragAmount.y.toInt()
                    )
                }
            }
    ) {
        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.size(48.dp))
                Text(
                    text = stringResource(R.string.control_panel),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { state.isPanelVisible = false }) {
                    Icon(painterResource(id = R.drawable.ic_close), "Close", tint = MaterialTheme.colorScheme.error)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), thickness = 2.dp)
            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = "${stringResource(R.string.editing)}: ${state.currentButtonName}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            DirectionalControls(state)

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    SliderComponent(
                        label = stringResource(R.string.scale),
                        value = state.scaleValue,
                        onValueChange = {
                            state.scaleValue = it
                            state.padOverlay?.setButtonScale(it.roundToInt())
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SliderComponent(
                        label = stringResource(R.string.opacity),
                        value = state.opacityValue,
                        onValueChange = {
                            state.opacityValue = it
                            state.padOverlay?.setButtonOpacity(it.roundToInt())
                        }
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { state.showResetDialog = true },
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors()
                ) {
                    Icon(painterResource(id = R.drawable.ic_restore), "Reset", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun DirectionalControls(state: OverlayEditorState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = { state.padOverlay?.moveButtonUp() }) {
            Icon(painterResource(id = R.drawable.ic_keyboard_arrow_up), "Move Up", tint = MaterialTheme.colorScheme.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.padOverlay?.moveButtonLeft() }) {
                Icon(painterResource(id = R.drawable.ic_keyboard_arrow_left), "Move Left", tint = MaterialTheme.colorScheme.primary)
            }
            Checkbox(
                checked = state.isEnabled,
                onCheckedChange = {
                    state.isEnabled = it
                    state.padOverlay?.enableButton(it)
                },
                modifier = Modifier.padding(4.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            )
            IconButton(onClick = { state.padOverlay?.moveButtonRight() }) {
                Icon(painterResource(id = R.drawable.ic_keyboard_arrow_right), "Move Right", tint = MaterialTheme.colorScheme.primary)
            }
        }
        IconButton(onClick = { state.padOverlay?.moveButtonDown() }) {
            Icon(painterResource(id = R.drawable.ic_keyboard_arrow_down), "Move Down", tint = MaterialTheme.colorScheme.primary)
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