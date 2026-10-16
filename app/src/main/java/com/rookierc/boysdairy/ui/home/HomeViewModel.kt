package com.rookierc.boysdairy.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rookierc.boysdairy.appContainer
import com.rookierc.boysdairy.data.EntryRepository
import com.rookierc.boysdairy.domain.Entry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth

/**
 * Raw data only: everything that depends on "today" (stats, cooldown, calendar highlight) is
 * derived in the UI from [entries] and the date, which is refreshed on resume and at midnight.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    /** All entries, newest first. */
    val entries: List<Entry> = emptyList(),
    val visibleMonth: YearMonth = YearMonth.now(),
)

class HomeViewModel(repository: EntryRepository) : ViewModel() {

    private val visibleMonth = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<HomeUiState> = combine(repository.entries, visibleMonth) { entries, month ->
        HomeUiState(isLoading = false, entries = entries, visibleMonth = month)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(visibleMonth = visibleMonth.value),
    )

    fun showPreviousMonth() = visibleMonth.update { it.minusMonths(1) }

    fun showNextMonth() = visibleMonth.update { it.plusMonths(1) }

    fun showMonth(month: YearMonth) {
        visibleMonth.value = month
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(appContainer().entryRepository) }
        }
    }
}
