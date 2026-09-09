package io.github.playfoundryhq.adaptiveflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Master switch for dark mode. Flipped to `true` once every screen reads
 * [AppTheme.colors] instead of hard-coded hex — until then a system dark theme
 * would leave un-migrated screens with dark text on a dark background.
 * Migration progress: MainActivity nav, Decks, Quest, Guide done.
 */
const val DARK_MODE_ENABLED = false

/**
 * AdaptiveFlow has a deliberate brand palette, so Material You dynamic colour is
 * off. The real colour surface for screens is [AppTheme.colors]; the M3
 * `colorScheme` here only backs system chrome (ripples, text selection,
 * date pickers, …) and is derived from the same tokens.
 */
@Composable
fun AdaptiveFlowTheme(
    darkTheme: Boolean = DARK_MODE_ENABLED && isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = appColors.accent,
            onPrimary = appColors.onAccent,
            background = appColors.screenGradient.last(),
            surface = appColors.surface,
            onSurface = appColors.textPrimary,
            onBackground = appColors.textPrimary,
            error = appColors.danger,
        )
    } else {
        lightColorScheme(
            primary = appColors.accent,
            onPrimary = appColors.onAccent,
            background = appColors.screenGradient.last(),
            surface = appColors.surface,
            onSurface = appColors.textPrimary,
            onBackground = appColors.textPrimary,
            error = appColors.danger,
        )
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
