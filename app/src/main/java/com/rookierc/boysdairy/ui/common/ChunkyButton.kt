package com.rookierc.boysdairy.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rookierc.boysdairy.ui.theme.FunShapes
import com.rookierc.boysdairy.ui.theme.FunTheme

/**
 * Chunky button with a free-form content row. Pressing it slides the button onto its shadow.
 * Text colour defaults to the dark ink that is readable on every accent fill.
 */
@Composable
fun ChunkyButtonSlot(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = FunTheme.colors.pink,
    contentColor: Color = FunTheme.colors.onAccent,
    enabled: Boolean = true,
    shape: Shape = FunShapes.button,
    shadowOffset: Dp = 4.dp,
    minHeight: Dp = 52.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val colors = FunTheme.colors
    FunSurface(
        modifier = modifier,
        shape = shape,
        containerColor = if (enabled) containerColor else colors.disabled,
        contentColor = if (enabled) contentColor else colors.onDisabled,
        shadowColor = if (enabled) colors.shadow else colors.shadow.copy(alpha = 0.35f),
        shadowOffset = shadowOffset,
        onClick = onClick,
        enabled = enabled,
        role = Role.Button,
        contentAlignment = Alignment.Center,
    ) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium) {
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = minHeight)
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/** Text (+ optional leading emoji) variant of [ChunkyButtonSlot]. */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    containerColor: Color = FunTheme.colors.pink,
    contentColor: Color = FunTheme.colors.onAccent,
    enabled: Boolean = true,
    shadowOffset: Dp = 4.dp,
    minHeight: Dp = 52.dp,
) {
    ChunkyButtonSlot(
        onClick = onClick,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        enabled = enabled,
        shadowOffset = shadowOffset,
        minHeight = minHeight,
    ) {
        if (emoji != null) {
            EmojiText(text = emoji, fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, textAlign = TextAlign.Center)
    }
}

/**
 * Small square-ish button for icons / emoji (back, month arrows, delete). 48dp touch target.
 * When not [enabled] it uses the same muted look as a disabled [ChunkyButtonSlot].
 */
@Composable
fun ChunkyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = FunTheme.colors.lemon,
    contentColor: Color = FunTheme.colors.onAccent,
    enabled: Boolean = true,
    size: Dp = 52.dp,
    shadowOffset: Dp = 3.dp,
    shape: Shape = FunShapes.field,
    content: @Composable () -> Unit,
) {
    val colors = FunTheme.colors
    FunSurface(
        modifier = modifier.size(size),
        shape = shape,
        containerColor = if (enabled) containerColor else colors.disabled,
        contentColor = if (enabled) contentColor else colors.onDisabled,
        shadowColor = if (enabled) colors.shadow else colors.shadow.copy(alpha = 0.35f),
        shadowOffset = shadowOffset,
        onClick = onClick,
        enabled = enabled,
        role = Role.Button,
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
