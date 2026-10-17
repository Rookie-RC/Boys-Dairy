@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rookierc.boysdairy.ui.add

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.data.Edition
import com.rookierc.boysdairy.domain.Achievement
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.Mood
import com.rookierc.boysdairy.ui.common.EditionEmoji
import com.rookierc.boysdairy.ui.common.EmojiText
import com.rookierc.boysdairy.ui.common.ChoiceCard
import com.rookierc.boysdairy.ui.common.ChunkyButton
import com.rookierc.boysdairy.ui.common.ChunkyButtonSlot
import com.rookierc.boysdairy.ui.common.ChunkyIconButton
import com.rookierc.boysdairy.ui.common.EmojiChip
import com.rookierc.boysdairy.ui.common.EmptyState
import com.rookierc.boysdairy.ui.common.FunCard
import com.rookierc.boysdairy.ui.common.FunConfirmDialog
import com.rookierc.boysdairy.ui.common.FunDialog
import com.rookierc.boysdairy.ui.common.FunTextField
import com.rookierc.boysdairy.ui.common.FunTopBar
import com.rookierc.boysdairy.ui.common.LocalEdition
import com.rookierc.boysdairy.ui.common.SectionHeader
import com.rookierc.boysdairy.ui.common.StickerBadge
import com.rookierc.boysdairy.ui.common.durationTierRes
import com.rookierc.boysdairy.ui.common.editionQuantityString
import com.rookierc.boysdairy.ui.common.editionString
import com.rookierc.boysdairy.ui.common.emoji
import com.rookierc.boysdairy.ui.common.hintRes
import com.rookierc.boysdairy.ui.common.labelRes
import com.rookierc.boysdairy.ui.common.labels
import com.rookierc.boysdairy.ui.common.pleasureTierRes
import com.rookierc.boysdairy.ui.common.quantityString
import com.rookierc.boysdairy.ui.common.rememberFormatter
import com.rookierc.boysdairy.ui.common.stringArrayResource
import com.rookierc.boysdairy.ui.common.stringResource
import com.rookierc.boysdairy.ui.theme.FunTheme
import com.rookierc.boysdairy.ui.theme.accent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Full-screen form to log a new entry or edit an existing one (which one is decided by the
 * navigation arguments read in [AddEntryViewModel.Factory]).
 *
 * Leaving with unsaved changes (close button or system back) asks for confirmation first.
 *
 * @param onClose back / close without saving
 * @param onSaved called after the entry was stored, with a random funny message for the snackbar
 *   (plus a line naming the achievements this save unlocked, if any; [celebrate] is then true).
 *   Edit mode: `undo` writes the entry back as it was before (null if the save changed nothing).
 *   It outlives this screen and its ViewModel, so the caller runs it in a scope of its own.
 * @param onDeleted edit mode: the entry was deleted from the form; close and show the message
 *   with an "undo" that runs `undo` (stores the entry again, under a new id), like [onSaved]
 * @param onEntryMissing edit mode: the entry no longer exists; close and show the given message
 */
@Composable
fun AddEntryScreen(
    onClose: () -> Unit,
    onSaved: (message: String, celebrate: Boolean, undo: EntryUndo?) -> Unit,
    onDeleted: (message: String, undo: EntryUndo) -> Unit,
    onEntryMissing: (message: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddEntryViewModel = viewModel(factory = AddEntryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editing = state.isEditing
    val missingMessage = stringResource(R.string.edit_entry_missing)
    val hasUnsavedChanges = viewModel.hasUnsavedChanges(state)
    var confirmDiscard by remember { mutableStateOf(false) }

    fun requestClose() {
        when {
            // A save / delete is in flight: leaving now could cancel it; the screen closes by itself.
            state.busy -> Unit
            hasUnsavedChanges -> confirmDiscard = true
            else -> onClose()
        }
    }

    // Without changes the back press falls through to the navigation and simply closes the form.
    BackHandler(enabled = hasUnsavedChanges || state.busy) { requestClose() }

    LaunchedEffect(state.entryMissing) {
        if (state.entryMissing) onEntryMissing(missingMessage)
    }

    Column(modifier = modifier.fillMaxSize()) {
        FunTopBar(
            title = stringResource(if (editing) R.string.edit_title else R.string.add_title),
            emoji = if (editing) "✏️" else EditionEmoji.TAPE.current,
            onBack = ::requestClose,
            useCloseIcon = true,
        )

        when {
            state.entryMissing -> Unit

            state.loading -> Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(emoji = "🗂️", message = stringResource(R.string.edit_loading))
            }

            else -> EntryForm(
                state = state,
                viewModel = viewModel,
                onSaved = onSaved,
                onDeleted = onDeleted,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (confirmDiscard) {
        FunConfirmDialog(
            title = stringResource(if (editing) R.string.edit_discard_title else R.string.add_discard_title),
            emoji = if (editing) "🙈" else "🗑️",
            message = stringResource(if (editing) R.string.edit_discard_message else R.string.add_discard_message),
            confirmText = stringResource(if (editing) R.string.edit_discard_confirm else R.string.add_discard_confirm),
            confirmColor = FunTheme.colors.danger,
            onConfirm = {
                confirmDiscard = false
                onClose()
            },
            dismissText = stringResource(R.string.add_discard_keep),
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun EntryForm(
    state: AddEntryUiState,
    viewModel: AddEntryViewModel,
    onSaved: (message: String, celebrate: Boolean, undo: EntryUndo?) -> Unit,
    onDeleted: (message: String, undo: EntryUndo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FunTheme.colors
    val context = LocalContext.current
    val edition = LocalEdition.current
    val saveToasts = stringArrayResource(
        if (state.isEditing) R.array.edit_save_toasts else R.array.save_toasts,
    )
    val deleteToasts = stringArrayResource(R.array.delete_toasts)
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
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
            onMinutesAgo = viewModel::setMinutesAgo,
        )

        FormCard(emoji = EditionEmoji.MODE_PICKER.current, title = stringResource(R.string.add_type_label)) {
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
                        EmojiText(text = type.emoji(edition), fontSize = 38.sp)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StepButton(
                    icon = Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.add_duration_minus_cd),
                    enabled = state.durationMinutes > AddEntryViewModel.MIN_DURATION,
                    onClick = { viewModel.stepDuration(-1) },
                )
                FunSlider(
                    value = state.durationMinutes.toFloat(),
                    onValueChange = { viewModel.setDuration(it.roundToInt()) },
                    valueRange = AddEntryViewModel.MIN_DURATION.toFloat()..AddEntryViewModel.MAX_DURATION.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                StepButton(
                    icon = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_duration_plus_cd),
                    enabled = state.durationMinutes < AddEntryViewModel.MAX_DURATION,
                    onClick = { viewModel.stepDuration(1) },
                )
            }
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
            val note = viewModel.note
            FunTextField(
                value = note,
                onValueChange = viewModel::onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.add_note_hint),
                minLines = 3,
                maxLines = 8,
            )
            if (note.length >= AddEntryViewModel.NOTE_COUNTER_FROM) {
                val atLimit = note.length >= AddEntryViewModel.MAX_NOTE_LENGTH
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.add_note_counter, note.length, AddEntryViewModel.MAX_NOTE_LENGTH),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (atLimit) FontWeight.ExtraBold else null,
                    color = if (atLimit) colors.textPrimary else colors.textSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (state.saveFailed || state.deleteFailed) {
            FunCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = colors.danger,
                contentColor = colors.onAccent,
            ) {
                Text(
                    text = stringResource(
                        if (state.deleteFailed) R.string.edit_delete_failed else R.string.add_save_failed,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        ChunkyButton(
            text = stringResource(
                when {
                    state.saving -> R.string.add_saving
                    state.isEditing -> R.string.edit_save
                    else -> R.string.add_save
                },
            ),
            emoji = "💾",
            onClick = {
                viewModel.save { unlocked, undo ->
                    val message = saveMessage(context.resources, edition, saveToasts.random(), unlocked)
                    onSaved(message, unlocked.isNotEmpty(), undo)
                }
            },
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
            minHeight = 58.dp,
        )

        if (state.isEditing) {
            // Secondary action: smaller than "save", in the danger colour, always confirmed first.
            ChunkyButton(
                text = stringResource(if (state.deleting) R.string.edit_deleting else R.string.edit_delete),
                emoji = "🗑️",
                onClick = { confirmDelete = true },
                enabled = state.canDelete,
                containerColor = colors.danger,
                modifier = Modifier.fillMaxWidth(),
                shadowOffset = 3.dp,
                minHeight = 48.dp,
            )
        }

        Spacer(Modifier.height(8.dp))
    }

    if (confirmDelete) {
        // Same wording as deleting from the history list.
        FunConfirmDialog(
            title = stringResource(R.string.history_delete_title),
            emoji = "🗑️",
            message = stringResource(R.string.history_delete_message),
            confirmText = stringResource(R.string.history_delete_confirm),
            confirmColor = colors.danger,
            onConfirm = {
                confirmDelete = false
                viewModel.delete { undo -> onDeleted(deleteToasts.random(), undo) }
            },
            dismissText = stringResource(R.string.history_delete_keep),
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * The save snackbar text: [toast], plus "🏆 解锁成就：夜猫子、马拉松选手" on a second line when the
 * save unlocked achievements. Built when the save completes, so it reads [resources] then, in the
 * [edition] that was showing when "save" was tapped.
 */
private fun saveMessage(
    resources: Resources,
    edition: Edition,
    toast: String,
    unlocked: List<Achievement>,
): String {
    if (unlocked.isEmpty()) return toast
    val separator = resources.editionString(R.string.achievement_unlocked_separator, edition)
    val names = unlocked.joinToString(separator = separator) {
        resources.editionString(it.labels(edition).nameRes, edition)
    }
    return toast + "\n" +
        resources.editionQuantityString(R.plurals.achievement_unlocked_toast, unlocked.size, edition, names)
}

/** The "−" / "+" buttons next to the duration slider. */
@Composable
private fun StepButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ChunkyIconButton(
        onClick = onClick,
        enabled = enabled,
        size = 48.dp,
        shadowOffset = 2.dp,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
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

/**
 * Date-time row: tap -> date picker -> time picker. Future points in time are not selectable.
 * Below it, quick-time chips set the time to "just now / 30 min / 1 h / 2 h ago" in one tap.
 */
@Composable
private fun DateTimeSection(
    dateTime: LocalDateTime,
    onDateTimeChange: (LocalDateTime) -> Unit,
    onMinutesAgo: (minutesAgo: Int) -> Unit,
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
        Spacer(Modifier.height(12.dp))
        QuickTimeChips(dateTime = dateTime, onPick = onMinutesAgo)
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

/**
 * "刚刚 / 30 分钟前 / 1 小时前 / 2 小时前". The chip whose time the form shows right now is
 * highlighted; the clock is re-read every minute, so "just now" lets go once it no longer is.
 */
@Composable
private fun QuickTimeChips(
    dateTime: LocalDateTime,
    onPick: (minutesAgo: Int) -> Unit,
) {
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(MINUTE_MILLIS - value % MINUTE_MILLIS)
            value = System.currentTimeMillis()
        }
    }
    val zone = remember { ZoneId.systemDefault() }
    val highlighted = matchingQuickTime(dateTime, nowMillis, zone)

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QUICK_TIME_OFFSETS_MINUTES.forEach { minutes ->
            EmojiChip(
                label = quickTimeLabel(minutes),
                selected = highlighted == minutes,
                onClick = { onPick(minutes) },
            )
        }
    }
}

private const val MINUTE_MILLIS = 60_000L

@Composable
private fun quickTimeLabel(minutesAgo: Int): String = when {
    minutesAgo == 0 -> stringResource(R.string.add_quick_time_now)
    minutesAgo % 60 == 0 -> quantityString(R.plurals.add_quick_time_hours_ago, minutesAgo / 60, minutesAgo / 60)
    else -> quantityString(R.plurals.add_quick_time_minutes_ago, minutesAgo, minutesAgo)
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
