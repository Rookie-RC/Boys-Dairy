package com.rookierc.boysdairy.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.domain.filterEntriesByNote
import com.rookierc.boysdairy.ui.common.EmptyState
import com.rookierc.boysdairy.ui.common.EntryCard
import com.rookierc.boysdairy.ui.common.FunConfirmDialog
import com.rookierc.boysdairy.ui.common.FunTextField
import com.rookierc.boysdairy.ui.common.LocalSnackbar
import com.rookierc.boysdairy.ui.common.ScreenTitle
import com.rookierc.boysdairy.ui.common.StickerBadge
import com.rookierc.boysdairy.ui.common.quantityString
import com.rookierc.boysdairy.ui.theme.FunTheme

/** "战绩回放": every entry, newest first, searchable by note, deletable with undo. */
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val deleteMessages = stringArrayResource(R.array.delete_toasts)
    val undoLabel = stringResource(R.string.action_undo)

    // Plain `remember`: the query is intentionally not persisted anywhere (privacy).
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Entry?>(null) }

    val allEntries = entries
    val visibleEntries = remember(allEntries, query) {
        if (allEntries == null) emptyList<Entry>() else filterEntriesByNote(allEntries, query)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenTitle(
                emoji = "📼",
                title = stringResource(R.string.history_title),
                subtitle = stringResource(R.string.history_subtitle),
                trailing = if (allEntries != null && allEntries.isNotEmpty()) {
                    {
                        StickerBadge(
                            text = quantityString(R.plurals.history_count, allEntries.size, allEntries.size),
                            containerColor = FunTheme.colors.lemon,
                        )
                    }
                } else {
                    null
                },
            )

            FunTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.history_search_hint),
                leadingEmoji = "🔍",
                singleLine = true,
                trailing = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.history_search_clear),
                            )
                        }
                    }
                } else {
                    null
                },
            )
        }

        when {
            allEntries == null -> Box(Modifier.fillMaxSize())

            visibleEntries.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (allEntries.isEmpty()) {
                    EmptyState(
                        emoji = "🧘",
                        message = stringResource(R.string.history_empty),
                        hint = stringResource(R.string.history_empty_hint),
                    )
                } else {
                    EmptyState(
                        emoji = "🔍",
                        message = stringResource(R.string.history_no_results),
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items = visibleEntries, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        onDelete = { pendingDelete = entry },
                    )
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        FunConfirmDialog(
            title = stringResource(R.string.history_delete_title),
            emoji = "🗑️",
            message = stringResource(R.string.history_delete_message),
            confirmText = stringResource(R.string.history_delete_confirm),
            confirmColor = FunTheme.colors.danger,
            onConfirm = {
                viewModel.delete(entry.id)
                pendingDelete = null
                snackbar.show(
                    message = deleteMessages.random(),
                    actionLabel = undoLabel,
                    onAction = { viewModel.restore(entry) },
                )
            },
            dismissText = stringResource(R.string.history_delete_keep),
            onDismiss = { pendingDelete = null },
        )
    }
}
