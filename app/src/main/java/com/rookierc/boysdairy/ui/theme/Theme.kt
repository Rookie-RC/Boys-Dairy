package com.rookierc.boysdairy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Material3 is only used for behaviour-heavy widgets (pickers, bottom sheet, text input);
 * `primary` is the darker, text-safe pink so TextButtons and picker selections stay readable.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFFD6246E),
    onPrimary = Color.White,
    primaryContainer = LightFunColors.pinkTint,
    onPrimaryContainer = LightFunColors.ink,
    secondary = LightFunColors.grape,
    onSecondary = Color.White,
    secondaryContainer = LightFunColors.grapeTint,
    onSecondaryContainer = LightFunColors.ink,
    tertiary = Color(0xFF0B7A58),
    onTertiary = Color.White,
    tertiaryContainer = LightFunColors.mintTint,
    onTertiaryContainer = LightFunColors.ink,
    background = LightFunColors.background,
    onBackground = LightFunColors.ink,
    surface = LightFunColors.surface,
    onSurface = LightFunColors.ink,
    surfaceVariant = LightFunColors.surfaceAlt,
    onSurfaceVariant = LightFunColors.textSecondary,
    surfaceTint = Color(0xFFD6246E),
    inverseSurface = LightFunColors.ink,
    inverseOnSurface = LightFunColors.background,
    inversePrimary = LightFunColors.pink,
    outline = LightFunColors.ink,
    outlineVariant = Color(0xFFD9CFB8),
    error = Color(0xFFC81E1E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410002),
    scrim = LightFunColors.ink,
    surfaceBright = LightFunColors.surface,
    surfaceDim = LightFunColors.background,
    surfaceContainerLowest = LightFunColors.surface,
    surfaceContainerLow = LightFunColors.surface,
    surfaceContainer = LightFunColors.surface,
    surfaceContainerHigh = LightFunColors.surface,
    surfaceContainerHighest = LightFunColors.surfaceAlt,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8FB8),
    onPrimary = DarkFunColors.onAccent,
    primaryContainer = DarkFunColors.pinkTint,
    onPrimaryContainer = Color(0xFFFFD6E6),
    secondary = DarkFunColors.grape,
    onSecondary = DarkFunColors.onAccent,
    secondaryContainer = DarkFunColors.grapeTint,
    onSecondaryContainer = Color(0xFFE2DAFF),
    tertiary = DarkFunColors.mint,
    onTertiary = DarkFunColors.onAccent,
    tertiaryContainer = DarkFunColors.mintTint,
    onTertiaryContainer = Color(0xFFC9F5E6),
    background = DarkFunColors.background,
    onBackground = DarkFunColors.textPrimary,
    surface = DarkFunColors.surface,
    onSurface = DarkFunColors.textPrimary,
    surfaceVariant = DarkFunColors.surfaceAlt,
    onSurfaceVariant = DarkFunColors.textSecondary,
    surfaceTint = Color(0xFFFF8FB8),
    inverseSurface = DarkFunColors.textPrimary,
    inverseOnSurface = DarkFunColors.background,
    inversePrimary = Color(0xFFD6246E),
    outline = DarkFunColors.ink,
    outlineVariant = Color(0xFF3E3562),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF410002),
    errorContainer = Color(0xFF7A1F18),
    onErrorContainer = Color(0xFFFFDAD4),
    scrim = Color.Black,
    surfaceBright = Color(0xFF362E57),
    surfaceDim = DarkFunColors.background,
    surfaceContainerLowest = DarkFunColors.background,
    surfaceContainerLow = DarkFunColors.surface,
    surfaceContainer = DarkFunColors.surface,
    surfaceContainerHigh = DarkFunColors.surfaceAlt,
    surfaceContainerHighest = Color(0xFF362E57),
)

/** Shapes shared by the chunky components. */
object FunShapes {
    val card = RoundedCornerShape(20.dp)
    val button = RoundedCornerShape(18.dp)
    val key = RoundedCornerShape(20.dp)
    val field = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(50)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Material3 theme + fun palette. Dynamic color is intentionally disabled. */
@Composable
fun BoysDairyTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val funColors = if (darkTheme) DarkFunColors else LightFunColors
    CompositionLocalProvider(LocalFunColors provides funColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
