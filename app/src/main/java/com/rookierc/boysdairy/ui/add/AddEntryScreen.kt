@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rookierc.boysdairy.ui.add

import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.Mood
import com.rookierc.boysdairy.ui.common.EmojiText
import com.rookierc.boysdairy.ui.common.ChoiceCard
import com.rookierc.boysdairy.ui.common.ChunkyButton
import com.rookierc.boysdairy.ui.common.ChunkyButtonSlot
import com.rookierc.boysdairy.ui.common.EmojiChip
import com.rookierc.boysdairy.ui.common.FunCard
import com.rookierc.boysdairy.ui.common.FunDialog
import com.rookierc.boysdairy.ui.common.FunTextField
import com.rookierc.boysdairy.ui.common.FunTopBar
import com.rookierc.boysdairy.ui.common.SectionHeader
import com.rookierc.boysdairy.ui.common.StickerBadge
import com.rookierc.boysdairy.ui.common.durationTierRes
import com.rookierc.boysdairy.ui.common.emoji
import com.rookierc.boysdairy.ui.common.hintRes
import com.rookierc.boysdairy.ui.common.labelRes
import com.rookierc.boysdairy.ui.common.pleasureTierRes
import com.rookierc.boysdairy.ui.common.rememberFormatter
import com.rookierc.boysdairy.ui.theme.FunTheme
import com.rookierc.boysdairy.ui.theme.accent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * Full-screen "log a new entry" form.
 *
 * @param onClose back / close without saving
 * @param onSaved called after the entry was stored, with a random funny message for the snackbar
 */
@Composable
fun AddEntryScreen(
    onClose: () -> Unit,
    onSaved: (message: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddEntryViewModel = viewModel(factory = AddEntryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saveToasts = stringArrayResource(R.array.save_toasts)
    val colors = FunTheme.colors

    Column(modifier = modifier.fillMaxSize()) {
        FunTopBar(
            title = stringResource(R.string.add_title),
            emoji = "📼",
            onBack = onClose,
            useCloseIcon = true,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DateTimeSection(
                dateTime = state.dateTime,
                onDateTimeChange = viewModel::setDateTime,
            )

            FormCard(emoji = "🎮", title = stringResource(R.string.add_type_label)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EntryType.entries.forEach { type ->
                        ChoiceCard(
                            selected = state.type == type,
                            onClick = { viewModel.setType(type) },
                            selectedColor = type.accent(),
                            modifier = Modifier.weight(1f),
                        ) {
                            EmojiText(text = type.emoji(), fontSize = 38.sp)
                            Text(
                                text = stringResource(type.labelRes()),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(type.hintRes()),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            FormCard(
                emoji = "⏱️",
                title = stringResource(R.string.add_duration_label),
                value = stringResource(R.string.add_duration_value, state.durationMinutes),
            ) {
                FunSlider(
                    value = state.durationMinutes.toFloat(),
                    onValueChange = { viewModel.setDuration(it.roundToInt()) },
                    valueRange = AddEntryViewModel.MIN_DURATION.toFloat()..AddEntryViewModel.MAX_DURATION.toFloat(),
                )
                ReactiveLabel(labelRes = durationTierRes(state.durationMinutes))
            }

            FormCard(emoji = "😰", title = stringResource(R.string.add_mood_before_label)) {
                MoodChips(
                    options = Mood.beforeOptions,
                    selected = state.moodBefore,
                    onSelect = viewModel::setMoodBefore,
                )
            }

            FormCard(emoji = "😌", title = stringResource(R.string.add_mood_after_label)) {
                MoodChips(
                    options = Mood.afterOptions,
                    selected = state.moodAfter,
                    onSelect = viewModel::setMoodAfter,
                )
            }

            FormCard(
                emoji = "🔥",
                title = stringResource(R.string.add_pleasure_label),
                value = stringResource(R.string.add_pleasure_value, state.pleasure),
            ) {
                FunSlider(
                    value = state.pleasure.toFloat(),
                    onValueChange = { viewModel.setPleasure(it.roundToInt()) },
                    valueRange = AddEntryViewModel.MIN_PLEASURE.toFloat()..AddEntryViewModel.MAX_PLEASURE.toFloat(),
                    steps = AddEntryViewModel.MAX_PLEASURE - AddEntryViewModel.MIN_PLEASURE - 1,
                )
                ReactiveLabel(labelRes = pleasureTierRes(state.pleasure))
            }

            FormCard(emoji = "📝", title = stringResource(R.string.add_note_label)) {
                FunTextField(
                    value = viewModel.note,
                    onValueChange = viewModel::onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(R.string.add_note_hint),
                    minLines = 3,
                    maxLines = 8,
                )
            }

            if (state.saveFailed) {
                FunCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = colors.danger,
                    contentColor = colors.onAccent,
                ) {
                    Text(
                        text = stringResource(R.string.add_save_failed),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }

            ChunkyButton(
                text = stringResource(if (state.saving) R.string.add_saving else R.string.add_save),
                emoji = "💾",
                onClick = { viewModel.save { onSaved(saveToasts.random()) } },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 58.dp,
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Sticker-card section of the form: emoji + title, optional value badge, then the controls. */
@Composable
private fun FormCard(
    emoji: String,
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FunCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            emoji = emoji,
            title = title,
            trailing = value?.let { text -> { StickerBadge(text = text) } },
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun FunSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    val colors = FunTheme.colors
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = colors.ink,
            activeTrackColor = colors.pink,
            // surfaceAlt is nearly invisible on the cream/white cards; an ink tint stays clearly
            // visible (>= 3:1) in both light and dark themes.
            inactiveTrackColor = colors.ink.copy(alpha = 0.5f),
            activeTickColor = colors.onAccent.copy(alpha = 0.55f),
            inactiveTickColor = colors.ink.copy(alpha = 0.35f),
        ),
    )
}

/** Label under a slider that changes with the value ("闪电侠 ⚡", "爽到 😆", ...). */
@Composable
private fun ReactiveLabel(labelRes: Int) {
    Crossfade(
        targetState = labelRes,
        modifier = Modifier.fillMaxWidth(),
        label = "reactiveLabel",
    ) { res ->
        Text(
            text = stringResource(res),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
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
            EmojiChip(
                label = stringResource(mood.labelRes()),
                emoji = mood.emoji(),
                selected = selected == mood,
                onClick = { onSelect(mood) },
            )
        }
    }
}

/** Date-time row: tap -> date picker -> time picker. Future points in time are not selectable. */
@Composable
private fun DateTimeSection(
    dateTime: LocalDateTime,
    onDateTimeChange: (LocalDateTime) -> Unit,
) {
    val formatter = rememberFormatter(R.string.pattern_date_time)
    var showDatePicker by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    FormCard(emoji = "🕒", title = stringResource(R.string.add_datetime_label)) {
        ChunkyButtonSlot(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth(),
            containerColor = FunTheme.colors.lemon,
        ) {
            EmojiText(text = "🗓️", fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = dateTime.format(formatter),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
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

    FunDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.add_pick_time_title),
        emoji = "⏰",
        buttons = {
            ChunkyButton(
                text = stringResource(R.string.action_confirm),
                onClick = { onConfirm(candidate) },
                enabled = !isInFuture,
                modifier = Modifier.fillMaxWidth(),
            )
            ChunkyButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                containerColor = FunTheme.colors.surface,
                contentColor = FunTheme.colors.textPrimary,
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TimePicker(state = timeState)
            if (isInFuture) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.add_time_future_error),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Only today and earlier can be picked. Dates arrive as UTC midnight millis. */
private class PastOrTodayDates(private val todayUtcMillis: Long) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtcMillis

    override fun isSelectableYear(year: Int): Boolean =
        year <= Instant.ofEpochMilli(todayUtcMillis).atZone(ZoneOffset.UTC).year
}
