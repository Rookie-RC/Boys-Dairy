package com.rookierc.boysdairy.ui.add

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rookierc.boysdairy.appContainer
import com.rookierc.boysdairy.data.EntryRepository
import com.rookierc.boysdairy.domain.Achievement
import com.rookierc.boysdairy.domain.AchievementProgress
import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.Mood
import com.rookierc.boysdairy.domain.computeAchievements
import com.rookierc.boysdairy.domain.newlyUnlocked
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class AddEntryUiState(
    /** True when an existing entry is edited instead of a new one being logged. */
    val isEditing: Boolean = false,
    /** Edit mode: true until the entry has been read. The form is not shown and cannot be saved. */
    val loading: Boolean = false,
    /** Edit mode: the entry no longer exists (deleted meanwhile); the screen closes itself. */
    val entryMissing: Boolean = false,
    val dateTime: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES),
    val type: EntryType = EntryType.SOLO,
    val durationMinutes: Int = DEFAULT_DURATION_MINUTES,
    val moodBefore: Mood = Mood.NEUTRAL,
    val moodAfter: Mood = Mood.RELAXED,
    val pleasure: Int = DEFAULT_PLEASURE,
    val saving: Boolean = false,
    /** True after the last save attempt threw; the form stays editable and the button is enabled. */
    val saveFailed: Boolean = false,
) {
    val canSave: Boolean get() = !loading && !entryMissing && !saving

    fun formValues(note: String) = EntryFormValues(
        dateTime = dateTime,
        type = type,
        durationMinutes = durationMinutes,
        moodBefore = moodBefore,
        moodAfter = moodAfter,
        pleasure = pleasure,
        note = note,
    )

    companion object {
        const val DEFAULT_DURATION_MINUTES = 10
        const val DEFAULT_PLEASURE = 5
    }
}

/**
 * Form for logging a new entry or editing an existing one.
 *
 * @param entryId edit this stored entry; `null` logs a new one
 * @param presetDate new entries only: back-fill this day (current time of day, never in the future)
 *
 * Only the navigation arguments come from the [androidx.lifecycle.SavedStateHandle]; the form
 * content (note, moods, ...) is never written to it, because saved state is handed to and kept
 * by the system (e.g. across process death), outside the app's own storage.
 */
class AddEntryViewModel(
    private val repository: EntryRepository,
    entryId: Long? = null,
    presetDate: LocalDate? = null,
) : ViewModel() {

    /** Same zone for reading and writing the timestamp, so an untouched time maps back exactly. */
    private val zone: ZoneId = ZoneId.systemDefault()

    private val _uiState = MutableStateFlow(
        if (entryId != null) {
            AddEntryUiState(isEditing = true, loading = true)
        } else {
            val now = LocalDateTime.now()
            AddEntryUiState(
                dateTime = presetDate?.let { backfillDateTime(it, now) }
                    ?: now.truncatedTo(ChronoUnit.MINUTES),
            )
        },
    )
    val uiState: StateFlow<AddEntryUiState> = _uiState.asStateFlow()

    /**
     * The note is Compose snapshot state (not part of [uiState]) so the text field is updated
     * synchronously, which keeps IME composition (e.g. Chinese input) stable.
     */
    var note: String by mutableStateOf("")
        private set

    /**
     * What the form started with (edit mode: the stored entry), to detect unsaved changes.
     * Snapshot state so composables reading [hasUnsavedChanges] recompose when it is set.
     * `null` while the entry is loading.
     */
    private var baseline: EntryFormValues? by mutableStateOf(null)

    /** Edit mode: the entry as it was loaded. */
    private var original: Entry? = null

    // New entries: whether the user already picked these before the "latest entry" defaults arrived.
    private var typeTouched = false
    private var durationTouched = false

    init {
        if (entryId != null) {
            viewModelScope.launch { loadForEditing(entryId) }
        } else {
            baseline = _uiState.value.formValues(note)
            viewModelScope.launch { applyLatestEntryDefaults() }
        }
    }

    private suspend fun loadForEditing(id: Long) {
        val entry = try {
            repository.get(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            null // Unreadable: handled like a vanished entry (the screen closes with a message).
        }
        if (entry == null) {
            _uiState.update { it.copy(loading = false, entryMissing = true) }
            return
        }
        original = entry
        note = entry.note
        _uiState.update {
            it.copy(
                loading = false,
                dateTime = entry.timestamp.toFormDateTime(zone),
                type = entry.type,
                // Kept as stored even outside the slider range (old imports): never changed silently.
                durationMinutes = entry.durationMinutes,
                moodBefore = entry.moodBefore,
                moodAfter = entry.moodAfter,
                pleasure = entry.pleasure,
            )
        }
        baseline = _uiState.value.formValues(note)
    }

    /** New entries start with the type and duration of the most recent entry, unless already picked. */
    private suspend fun applyLatestEntryDefaults() {
        val latest = try {
            repository.snapshot().firstOrNull()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            null // Only a convenience: keep the built-in defaults.
        } ?: return

        val type = latest.type
        val duration = latest.durationMinutes.coerceIn(MIN_DURATION, MAX_DURATION)
        _uiState.update {
            it.copy(
                type = if (typeTouched) it.type else type,
                durationMinutes = if (durationTouched) it.durationMinutes else duration,
            )
        }
        baseline = baseline?.let {
            it.copy(
                type = if (typeTouched) it.type else type,
                durationMinutes = if (durationTouched) it.durationMinutes else duration,
            )
        }
    }

    /** True if the form differs from what it started with. Reads snapshot state (observable). */
    fun hasUnsavedChanges(state: AddEntryUiState): Boolean {
        val start = baseline ?: return false
        return state.formValues(note).differsFrom(start)
    }

    fun onNoteChange(value: String) {
        note = acceptNoteEdit(current = note, proposed = value, maxLength = MAX_NOTE_LENGTH)
    }

    fun setDateTime(value: LocalDateTime) {
        // Future times are not allowed; clamp defensively.
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        _uiState.update { it.copy(dateTime = if (value.isAfter(now)) now else value) }
    }

    fun setType(value: EntryType) {
        typeTouched = true
        _uiState.update { it.copy(type = value) }
    }

    fun setDuration(minutes: Int) {
        durationTouched = true
        _uiState.update { it.copy(durationMinutes = minutes.coerceIn(MIN_DURATION, MAX_DURATION)) }
    }

    /** The "−" / "+" buttons: one minute at a time, within the slider range. */
    fun stepDuration(deltaMinutes: Int) {
        setDuration(_uiState.value.durationMinutes + deltaMinutes)
    }

    fun setMoodBefore(value: Mood) = _uiState.update { it.copy(moodBefore = value) }

    fun setMoodAfter(value: Mood) = _uiState.update { it.copy(moodAfter = value) }

    fun setPleasure(value: Int) =
        _uiState.update { it.copy(pleasure = value.coerceIn(MIN_PLEASURE, MAX_PLEASURE)) }

    /** Hides the "could not save" message. */
    fun dismissSaveError() = _uiState.update { it.copy(saveFailed = false) }

    /**
     * Stores the entry (new: insert, edit: update in place) and calls [onSaved] on the main thread
     * once it is persisted. If storing throws, the form is kept, [AddEntryUiState.saving] is reset
     * and [AddEntryUiState.saveFailed] is raised so the user can retry.
     *
     * [onSaved] receives the achievements this save unlocked (see [newlyUnlocked]), in display
     * order; empty when there are none or when the diary could not be read before / after.
     */
    fun save(onSaved: (unlocked: List<Achievement>) -> Unit) {
        val snapshot = _uiState.value
        if (!snapshot.canSave) return
        val editing = original
        if (snapshot.isEditing && editing == null) return
        _uiState.update { it.copy(saving = true, saveFailed = false) }

        // One "now" for the timestamp and both achievement computations, so time-based badges
        // (gaps) cannot flip just because the clock moved between the two reads.
        val nowMillis = System.currentTimeMillis()
        val entry = Entry(
            id = editing?.id ?: 0,
            timestamp = resolveTimestamp(
                chosen = snapshot.dateTime,
                original = editing,
                zone = zone,
                nowMillis = nowMillis,
            ),
            type = snapshot.type,
            durationMinutes = snapshot.durationMinutes,
            moodBefore = snapshot.moodBefore,
            moodAfter = snapshot.moodAfter,
            pleasure = snapshot.pleasure,
            note = noteToStore(note, editing?.note),
        )

        viewModelScope.launch {
            val before = achievementsOrNull(nowMillis)
            try {
                if (editing != null) repository.update(entry) else repository.add(entry)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(saving = false, saveFailed = true) }
                return@launch
            }
            val after = if (before != null) achievementsOrNull(nowMillis) else null
            val unlocked = if (before != null && after != null) newlyUnlocked(before, after) else emptyList()
            // `saving` intentionally stays true: the screen is being closed and a second tap
            // on "save" during the exit transition must not create a duplicate.
            onSaved(unlocked)
        }
    }

    /** Achievement progress of the whole diary right now, or `null` if it cannot be read. */
    private suspend fun achievementsOrNull(nowMillis: Long): List<AchievementProgress>? = try {
        val entries = repository.snapshot()
        withContext(Dispatchers.Default) { computeAchievements(entries, nowMillis, zone) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        null // Only a celebration: never let it get in the way of saving.
    }

    companion object {
        const val MIN_DURATION = 1
        const val MAX_DURATION = 120
        const val MIN_PLEASURE = 1
        const val MAX_PLEASURE = 10

        /** Notes cannot grow past this; longer notes from older versions / backups are kept as is. */
        const val MAX_NOTE_LENGTH = 1000

        /** The `length/max` counter under the note appears from this length on. */
        const val NOTE_COUNTER_FROM = 900

        /** Navigation argument: id of the entry to edit, [NO_ENTRY_ID] for a new entry. */
        const val ARG_ENTRY_ID = "entryId"
        const val NO_ENTRY_ID = -1L

        /** Navigation argument: epoch day a new entry is back-filled for, [NO_EPOCH_DAY] for none. */
        const val ARG_EPOCH_DAY = "epochDay"

        /** Not -1: that is a valid epoch day (31 Dec 1969). */
        const val NO_EPOCH_DAY = Long.MIN_VALUE

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // Read-only use of the saved state: just the navigation arguments.
                val args = createSavedStateHandle()
                val entryId = args.get<Long>(ARG_ENTRY_ID)?.takeIf { it > 0 }
                val presetDate = args.get<Long>(ARG_EPOCH_DAY)
                    ?.takeIf { it != NO_EPOCH_DAY }
                    ?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() }
                AddEntryViewModel(appContainer().entryRepository, entryId, presetDate)
            }
        }
    }
}
