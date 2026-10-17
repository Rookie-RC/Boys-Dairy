package com.rookierc.boysdairy.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rookierc.boysdairy.appContainer
import com.rookierc.boysdairy.data.EntryRepository
import com.rookierc.boysdairy.data.SettingsRepository
import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.domain.backupReminderSnoozeEnd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    /** Last successful export (epoch millis); `null` = never. */
    val lastExportAt: Long? = null,
    /** The backup reminder is snoozed until then (epoch millis); `null` = never snoozed. */
    val backupReminderSnoozedUntil: Long? = null,
)

class HomeViewModel(
    repository: EntryRepository,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val visibleMonth = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<HomeUiState> = combine(
        repository.entries,
        visibleMonth,
        settings.lastExportAt,
        settings.backupReminderSnoozedUntil,
    ) { entries, month, lastExportAt, snoozedUntil ->
        HomeUiState(
            isLoading = false,
            entries = entries,
            visibleMonth = month,
            lastExportAt = lastExportAt,
            backupReminderSnoozedUntil = snoozedUntil,
        )
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

    /** "Remind me later" on the backup reminder card. */
    fun snoozeBackupReminder() {
        viewModelScope.launch { settings.setBackupReminderSnoozedUntil(backupReminderSnoozeEnd(clock())) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HomeViewModel(container.entryRepository, container.settingsRepository)
            }
        }
    }
}
