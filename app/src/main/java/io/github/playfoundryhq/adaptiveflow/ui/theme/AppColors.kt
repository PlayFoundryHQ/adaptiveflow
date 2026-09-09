package io.github.playfoundryhq.adaptiveflow.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour tokens for AdaptiveFlow. Screens read these via
 * [AppTheme.colors] instead of hard-coding hex values, so the whole app can
 * switch between light and dark.
 *
 * Migration is screen-by-screen — a screen that still hard-codes `Color(0x…)`
 * simply won't respond to dark mode yet. Decorative per-language palettes
 * (`DeckStyle`) are intentionally left literal for now.
 */
@Immutable
data class AppColors(
    val isDark: Boolean,
    /** MainActivity's full-bleed background gradient (top → bottom). */
    val screenGradient: List<Color>,
    /** Cards, sheets, dialogs, menus. Was `Color.White`. */
    val surface: Color,
    /** Subtle fills: chips, inset rows, progress tracks. Was `#F1F5F9` / `#F8FAFC`. */
    val surfaceMuted: Color,
    /** Hairline borders and dividers. Was `#E2E8F0` / `#F1F5F9`. */
    val hairline: Color,
    /** Headings and high-emphasis text. Was `#1E293B` / `#0F172A`. */
    val textPrimary: Color,
    /** Body and secondary text. Was `#64748B` / `#475569`. */
    val textSecondary: Color,
    /** Captions, hints, disabled text. Was `#94A3B8`. */
    val textFaint: Color,
    /** Brand accent (buttons, active nav, links). Was `#0054D1`. */
    val accent: Color,
    /** Tinted background behind accent content. Was `#EFF6FF` / `#E0E7FF`. */
    val accentMuted: Color,
    /** Text/icons on top of [accent]. */
    val onAccent: Color,
    /** The dark "active study goal" hero banner. */
    val heroSurface: Color,
    val heroText: Color,
    val heroTextMuted: Color,
    val success: Color,
    val successMuted: Color,
    val warning: Color,
    val warningMuted: Color,
    val danger: Color,
    val dangerMuted: Color,
)

val LightAppColors = AppColors(
    isDark = false,
    screenGradient = listOf(Color(0xFFEEF2FF), Color(0xFFF5F3FF), Color(0xFFF8FAFC)),
    surface = Color.White,
    surfaceMuted = Color(0xFFF1F5F9),
    hairline = Color(0xFFE2E8F0),
    textPrimary = Color(0xFF1E293B),
    textSecondary = Color(0xFF64748B),
    textFaint = Color(0xFF94A3B8),
    accent = Color(0xFF0054D1),
    accentMuted = Color(0xFFEFF6FF),
    onAccent = Color.White,
    heroSurface = Color(0xFF0F172A),
    heroText = Color.White,
    heroTextMuted = Color(0xCCFFFFFF),
    success = Color(0xFF16A34A),
    successMuted = Color(0xFFF0FDF4),
    warning = Color(0xFFD97706),
    warningMuted = Color(0xFFFEF3C7),
    danger = Color(0xFFDC2626),
    dangerMuted = Color(0xFFFEE2E2),
)

val DarkAppColors = AppColors(
    isDark = true,
    screenGradient = listOf(Color(0xFF0B1120), Color(0xFF111827), Color(0xFF0F172A)),
    surface = Color(0xFF1E293B),
    surfaceMuted = Color(0xFF273549),
    hairline = Color(0xFF334155),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textFaint = Color(0xFF64748B),
    accent = Color(0xFF60A5FA),
    accentMuted = Color(0xFF1E3A5F),
    onAccent = Color(0xFF0B1120),
    heroSurface = Color(0xFF1E293B),
    heroText = Color(0xFFF1F5F9),
    heroTextMuted = Color(0xB3F1F5F9),
    success = Color(0xFF34D399),
    successMuted = Color(0xFF12351F),
    warning = Color(0xFFFBBF24),
    warningMuted = Color(0xFF3A2E12),
    danger = Color(0xFFF87171),
    dangerMuted = Color(0xFF3B1D1D),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** Entry point for semantic colours: `AppTheme.colors.surface`. */
object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}
