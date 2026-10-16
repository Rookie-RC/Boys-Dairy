package com.rookierc.boysdairy.ui.add

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rookierc.boysdairy.appContainer
import com.rookierc.boysdairy.data.EntryRepository
import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.Mood
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class AddEntryUiState(
    val dateTime: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES),
    val type: EntryType = EntryType.SOLO,
    val durationMinutes: Int = DEFAULT_DURATION_MINUTES,
    val moodBefore: Mood = Mood.NEUTRAL,
    val moodAfter: Mood = Mood.RELAXED,
    val pleasure: Int = DEFAULT_PLEASURE,
    val saving: Boolean = false,
) {
    companion object {
        const val DEFAULT_DURATION_MINUTES = 10
        const val DEFAULT_PLEASURE = 5
    }
}

class AddEntryViewModel(private val repository: EntryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEntryUiState())
    val uiState: StateFlow<AddEntryUiState> = _uiState.asStateFlow()

    /**
     * The note is Compose snapshot state (not part of [uiState]) so the text field is updated
     * synchronously, which keeps IME composition (e.g. Chinese input) stable.
     */
    var note: String by mutableStateOf("")
        private set

    fun onNoteChange(value: String) {
        note = value
    }

    fun setDateTime(value: LocalDateTime) {
        // Future times are not allowed; clamp defensively.
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        _uiState.update { it.copy(dateTime = if (value.isAfter(now)) now else value) }
    }

    fun setType(value: EntryType) = _uiState.update { it.copy(type = value) }

    fun setDuration(minutes: Int) =
        _uiState.update { it.copy(durationMinutes = minutes.coerceIn(MIN_DURATION, MAX_DURATION)) }

    fun setMoodBefore(value: Mood) = _uiState.update { it.copy(moodBefore = value) }

    fun setMoodAfter(value: Mood) = _uiState.update { it.copy(moodAfter = value) }

    fun setPleasure(value: Int) =
        _uiState.update { it.copy(pleasure = value.coerceIn(MIN_PLEASURE, MAX_PLEASURE)) }

    /** Stores the entry and calls [onSaved] on the main thread once it is persisted. */
    fun save(onSaved: () -> Unit) {
        val snapshot = _uiState.value
        if (snapshot.saving) return
        _uiState.update { it.copy(saving = true) }

        val now = System.currentTimeMillis()
        val timestamp = snapshot.dateTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
            .coerceAtMost(now)

        viewModelScope.launch {
            repository.add(
                Entry(
                    timestamp = timestamp,
                    type = snapshot.type,
                    durationMinutes = snapshot.durationMinutes,
                    moodBefore = snapshot.moodBefore,
                    moodAfter = snapshot.moodAfter,
                    pleasure = snapshot.pleasure,
                    note = note.trim(),
                ),
            )
            onSaved()
        }
    }

    companion object {
        const val MIN_DURATION = 1
        const val MAX_DURATION = 120
        const val MIN_PLEASURE = 1
        const val MAX_PLEASURE = 10

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AddEntryViewModel(appContainer().entryRepository) }
        }
    }
}
