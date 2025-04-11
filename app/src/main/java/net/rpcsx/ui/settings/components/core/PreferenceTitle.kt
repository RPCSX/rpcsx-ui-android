package net.rpcsx.ui.settings.components.core

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import net.rpcsx.ui.settings.components.LocalPreferenceState
import net.rpcsx.ui.settings.util.preferenceColor
import net.rpcsx.ui.settings.util.preferenceSubtitleColor

@Composable
internal fun PreferenceTitle(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalPreferenceState.current,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    style: TextStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = 17.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp
    ),
    color: Color = preferenceColor(enabled, LocalContentColor.current)
) {
    Text(
        text = title,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        color = color,
    )
}

@Composable
internal fun PreferenceValue(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalPreferenceState.current,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    style: TextStyle = MaterialTheme.typography.labelMedium.copy(
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
    ),
    color: Color = preferenceColor(enabled, LocalContentColor.current)
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        color = color,
    )
}

@Composable
internal fun PreferenceTitle(
    title: AnnotatedString,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalPreferenceState.current,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = preferenceColor(enabled, LocalContentColor.current)
) {
    Text(
        text = title,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        color = color,
    )
}

@Composable
fun PreferenceSubtitle(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalPreferenceState.current,
    maxLines: Int = 2,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = preferenceSubtitleColor(enabled, LocalContentColor.current),
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        color = color,
    )
}

@Composable
fun PreferenceSubtitle(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalPreferenceState.current,
    maxLines: Int = 2,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = preferenceSubtitleColor(enabled, LocalContentColor.current),
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        color = color,
    )
}
