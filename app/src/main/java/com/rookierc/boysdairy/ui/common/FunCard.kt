package com.rookierc.boysdairy.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rookierc.boysdairy.ui.theme.FunShapes
import com.rookierc.boysdairy.ui.theme.FunTheme

/**
 * Chunky card: 2dp outline, 20dp corners and a hard offset shadow. Clickable when [onClick] is set;
 * [onClickLabel] is what accessibility services announce as the click action.
 */
@Composable
fun FunCard(
    modifier: Modifier = Modifier,
    containerColor: Color = FunTheme.colors.surface,
    contentColor: Color = FunTheme.colors.textPrimary,
    shape: Shape = FunShapes.card,
    shadowOffset: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    FunSurface(
        modifier = modifier,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        shadowOffset = shadowOffset,
        onClick = onClick,
        enabled = enabled,
        role = if (onClick != null) Role.Button else null,
        onClickLabel = onClickLabel,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
