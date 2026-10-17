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
import com.rookierc.boysdairy.domain.TodayAdvice
import com.rookierc.boysdairy.domain.backupReminderSnoozeEnd
import com.rookierc.boysdairy.domain.todayAdvice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Mostly raw data: what depends on "today" (stats, cooldown, calendar highlight) is derived in the
 * UI from [entries] and the date, which is refreshed on resume and at midnight. The exception is
 * [advice], computed here and recomputed whenever the screen reports a new date.
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
    /** "Today's almanac" card, also shown without entries. In memory only: never stored or sent anywhere. */
    val advice: TodayAdvice,
)

class HomeViewModel(
    repository: EntryRepository,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    private val visibleMonth = MutableStateFlow(YearMonth.now())

    /** Last date reported by [onDayChanged]; a new value only triggers a fresh [HomeUiState.advice]. */
    private val day = MutableStateFlow<LocalDate?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.entries,
        visibleMonth,
        settings.lastExportAt,
        settings.backupReminderSnoozedUntil,
        day,
    ) { entries, month, lastExportAt, snoozedUntil, _ ->
        HomeUiState(
            isLoading = false,
            entries = entries,
            visibleMonth = month,
            lastExportAt = lastExportAt,
            backupReminderSnoozedUntil = snoozedUntil,
            advice = todayAdvice(entries, clock(), zone()),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(
            visibleMonth = visibleMonth.value,
            advice = todayAdvice(emptyList(), clock(), zone()),
        ),
    )

    fun showPreviousMonth() = visibleMonth.update { it.minusMonths(1) }

    fun showNextMonth() = visibleMonth.update { it.plusMonths(1) }

    fun showMonth(month: YearMonth) {
        visibleMonth.value = month
    }

    /** The screen's "today" changed (resume, midnight): today's almanac is worked out afresh. */
    fun onDayChanged(today: LocalDate) {
        day.value = today
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
