package com.rookierc.boysdairy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

private val base = Typography()

/** Material3 defaults with the headings pushed towards bold/extra-bold for the sticker look. */
val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.ExtraBold),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.ExtraBold),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Bold),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Bold),
)
