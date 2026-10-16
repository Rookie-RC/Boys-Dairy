package com.rookierc.boysdairy.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rookierc.boysdairy.appContainer
import com.rookierc.boysdairy.data.EntryRepository
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.buildDayMarkers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class HomeUiState(
    val isLoading: Boolean = true,
    /** Epoch millis of the most recent entry, or null if there are none. */
    val lastEntryTimestamp: Long? = null,
    /** Marker per calendar day that has at least one entry. */
    val dayMarkers: Map<LocalDate, EntryType> = emptyMap(),
    val visibleMonth: YearMonth = YearMonth.now(),
)

class HomeViewModel(repository: EntryRepository) : ViewModel() {

    private val visibleMonth = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<HomeUiState> = combine(repository.entries, visibleMonth) { entries, month ->
        HomeUiState(
            isLoading = false,
            lastEntryTimestamp = entries.maxOfOrNull { it.timestamp },
            dayMarkers = buildDayMarkers(entries, ZoneId.systemDefault()),
            visibleMonth = month,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(visibleMonth = visibleMonth.value),
    )

    fun showPreviousMonth() = visibleMonth.update { it.minusMonths(1) }

    fun showNextMonth() = visibleMonth.update { it.plusMonths(1) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(appContainer().entryRepository) }
        }
    }
}
