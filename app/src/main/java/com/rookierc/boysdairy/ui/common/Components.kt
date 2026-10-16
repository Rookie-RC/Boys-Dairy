package com.rookierc.boysdairy.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rookierc.boysdairy.ui.theme.FunTheme

/** Inset set used for nested Scaffolds whose parent already handles the system bars. */
val NoInsets = WindowInsets(0, 0, 0, 0)

/** Big emoji + message, used for empty lists and "nothing here" states. */
@Composable
fun EmptyState(
    emoji: String,
    message: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = emoji, fontSize = 64.sp)
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = FunTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = FunTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Decorative emoji. Hidden from accessibility services: every emoji in the app sits next to a
 * text label (or a click label) that already says what it means.
 */
@Composable
fun EmojiText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontSize = fontSize,
        modifier = modifier.clearAndSetSemantics { },
    )
}
