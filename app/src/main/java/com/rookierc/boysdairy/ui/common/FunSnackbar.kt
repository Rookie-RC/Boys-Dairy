package com.rookierc.boysdairy.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rookierc.boysdairy.ui.theme.FunTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * App-wide snackbar entry point. It lives in the main navigation, so a snackbar (e.g. the delete
 * "undo") survives navigating between tabs.
 */
@Stable
class FunSnackbarController(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    /** [long] keeps a message without an action up longer (e.g. a save toast plus an unlocked achievement). */
    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        long: Boolean = false,
    ) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = if (actionLabel != null || long) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                onAction?.invoke()
            }
        }
    }
}

val LocalSnackbar = staticCompositionLocalOf<FunSnackbarController> {
    error("FunSnackbarController not provided")
}

@Composable
fun FunSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        FunSnackbar(data)
    }
}

@Composable
private fun FunSnackbar(data: SnackbarData) {
    val colors = FunTheme.colors
    FunCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        containerColor = colors.lemon,
        contentColor = colors.onAccent,
        shadowOffset = 4.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            val actionLabel = data.visuals.actionLabel
            if (actionLabel != null) {
                ChunkyButton(
                    text = actionLabel,
                    onClick = { data.performAction() },
                    containerColor = colors.pink,
                    shadowOffset = 3.dp,
                    minHeight = 44.dp,
                )
            }
        }
    }
}
