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
import com.rookierc.boysdairy.data.GapExclusionStore
import com.rookierc.boysdairy.domain.Achievement
import com.rookierc.boysdairy.domain.AchievementProgress
import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.GapExclusion
import com.rookierc.boysdairy.domain.Mood
import com.rookierc.boysdairy.domain.computeAchievements
import com.rookierc.boysdairy.domain.newlyUnlocked
import com.rookierc.boysdairy.domain.recentKeywords
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    /** Edit mode: the entry is being deleted; like [saving], the screen then closes by itself. */
    val deleting: Boolean = false,
    /** Edit mode: true after the last delete attempt threw; the entry and the form are unchanged. */
    val deleteFailed: Boolean = false,
    /**
     * Keywords of earlier solo entries, offered as one-tap chips under the keyword field: most
     * recently used first, at most [AddEntryViewModel.KEYWORD_SUGGESTIONS] (see [recentKeywords]).
     */
    val recentKeywords: List<String> = emptyList(),
) {
    /** A save or delete is in flight: leaving now could cancel it. */
    val busy: Boolean get() = saving || deleting

    val canSave: Boolean get() = !loading && !entryMissing && !busy

    /** Edit mode only: the loaded entry can be deleted from the form. */
    val canDelete: Boolean get() = isEditing && canSave

    fun formValues(note: String, keyword: String) = EntryFormValues(
        dateTime = dateTime,
        type = type,
        durationMinutes = durationMinutes,
        moodBefore = moodBefore,
        moodAfter = moodAfter,
        pleasure = pleasure,
        note = note,
        keyword = keyword,
    )

    companion object {
        const val DEFAULT_DURATION_MINUTES = 10
        const val DEFAULT_PLEASURE = 5
    }
}

/**
 * Form for logging a new entry or editing an existing one.
 *
 * @param gapExclusionStore gaps marked "does not count", read (never written) for the unlock line
 *   of the save message
 * @param entryId edit this stored entry; `null` logs a new one
 * @param presetDate new entries only: back-fill this day (current time of day, never in the future)
 *
 * Only the navigation arguments come from the [androidx.lifecycle.SavedStateHandle]; the form
 * content (note, moods, ...) is never written to it, because saved state is handed to and kept
 * by the system (e.g. across process death), outside the app's own storage.
 */
class AddEntryViewModel(
    private val repository: EntryRepository,
    private val gapExclusionStore: GapExclusionStore,
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
     * Text of the solo keyword field, snapshot state like [note]. Kept while the type is switched
     * to partner (the field is hidden then), so switching back to solo brings it back; only
     * [keywordToStore] decides what is saved.
     */
    var keyword: String by mutableStateOf("")
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
            viewModelScope.launch {
                if (loadForEditing(entryId)) loadKeywordSuggestions(diaryOrNull())
            }
        } else {
            baseline = _uiState.value.formValues(note, keyword)
            viewModelScope.launch {
                val diary = diaryOrNull()
                applyLatestEntryDefaults(diary?.firstOrNull())
                loadKeywordSuggestions(diary)
            }
        }
    }

    /** Every entry, newest first, or `null` if it cannot be read (only used for conveniences). */
    private suspend fun diaryOrNull(): List<Entry>? = try {
        repository.snapshot()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        null
    }

    /** Fills [AddEntryUiState.recentKeywords] from [diary]; nothing to offer if it could not be read. */
    private suspend fun loadKeywordSuggestions(diary: List<Entry>?) {
        if (diary.isNullOrEmpty()) return
        val suggestions = withContext(Dispatchers.Default) { recentKeywords(diary, KEYWORD_SUGGESTIONS) }
        _uiState.update { it.copy(recentKeywords = suggestions) }
    }

    /** Edit mode: reads the entry into the form. False if it is gone (the screen then closes). */
    private suspend fun loadForEditing(id: Long): Boolean {
        val entry = try {
            repository.get(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            null // Unreadable: handled like a vanished entry (the screen closes with a message).
        }
        if (entry == null) {
            _uiState.update { it.copy(loading = false, entryMissing = true) }
            return false
        }
        original = entry
        note = entry.note
        keyword = entry.keyword.orEmpty()
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
        baseline = _uiState.value.formValues(note, keyword)
        return true
    }

    /**
     * New entries start with the type and duration of the [latest] entry, unless already picked.
     * `null` (empty or unreadable diary): the built-in defaults stay.
     */
    private fun applyLatestEntryDefaults(latest: Entry?) {
        if (latest == null) return
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
        return state.formValues(note, keyword).differsFrom(start)
    }

    fun onNoteChange(value: String) {
        note = acceptNoteEdit(current = note, proposed = value, maxLength = MAX_NOTE_LENGTH)
    }

    fun onKeywordChange(value: String) {
        keyword = acceptKeywordEdit(current = keyword, proposed = value, maxLength = MAX_KEYWORD_LENGTH)
    }

    /** A "recently used" chip: the keyword becomes exactly that one (even if older and longer than the limit). */
    fun pickKeyword(value: String) {
        keyword = value
    }

    fun setDateTime(value: LocalDateTime) {
        // Future times are not allowed; clamp defensively.
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        _uiState.update { it.copy(dateTime = if (value.isAfter(now)) now else value) }
    }

    /** A quick-time chip: the time becomes [minutesAgo] minutes before now (see [quickTimeDateTime]). */
    fun setMinutesAgo(minutesAgo: Int) {
        val dateTime = quickTimeDateTime(System.currentTimeMillis(), minutesAgo.coerceAtLeast(0), zone)
        _uiState.update { it.copy(dateTime = dateTime) }
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

    /** The repository alone, for undo lambdas that must not keep this (soon cleared) ViewModel alive. */
    private fun undoWith(block: suspend EntryRepository.() -> Unit): EntryUndo {
        val repo = repository
        return { repo.block() }
    }

    /**
     * Stores the entry (new: insert, edit: update in place) and calls [onSaved] on the main thread
     * once it is persisted. If storing throws, the form is kept, [AddEntryUiState.saving] is reset
     * and [AddEntryUiState.saveFailed] is raised so the user can retry.
     *
     * [onSaved] receives the badges this save unlocked (see [newlyUnlocked]), honours and shame
     * badges alike, in display order; empty when there are none or when the diary could not be read
     * before / after. Both computations use the gap exclusions as read once before saving (an
     * unreadable set counts as empty), so only the entry itself can make a difference. In edit
     * mode it also receives an undo that writes the entry back exactly as it was loaded (see
     * [revertOfEdit]); `null` for new entries and for saves that changed nothing.
     */
    fun save(onSaved: (unlocked: List<Achievement>, undo: EntryUndo?) -> Unit) {
        val snapshot = _uiState.value
        if (!snapshot.canSave) return
        val editing = original
        if (snapshot.isEditing && editing == null) return
        _uiState.update { it.copy(saving = true, saveFailed = false, deleteFailed = false) }

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
            keyword = keywordToStore(keyword, snapshot.type, editing?.keyword),
        )
        val undo = editing?.let { revertOfEdit(original = it, saved = entry) }
            ?.let { revert -> undoWith { update(revert) } }

        viewModelScope.launch {
            val exclusions = gapExclusionsOrEmpty()
            val before = achievementsOrNull(nowMillis, exclusions)
            try {
                if (editing != null) repository.update(entry) else repository.add(entry)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(saving = false, saveFailed = true) }
                return@launch
            }
            val after = if (before != null) achievementsOrNull(nowMillis, exclusions) else null
            val unlocked = if (before != null && after != null) newlyUnlocked(before, after) else emptyList()
            // `saving` intentionally stays true: the screen is being closed and a second tap
            // on "save" during the exit transition must not create a duplicate.
            onSaved(unlocked, undo)
        }
    }

    /**
     * Edit mode: deletes the entry and calls [onDeleted] on the main thread once it is gone, with
     * an undo that stores the entry again as it was loaded, under a fresh id (like undoing a delete
     * in the history list). Unsaved changes in the form are dropped. If deleting throws, the form
     * stays open and [AddEntryUiState.deleteFailed] is raised.
     */
    fun delete(onDeleted: (undo: EntryUndo) -> Unit) {
        val snapshot = _uiState.value
        val stored = original
        if (!snapshot.canDelete || stored == null) return
        _uiState.update { it.copy(deleting = true, deleteFailed = false, saveFailed = false) }

        viewModelScope.launch {
            try {
                repository.delete(stored.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(deleting = false, deleteFailed = true) }
                return@launch
            }
            // `deleting` stays true for the same reason `saving` does after a save.
            onDeleted(undoWith { add(stored.copy(id = 0)) })
        }
    }

    /**
     * Achievement progress of the whole diary right now, without the gaps in [exclusions], or
     * `null` if the diary cannot be read.
     */
    private suspend fun achievementsOrNull(
        nowMillis: Long,
        exclusions: Set<GapExclusion>,
    ): List<AchievementProgress>? = try {
        val entries = repository.snapshot()
        withContext(Dispatchers.Default) { computeAchievements(entries, nowMillis, zone, exclusions) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        null // Only a celebration: never let it get in the way of saving.
    }

    /** The gaps marked "does not count"; empty if they cannot be read (like [achievementsOrNull]). */
    private suspend fun gapExclusionsOrEmpty(): Set<GapExclusion> = try {
        gapExclusionStore.exclusions.first()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        emptySet()
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

        /** The solo keyword cannot grow past this; longer ones from backups are kept as is. */
        const val MAX_KEYWORD_LENGTH = 30

        /** The `length/max` counter under the keyword appears from this length on. */
        const val KEYWORD_COUNTER_FROM = 24

        /** At most this many "recently used" keyword chips. */
        const val KEYWORD_SUGGESTIONS = 5

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
                val container = appContainer()
                AddEntryViewModel(container.entryRepository, container.gapExclusionStore, entryId, presetDate)
            }
        }
    }
}
