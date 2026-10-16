package com.rookierc.boysdairy.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rookierc.boysdairy.ui.theme.FunShapes
import com.rookierc.boysdairy.ui.theme.FunTheme

/**
 * Single-choice pill with an optional leading emoji. Unselected = flat outlined pill,
 * selected = [selectedColor] fill with a small hard shadow.
 */
@Composable
fun EmojiChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    selectedColor: Color = FunTheme.colors.lemon,
) {
    val colors = FunTheme.colors
    FunSurface(
        modifier = modifier.semantics { this.selected = selected },
        shape = FunShapes.chip,
        containerColor = if (selected) selectedColor else colors.surface,
        contentColor = if (selected) colors.onAccent else colors.textPrimary,
        shadowOffset = 3.dp,
        showShadow = selected,
        onClick = onClick,
        role = Role.RadioButton,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 44.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (emoji != null) {
                EmojiText(text = emoji, fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Big selectable card (entry type picker, roast level). Same select/unselect language as [EmojiChip]. */
@Composable
fun ChoiceCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = FunTheme.colors.lemon,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FunTheme.colors
    FunSurface(
        modifier = modifier.semantics { this.selected = selected },
        shape = FunShapes.card,
        containerColor = if (selected) selectedColor else colors.surface,
        contentColor = if (selected) colors.onAccent else colors.textPrimary,
        shadowOffset = 4.dp,
        showShadow = selected,
        onClick = onClick,
        role = Role.RadioButton,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

/** Little tilted label sticker. */
@Composable
fun StickerBadge(
    text: String,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    containerColor: Color = FunTheme.colors.lemon,
    contentColor: Color = FunTheme.colors.onAccent,
    rotation: Float = -3f,
) {
    FunSurface(
        modifier = modifier.rotate(rotation),
        shape = FunShapes.chip,
        containerColor = containerColor,
        contentColor = contentColor,
        shadowOffset = 2.dp,
        borderWidth = 2.dp,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (emoji != null) {
                EmojiText(text = emoji, fontSize = 14.sp)
                Spacer(Modifier.width(4.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
