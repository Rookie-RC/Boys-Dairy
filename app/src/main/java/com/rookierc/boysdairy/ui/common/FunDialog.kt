package com.rookierc.boysdairy.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rookierc.boysdairy.ui.theme.FunTheme

/**
 * Dialog drawn as a [FunCard]. [content] is the body, [buttons] the stacked action buttons
 * (usually [ChunkyButton]s with `Modifier.fillMaxWidth()`).
 *
 * Dialogs inherit the window's secure flag by default, so FLAG_SECURE still covers them.
 */
@Composable
fun FunDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    dismissible: Boolean = true,
    containerColor: Color = FunTheme.colors.surface,
    buttons: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FunCard(
                modifier = modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 400.dp)
                    .fillMaxWidth(),
                containerColor = containerColor,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (emoji != null) {
                            EmojiText(text = emoji, fontSize = 30.sp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { heading() },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    content()
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = buttons,
                    )
                }
            }
        }
    }
}

/**
 * Two-button confirmation: a colored confirm button over a plain dismiss button.
 * [message] is the body text; [content] can add more below it.
 */
@Composable
fun FunConfirmDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    message: String? = null,
    confirmColor: Color = FunTheme.colors.pink,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    FunDialog(
        onDismissRequest = onDismiss,
        title = title,
        modifier = modifier,
        emoji = emoji,
        buttons = {
            ChunkyButton(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                containerColor = confirmColor,
                enabled = confirmEnabled,
            )
            ChunkyButton(
                text = dismissText,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                containerColor = FunTheme.colors.surface,
                contentColor = FunTheme.colors.textPrimary,
            )
        },
        content = {
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = FunTheme.colors.textPrimary,
                )
            }
            content()
        },
    )
}
