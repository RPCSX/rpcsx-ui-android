package net.rpcsx.ui.settings.components.core

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.rpcsx.R

@Composable
fun DeletableListItem(
    onDelete: (() -> Unit)?, modifier: Modifier = Modifier, body: @Composable () -> Unit = {},
) {
    if (onDelete == null) {
        body()
        return
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        })

    SwipeToDismissBox(
        modifier = modifier.animateContentSize(),
        state = dismissState,
        backgroundContent = {
            if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box (
                    modifier = Modifier.fillMaxSize()
                        .background(
                            Color.Red,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = "Delete",
                        tint = Color.White,
                    )
                }
            }
        },
        content = { body() },
        enableDismissFromEndToStart = true,
        enableDismissFromStartToEnd = false
    )
}
