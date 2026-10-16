package com.rookierc.boysdairy.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rookierc.boysdairy.ui.theme.FunShapes
import com.rookierc.boysdairy.ui.theme.FunTheme

/**
 * The building block of the neo-brutalist look: a rounded, solidly outlined surface with a hard
 * (blur-free) offset shadow behind it.
 *
 * The shadow's space is always reserved at the end/bottom of the layout so nothing overlaps it.
 * When [onClick] is set the surface "presses down" on touch: it slides onto its shadow, which
 * makes the offset disappear. [showShadow] = false keeps the reserved space but draws a flat
 * surface (used for unselected chips).
 */
@Composable
fun FunSurface(
    modifier: Modifier = Modifier,
    shape: Shape = FunShapes.card,
    containerColor: Color = FunTheme.colors.surface,
    contentColor: Color = FunTheme.colors.textPrimary,
    borderColor: Color = FunTheme.colors.ink,
    shadowColor: Color = FunTheme.colors.shadow,
    shadowOffset: Dp = 4.dp,
    borderWidth: Dp = 2.dp,
    showShadow: Boolean = true,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    role: Role? = null,
    onClickLabel: String? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val press = animateFloatAsState(
        targetValue = if (pressed && enabled && showShadow) 1f else 0f,
        animationSpec = tween(durationMillis = 70),
        label = "funPress",
    )
    val offsetPx = with(LocalDensity.current) { shadowOffset.toPx() }

    Box(
        modifier = modifier
            .padding(end = shadowOffset, bottom = shadowOffset)
            .then(
                if (showShadow) {
                    Modifier.drawBehind {
                        val outline = shape.createOutline(size, layoutDirection, this)
                        translate(left = offsetPx, top = offsetPx) {
                            drawShadowOutline(outline = outline, color = shadowColor)
                        }
                    }
                } else {
                    Modifier
                },
            )
            .graphicsLayer {
                translationX = offsetPx * press.value
                translationY = offsetPx * press.value
            }
            .clip(shape)
            .background(containerColor)
            .border(width = borderWidth, color = borderColor, shape = shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        onClickLabel = onClickLabel,
                        role = role,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = contentAlignment,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/** Fills [outline] with a flat color (only uses DrawScope member functions). */
private fun DrawScope.drawShadowOutline(outline: Outline, color: Color) {
    when (outline) {
        is Outline.Rectangle -> drawRect(
            color = color,
            topLeft = outline.rect.topLeft,
            size = outline.rect.size,
        )

        is Outline.Rounded -> drawPath(
            path = Path().apply { addRoundRect(outline.roundRect) },
            color = color,
        )

        is Outline.Generic -> drawPath(path = outline.path, color = color)
    }
}
