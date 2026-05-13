package net.rpcsx.ui.settings.components.base

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseDialogPreference(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = properties
    ) {
        DialogContent(
            icon = icon,
            title = title,
            content = content
        )
    }
}

@Composable
private fun DialogContent(
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)?,
    title: (@Composable () -> Unit)?,
    content: @Composable (() -> Unit)?
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.8f)
    )

    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(220)
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (icon != null || title != null) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    icon?.let {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CompositionLocalProvider(
                                    LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer
                                ) {
                                    icon()
                                }
                            }
                        }
                    }

                    title?.let {
                        ProvideContentColorTextStyle(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            textStyle = MaterialTheme.typography.titleLarge
                        ) {
                            title()
                        }
                    }
                }
            }

            content?.let {
                ProvideContentColorTextStyle(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    textStyle = MaterialTheme.typography.bodyLarge
                ) {
                    Box {
                        content()
                    }
                }
            }
        }
    }
}


@Composable
fun ProvideContentColorTextStyle(
    contentColor: Color,
    textStyle: TextStyle,
    content: @Composable () -> Unit
) {
    val mergedStyle = LocalTextStyle.current.merge(textStyle)
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalTextStyle provides mergedStyle,
        content = content
    )
}