package com.rookierc.boysdairy.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.rookierc.boysdairy.domain.EntryType

/**
 * Palette tokens of the playful neo-brutalist look. Material3's [androidx.compose.material3.ColorScheme]
 * is derived from the same values (see Theme.kt) but the fun components read from here.
 *
 * Rules of thumb that keep text readable (>= 4.5:1):
 *  - text on pink / lemon / mint / solo / danger fills is always [onAccent] (dark ink),
 *  - text on grape fills is [onGrape],
 *  - text on tints, cards and the background is [textPrimary].
 */
@Immutable
class FunColors(
    val isDark: Boolean,
    /** Outline colour of every chunky element (dark ink in light mode, pale lavender in dark mode). */
    val ink: Color,
    /** Hard offset shadow behind chunky elements. */
    val shadow: Color,
    val background: Color,
    val surface: Color,
    /** Slightly tinted surface for tracks, inactive parts and quiet boxes. */
    val surfaceAlt: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val pink: Color,
    val lemon: Color,
    val mint: Color,
    val grape: Color,
    val solo: Color,
    val danger: Color,
    val onAccent: Color,
    val onGrape: Color,
    val pinkTint: Color,
    val lemonTint: Color,
    val mintTint: Color,
    val grapeTint: Color,
    val soloTint: Color,
    val disabled: Color,
    val onDisabled: Color,
)

internal val LightFunColors = FunColors(
    isDark = false,
    ink = Color(0xFF1B1530),
    shadow = Color(0xFF1B1530),
    background = Color(0xFFFFF7E6),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF6EBD0),
    textPrimary = Color(0xFF1B1530),
    textSecondary = Color(0xFF5B5470),
    pink = Color(0xFFFF4F8B),
    lemon = Color(0xFFFFD43B),
    mint = Color(0xFF2BD9A0),
    grape = Color(0xFF6A47F5),
    solo = Color(0xFF3D9BFF),
    danger = Color(0xFFFF6B5E),
    onAccent = Color(0xFF1B1530),
    onGrape = Color(0xFFFFFFFF),
    pinkTint = Color(0xFFFFD6E6),
    lemonTint = Color(0xFFFFF0B3),
    mintTint = Color(0xFFC9F5E6),
    grapeTint = Color(0xFFE2DAFF),
    soloTint = Color(0xFFD4E8FF),
    disabled = Color(0xFFE4DCCB),
    onDisabled = Color(0xFF6F6888),
)

internal val DarkFunColors = FunColors(
    isDark = true,
    ink = Color(0xFFF4EEFF),
    shadow = Color(0xFF050309),
    background = Color(0xFF141022),
    surface = Color(0xFF211B36),
    surfaceAlt = Color(0xFF2C2547),
    textPrimary = Color(0xFFF4EEFF),
    textSecondary = Color(0xFFC4BAE0),
    pink = Color(0xFFFF6FA1),
    lemon = Color(0xFFFFDB58),
    mint = Color(0xFF3DE8B0),
    grape = Color(0xFFA58FFF),
    solo = Color(0xFF5BADFF),
    danger = Color(0xFFFF7A6E),
    onAccent = Color(0xFF1B1530),
    onGrape = Color(0xFF1B1530),
    pinkTint = Color(0xFF4A2140),
    lemonTint = Color(0xFF4A4220),
    mintTint = Color(0xFF17413A),
    grapeTint = Color(0xFF35296B),
    soloTint = Color(0xFF1D3556),
    disabled = Color(0xFF3A3358),
    onDisabled = Color(0xFF9C93BC),
)

val LocalFunColors = staticCompositionLocalOf { LightFunColors }

/** Entry point for the fun palette: `FunTheme.colors.pink`. */
object FunTheme {
    val colors: FunColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFunColors.current
}

/** Saturated accent of an entry type: electric blue for solo, hot pink for partner. */
@Composable
@ReadOnlyComposable
fun EntryType.accent(): Color = when (this) {
    EntryType.SOLO -> FunTheme.colors.solo
    EntryType.PARTNER -> FunTheme.colors.pink
}

/** Soft tint of an entry type, for calendar cells and quiet backgrounds. */
@Composable
@ReadOnlyComposable
fun EntryType.tint(): Color = when (this) {
    EntryType.SOLO -> FunTheme.colors.soloTint
    EntryType.PARTNER -> FunTheme.colors.pinkTint
}
