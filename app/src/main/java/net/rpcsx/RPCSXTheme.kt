
package net.rpcsx

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

object colors {
    val primaryLight = Color(0xFF4D5C92)
    val onPrimaryLight = Color(0xFFFFFFFF)
    val primaryContainerLight = Color(0xFFDCE1FF)
    val onPrimaryContainerLight = Color(0xFF354479)
    val secondaryLight = Color(0xFF595D72)
    val onSecondaryLight = Color(0xFFFFFFFF)
    val secondaryContainerLight = Color(0xFFDEE1F9)
    val onSecondaryContainerLight = Color(0xFF424659)
    val tertiaryLight = Color(0xFF75546F)
    val onTertiaryLight = Color(0xFFFFFFFF)
    val tertiaryContainerLight = Color(0xFFFFD7F5)
    val onTertiaryContainerLight = Color(0xFF5B3D57)
    val errorLight = Color(0xFFBA1A1A)
    val onErrorLight = Color(0xFFFFFFFF)
    val errorContainerLight = Color(0xFFFFDAD6)
    val onErrorContainerLight = Color(0xFF93000A)
    val backgroundLight = Color(0xFFFAF8FF)
    val onBackgroundLight = Color(0xFF1A1B21)
    val surfaceLight = Color(0xFFFAF8FF)
    val onSurfaceLight = Color(0xFF1A1B21)
    val surfaceVariantLight = Color(0xFFE2E1EC)
    val onSurfaceVariantLight = Color(0xFF45464F)
    val outlineLight = Color(0xFF767680)
    val outlineVariantLight = Color(0xFFC6C6D0)
    val scrimLight = Color(0xFF000000)
    val inverseSurfaceLight = Color(0xFF2F3036)
    val inverseOnSurfaceLight = Color(0xFFF1F0F7)
    val inversePrimaryLight = Color(0xFFB6C4FF)
    val surfaceDimLight = Color(0xFFDAD9E0)
    val surfaceBrightLight = Color(0xFFFAF8FF)
    val surfaceContainerLowestLight = Color(0xFFFFFFFF)
    val surfaceContainerLowLight = Color(0xFFF4F3FA)
    val surfaceContainerLight = Color(0xFFEEEDF4)
    val surfaceContainerHighLight = Color(0xFFE9E7EF)
    val surfaceContainerHighestLight = Color(0xFFE3E1E9)

    val primaryDark = Color(0xFFB6C4FF)
    val onPrimaryDark = Color(0xFF1D2D61)
    val primaryContainerDark = Color(0xFF354479)
    val onPrimaryContainerDark = Color(0xFFDCE1FF)
    val secondaryDark = Color(0xFFC2C5DD)
    val onSecondaryDark = Color(0xFF2B3042)
    val secondaryContainerDark = Color(0xFF424659)
    val onSecondaryContainerDark = Color(0xFFDEE1F9)
    val tertiaryDark = Color(0xFFE3BADA)
    val onTertiaryDark = Color(0xFF432740)
    val tertiaryContainerDark = Color(0xFF5B3D57)
    val onTertiaryContainerDark = Color(0xFFFFD7F5)
    val errorDark = Color(0xFFFFB4AB)
    val onErrorDark = Color(0xFF690005)
    val errorContainerDark = Color(0xFF93000A)
    val onErrorContainerDark = Color(0xFFFFDAD6)
    val backgroundDark = Color(0xFF121318)
    val onBackgroundDark = Color(0xFFE3E1E9)
    val surfaceDark = Color(0xFF121318)
    val onSurfaceDark = Color(0xFFE3E1E9)
    val surfaceVariantDark = Color(0xFF45464F)
    val onSurfaceVariantDark = Color(0xFFC6C6D0)
    val outlineDark = Color(0xFF90909A)
    val outlineVariantDark = Color(0xFF45464F)
    val scrimDark = Color(0xFF000000)
    val inverseSurfaceDark = Color(0xFFE3E1E9)
    val inverseOnSurfaceDark = Color(0xFF2F3036)
    val inversePrimaryDark = Color(0xFF4D5C92)
    val surfaceDimDark = Color(0xFF121318)
    val surfaceBrightDark = Color(0xFF38393F)
    val surfaceContainerLowestDark = Color(0xFF0D0E13)
    val surfaceContainerLowDark = Color(0xFF1A1B21)
    val surfaceContainerDark = Color(0xFF1E1F25)
    val surfaceContainerHighDark = Color(0xFF292A2F)
    val surfaceContainerHighestDark = Color(0xFF34343A)
}

private val lightScheme = lightColorScheme(
    primary = colors.primaryLight,
    onPrimary = colors.onPrimaryLight,
    primaryContainer = colors.primaryContainerLight,
    onPrimaryContainer = colors.onPrimaryContainerLight,
    secondary = colors.secondaryLight,
    onSecondary = colors.onSecondaryLight,
    secondaryContainer = colors.secondaryContainerLight,
    onSecondaryContainer = colors.onSecondaryContainerLight,
    tertiary = colors.tertiaryLight,
    onTertiary = colors.onTertiaryLight,
    tertiaryContainer = colors.tertiaryContainerLight,
    onTertiaryContainer = colors.onTertiaryContainerLight,
    error = colors.errorLight,
    onError = colors.onErrorLight,
    errorContainer = colors.errorContainerLight,
    onErrorContainer = colors.onErrorContainerLight,
    background = colors.backgroundLight,
    onBackground = colors.onBackgroundLight,
    surface = colors.surfaceLight,
    onSurface = colors.onSurfaceLight,
    surfaceVariant = colors.surfaceVariantLight,
    onSurfaceVariant = colors.onSurfaceVariantLight,
    outline = colors.outlineLight,
    outlineVariant = colors.outlineVariantLight,
    scrim = colors.scrimLight,
    inverseSurface = colors.inverseSurfaceLight,
    inverseOnSurface = colors.inverseOnSurfaceLight,
    inversePrimary = colors.inversePrimaryLight,
    surfaceDim = colors.surfaceDimLight,
    surfaceBright = colors.surfaceBrightLight,
    surfaceContainerLowest = colors.surfaceContainerLowestLight,
    surfaceContainerLow = colors.surfaceContainerLowLight,
    surfaceContainer = colors.surfaceContainerLight,
    surfaceContainerHigh = colors.surfaceContainerHighLight,
    surfaceContainerHighest = colors.surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = colors.primaryDark,
    onPrimary = colors.onPrimaryDark,
    primaryContainer = colors.primaryContainerDark,
    onPrimaryContainer = colors.onPrimaryContainerDark,
    secondary = colors.secondaryDark,
    onSecondary = colors.onSecondaryDark,
    secondaryContainer = colors.secondaryContainerDark,
    onSecondaryContainer = colors.onSecondaryContainerDark,
    tertiary = colors.tertiaryDark,
    onTertiary = colors.onTertiaryDark,
    tertiaryContainer = colors.tertiaryContainerDark,
    onTertiaryContainer = colors.onTertiaryContainerDark,
    error = colors.errorDark,
    onError = colors.onErrorDark,
    errorContainer = colors.errorContainerDark,
    onErrorContainer = colors.onErrorContainerDark,
    background = colors.backgroundDark,
    onBackground = colors.onBackgroundDark,
    surface = colors.surfaceDark,
    onSurface = colors.onSurfaceDark,
    surfaceVariant = colors.surfaceVariantDark,
    onSurfaceVariant = colors.onSurfaceVariantDark,
    outline = colors.outlineDark,
    outlineVariant = colors.outlineVariantDark,
    scrim = colors.scrimDark,
    inverseSurface = colors.inverseSurfaceDark,
    inverseOnSurface = colors.inverseOnSurfaceDark,
    inversePrimary = colors.inversePrimaryDark,
    surfaceDim = colors.surfaceDimDark,
    surfaceBright = colors.surfaceBrightDark,
    surfaceContainerLowest = colors.surfaceContainerLowestDark,
    surfaceContainerLow = colors.surfaceContainerLowDark,
    surfaceContainer = colors.surfaceContainerDark,
    surfaceContainerHigh = colors.surfaceContainerHighDark,
    surfaceContainerHighest = colors.surfaceContainerHighestDark,
)

@Composable
fun RPCSXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // TODO(Ishan09811): Implement dynamic colors option whenever settings gets implemented
    val colors = if (darkTheme) darkScheme else lightScheme

    val view = LocalView.current
    val activity = view.context as? Activity

    SideEffect {
        activity?.window?.apply {
            statusBarColor = android.graphics.Color.TRANSPARENT
            navigationBarColor = android.graphics.Color.TRANSPARENT
            isNavigationBarContrastEnforced = false
            val insetsController = WindowInsetsControllerCompat(this, decorView)
            insetsController.isAppearanceLightNavigationBars = !darkTheme
            insetsController.isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}
