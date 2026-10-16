@file:OptIn(ExperimentalMaterial3Api::class)

package com.rookierc.boysdairy.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.ui.common.EmptyState
import com.rookierc.boysdairy.ui.common.EntryCard
import com.rookierc.boysdairy.ui.common.SectionHeader
import com.rookierc.boysdairy.ui.common.quantityString
import com.rookierc.boysdairy.ui.common.rememberFormatter
import com.rookierc.boysdairy.ui.theme.FunShapes
import com.rookierc.boysdairy.ui.theme.FunTheme
import java.time.LocalDate

/** Bottom sheet that lists everything logged on one calendar day. */
@Composable
fun DayEntriesSheet(
    date: LocalDate,
    entries: List<Entry>,
    onDismiss: () -> Unit,
) {
    val colors = FunTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormatter = rememberFormatter(R.string.pattern_date_weekday)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = FunShapes.sheet,
        containerColor = colors.background,
        contentColor = colors.textPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(48.dp)
                    .height(6.dp)
                    .clip(FunShapes.chip)
                    .background(colors.ink),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(
                emoji = "📼",
                title = stringResource(R.string.day_sheet_title, date.format(dateFormatter)),
            )
            if (entries.isEmpty()) {
                EmptyState(emoji = "🕳️", message = stringResource(R.string.day_sheet_empty))
            } else {
                Text(
                    text = quantityString(R.plurals.day_sheet_count, entries.size, entries.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
                entries.forEach { entry ->
                    EntryCard(entry = entry, showDate = false)
                }
            }
        }
    }
}
