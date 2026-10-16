@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rookierc.boysdairy.ui.add

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.Mood
import com.rookierc.boysdairy.ui.common.AppCard
import com.rookierc.boysdairy.ui.common.SectionLabel
import com.rookierc.boysdairy.ui.common.labelRes
import com.rookierc.boysdairy.ui.common.rememberFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

@Composable
fun AddEntryScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddEntryViewModel = viewModel(factory = AddEntryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DateTimeField(
                dateTime = state.dateTime,
                onDateTimeChange = viewModel::setDateTime,
            )

            FormSection(label = stringResource(R.string.add_type_label)) {
                val types = EntryType.entries
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    types.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = state.type == type,
                            onClick = { viewModel.setType(type) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                        ) {
                            Text(stringResource(type.labelRes()))
                        }
                    }
                }
            }

            FormSection(
                label = stringResource(R.string.add_duration_label),
                value = stringResource(R.string.add_duration_value, state.durationMinutes),
            ) {
                Slider(
                    value = state.durationMinutes.toFloat(),
                    onValueChange = { viewModel.setDuration(it.roundToInt()) },
                    valueRange = AddEntryViewModel.MIN_DURATION.toFloat()..AddEntryViewModel.MAX_DURATION.toFloat(),
                )
            }

            FormSection(label = stringResource(R.string.add_mood_before_label)) {
                MoodChips(
                    options = Mood.beforeOptions,
                    selected = state.moodBefore,
                    onSelect = viewModel::setMoodBefore,
                )
            }

            FormSection(label = stringResource(R.string.add_mood_after_label)) {
                MoodChips(
                    options = Mood.afterOptions,
                    selected = state.moodAfter,
                    onSelect = viewModel::setMoodAfter,
                )
            }

            FormSection(
                label = stringResource(R.string.add_pleasure_label),
                value = stringResource(R.string.add_pleasure_value, state.pleasure),
            ) {
                Slider(
                    value = state.pleasure.toFloat(),
                    onValueChange = { viewModel.setPleasure(it.roundToInt()) },
                    valueRange = AddEntryViewModel.MIN_PLEASURE.toFloat()..AddEntryViewModel.MAX_PLEASURE.toFloat(),
                    steps = AddEntryViewModel.MAX_PLEASURE - AddEntryViewModel.MIN_PLEASURE - 1,
                )
            }

            OutlinedTextField(
                value = viewModel.note,
                onValueChange = viewModel::onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.add_note_label)) },
                minLines = 4,
                maxLines = 8,
            )

            Button(
                onClick = { viewModel.save(onClose) },
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.add_save))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FormSection(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(text = label)
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        content()
    }
}

@Composable
private fun MoodChips(
    options: List<Mood>,
    selected: Mood,
    onSelect: (Mood) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { mood ->
            FilterChip(
                selected = selected == mood,
                onClick = { onSelect(mood) },
                label = { Text(stringResource(mood.labelRes())) },
            )
        }
    }
}

/** Date-time row: tap -> date picker -> time picker. Future points in time are not selectable. */
@Composable
private fun DateTimeField(
    dateTime: LocalDateTime,
    onDateTimeChange: (LocalDateTime) -> Unit,
) {
    val formatter = rememberFormatter(R.string.pattern_date_time)
    var showDatePicker by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    FormSection(label = stringResource(R.string.add_datetime_label)) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = dateTime.format(formatter),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }

    if (showDatePicker) {
        val todayUtcMillis = remember {
            LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = remember(todayUtcMillis) { PastOrTodayDates(todayUtcMillis) },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = dateState.selectedDateMillis != null,
                    onClick = {
                        dateState.selectedDateMillis?.let { millis ->
                            pickedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.action_next))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    pickedDate?.let { date ->
        TimePickerDialog(
            date = date,
            initialTime = dateTime.toLocalTime(),
            onDismiss = { pickedDate = null },
            onConfirm = { picked ->
                onDateTimeChange(picked)
                pickedDate = null
            },
        )
    }
}

@Composable
private fun TimePickerDialog(
    date: LocalDate,
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit,
) {
    val context = LocalContext.current
    val timeState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    val candidate = LocalDateTime.of(date, LocalTime.of(timeState.hour, timeState.minute))
    val isInFuture = candidate.isAfter(LocalDateTime.now())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_pick_time_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = timeState)
                if (isInFuture) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.add_time_future_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !isInFuture, onClick = { onConfirm(candidate) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Only today and earlier can be picked. Dates arrive as UTC midnight millis. */
private class PastOrTodayDates(private val todayUtcMillis: Long) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtcMillis

    override fun isSelectableYear(year: Int): Boolean =
        year <= Instant.ofEpochMilli(todayUtcMillis).atZone(ZoneOffset.UTC).year
}
