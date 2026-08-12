package net.rpcsx.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class OverlayEditorState {
    var isPanelVisible by mutableStateOf(true)
    var scaleValue by mutableFloatStateOf(50f)
    var opacityValue by mutableFloatStateOf(100f)
    var isEnabled by mutableStateOf(true)
    var currentButtonName by mutableStateOf("Everything")
    var showResetDialog by mutableStateOf(false)
    var padOverlay: PadOverlay? by mutableStateOf(null)

    fun onInputSelected(info: Triple<String, Int, Int>?, enabled: Boolean?) {
        if (info != null) {
            currentButtonName = info.first
            scaleValue = info.second.toFloat()
            opacityValue = info.third.toFloat()
        } else {
            currentButtonName = "Everything"
        }
        if (enabled != null) {
            isEnabled = enabled
        }
    }
}

@Composable
fun rememberOverlayEditorState(): OverlayEditorState {
    return remember { OverlayEditorState() }
}