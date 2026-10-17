package com.rookierc.boysdairy.ui.history

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.filterEntries
import com.rookierc.boysdairy.domain.groupEntriesByMonth
import com.rookierc.boysdairy.ui.common.EmojiChip
import com.rookierc.boysdairy.ui.common.EmptyState
import com.rookierc.boysdairy.ui.common.EntryCard
import com.rookierc.boysdairy.ui.common.FunConfirmDialog
import com.rookierc.boysdairy.ui.common.FunTextField
import com.rookierc.boysdairy.ui.common.LocalSnackbar
import com.rookierc.boysdairy.ui.common.ScreenTitle
import com.rookierc.boysdairy.ui.common.SectionHeader
import com.rookierc.boysdairy.ui.common.StickerBadge
import com.rookierc.boysdairy.ui.common.emoji
import com.rookierc.boysdairy.ui.common.quantityString
import com.rookierc.boysdairy.ui.common.rememberFormatter
import com.rookierc.boysdairy.ui.theme.FunTheme
import com.rookierc.boysdairy.ui.theme.accent
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.drop

/**
 * "战绩回放": every entry, newest first and grouped by month, searchable by note, filterable by
 * type, deletable with undo. Tapping a card opens it for editing via [onEditEntry].
 *
 * Title, search field and type chips are the first items of the list and scroll away with it
 * (only the month headers stick), so with large system fonts the list keeps most of the screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onEditEntry: (entryId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val deleteMessages = stringArrayResource(R.array.delete_toasts)
    val undoLabel = stringResource(R.string.action_undo)

    // Plain `remember`: the query and the type filter are intentionally not persisted (privacy).
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<EntryType?>(null) }
    var pendingDelete by remember { mutableStateOf<Entry?>(null) }

    val allEntries = entries
    val visibleEntries = remember(allEntries, query, typeFilter) {
        if (allEntries == null) emptyList<Entry>() else filterEntries(allEntries, query, typeFilter)
    }
    val monthGroups = remember(visibleEntries) {
        groupEntriesByMonth(visibleEntries, ZoneId.systemDefault())
    }

    val listState = rememberLazyListState()
    // A new search term or filter jumps back to the top, so the search field and the first results
    // are in view: the focused field never stays scrolled off screen with the keyboard still up.
    // The first value is skipped, so coming back to this tab keeps the restored scroll position.
    LaunchedEffect(listState) {
        snapshotFlow { query to typeFilter }
            .drop(1)
            .collect { listState.scrollToItem(0) }
    }

    // The 16 dp side gutter is applied per item instead of as content padding: that way a stuck
    // month header can paint its opaque background across the full width and the cards scrolling
    // underneath it (shadows included) never show through at the edges.
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "title", contentType = "title") {
            ScreenTitle(
                emoji = "📼",
                title = stringResource(R.string.history_title),
                subtitle = stringResource(R.string.history_subtitle),
                // Top padding here, not as content padding: a stuck month header sits flush at the top.
                modifier = Modifier.padding(start = ListGutter, end = ListGutter, top = 12.dp),
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
        }

        item(key = "search", contentType = "search") {
            FunTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ListGutter),
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

        item(key = "filters", contentType = "filters") {
            TypeFilterChips(
                selected = typeFilter,
                onSelect = { typeFilter = it },
                modifier = Modifier.padding(horizontal = ListGutter),
            )
        }

        when {
            allEntries == null -> Unit

            // Right under the search field, which stays usable when nothing matched.
            visibleEntries.isEmpty() -> item(key = "empty", contentType = "empty") {
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

            else -> monthGroups.forEach { (month, monthEntries) ->
                stickyHeader(key = "month-$month", contentType = "month") {
                    MonthHeader(month = month, count = monthEntries.size)
                }
                items(items = monthEntries, key = { it.id }, contentType = { "entry" }) { entry ->
                    EntryCard(
                        entry = entry,
                        onClick = { onEditEntry(entry.id) },
                        onDelete = { pendingDelete = entry },
                        modifier = Modifier.padding(horizontal = ListGutter),
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

private val ListGutter = 16.dp

/** "All / Solo / Co-op" chips; `null` = all types. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeFilterChips(
    selected: EntryType?,
    onSelect: (EntryType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EmojiChip(
            label = stringResource(R.string.history_filter_all),
            emoji = "🎞️",
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        EntryType.entries.forEach { type ->
            EmojiChip(
                label = stringResource(type.filterLabelRes()),
                emoji = type.emoji(),
                selected = selected == type,
                onClick = { onSelect(type) },
                selectedColor = type.accent(),
            )
        }
    }
}

@StringRes
private fun EntryType.filterLabelRes(): Int = when (this) {
    EntryType.SOLO -> R.string.history_filter_solo
    EntryType.PARTNER -> R.string.history_filter_partner
}

/**
 * Sticky month divider of the list: "2026年10月 · 5 次" (counts what is visible after filtering).
 * Full width with the screen background, so cards scrolling under it are hidden.
 */
@Composable
private fun MonthHeader(month: YearMonth, count: Int) {
    val formatter = rememberFormatter(R.string.pattern_month_year)
    SectionHeader(
        emoji = "🗓️",
        title = quantityString(R.plurals.history_month_header, count, month.format(formatter), count),
        modifier = Modifier
            .fillMaxWidth()
            .background(FunTheme.colors.background)
            .padding(start = ListGutter, end = ListGutter, top = 6.dp, bottom = 4.dp),
    )
}
