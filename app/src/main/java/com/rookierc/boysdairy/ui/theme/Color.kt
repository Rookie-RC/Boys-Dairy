package com.rookierc.boysdairy.ui.theme

import androidx.compose.ui.graphics.Color
import com.rookierc.boysdairy.domain.EntryType

// Entry colors (same in light and dark)
val SoloColor = Color(0xFF3B82F6)
val PartnerColor = Color(0xFFEF4444)

fun EntryType.color(): Color = when (this) {
    EntryType.SOLO -> SoloColor
    EntryType.PARTNER -> PartnerColor
}

// Dark palette (slate)
internal val DarkBackground = Color(0xFF020617)
internal val DarkSurface = Color(0xFF0F172A)
internal val DarkSurfaceVariant = Color(0xFF1E293B)
internal val DarkOutline = Color(0xFF334155)
internal val DarkOnSurface = Color(0xFFF1F5F9)
internal val DarkOnSurfaceVariant = Color(0xFF94A3B8)
internal val DarkPrimary = Color(0xFF3B82F6)

// Light palette (slate)
internal val LightBackground = Color(0xFFF8FAFC)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightSurfaceVariant = Color(0xFFF1F5F9)
internal val LightOutline = Color(0xFFCBD5E1)
internal val LightOnSurface = Color(0xFF0F172A)
internal val LightOnSurfaceVariant = Color(0xFF475569)
internal val LightPrimary = Color(0xFF2563EB)
