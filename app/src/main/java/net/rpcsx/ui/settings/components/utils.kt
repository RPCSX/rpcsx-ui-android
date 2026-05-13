package net.rpcsx.ui.settings.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.safeCombinedClickable(
    debounceTime: Long = 500L,
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier = composed {
    var lastClickTime by remember { mutableStateOf(0L) }

    this.combinedClickable(
        onClick = {
            val now = System.currentTimeMillis()
            if (now - lastClickTime > debounceTime) {
                lastClickTime = now
                onClick()
            }
        },
        onLongClick = {
            onLongClick()
        }
    )
}