package com.rookierc.boysdairy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rookierc.boysdairy.ai.AiProvider
import com.rookierc.boysdairy.ai.AiSelection
import com.rookierc.boysdairy.ai.ApiKeyStore
import com.rookierc.boysdairy.ai.aiSelectionFlow
import com.rookierc.boysdairy.appContainer
import com.rookierc.boysdairy.data.EntryRepository
import com.rookierc.boysdairy.data.SettingsRepository
import com.rookierc.boysdairy.data.ThemeMode
import com.rookierc.boysdairy.security.LockManager
import com.rookierc.boysdairy.security.PinCheckResult
import com.rookierc.boysdairy.security.PinHasher
import com.rookierc.boysdairy.ui.lock.LockoutCountdown
import com.rookierc.boysdairy.ui.lock.PinMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which dialog of the "delete all entries" flow is showing. */
enum class ClearAllStep { HIDDEN, CONFIRM, PIN }

/**
 * State of the "delete all entries" flow. The PIN itself is never part of it: it lives only in
 * the dialog's composition state and is passed straight to [SettingsViewModel.confirmClearAll].
 */
data class ClearAllUiState(
    val step: ClearAllStep = ClearAllStep.HIDDEN,
    val working: Boolean = false,
    val message: PinMessage? = null,
    val lockoutSeconds: Int = 0,
    /** Deleting (or checking the PIN) threw; the dialog stays open so the user can retry. */
    val failed: Boolean = false,
    /** Bumped whenever the typed PIN should be cleared (wrong PIN, lockout). */
    val pinResetToken: Int = 0,
    /** One-shot: the entries were just deleted; the screen shows a snackbar and consumes it. */
    val justCleared: Boolean = false,
) {
    val inputEnabled: Boolean get() = !working && lockoutSeconds == 0
}

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val entryRepository: EntryRepository,
    private val lockManager: LockManager,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ThemeMode.SYSTEM,
    )

    /**
     * The selected AI provider and its key status; `null` until the first read. The key itself
     * never reaches the UI layer.
     */
    val ai: StateFlow<AiSelection?> = aiSelectionFlow(settings, apiKeyStore).stateIn<AiSelection?>(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun selectProvider(provider: AiProvider) {
        viewModelScope.launch { settings.setAiProvider(provider) }
    }

    fun saveApiKey(provider: AiProvider, key: String) {
        viewModelScope.launch { apiKeyStore.save(provider, key.trim()) }
    }

    fun clearApiKey(provider: AiProvider) {
        viewModelScope.launch { apiKeyStore.clear(provider) }
    }

    // ---- Danger zone: delete all entries ----------------------------------------------------

    /** Whether there is anything to delete; `null` until the first database read. */
    val hasEntries: StateFlow<Boolean?> = entryRepository.entries
        .map { it.isNotEmpty() }
        .distinctUntilChanged()
        .stateIn<Boolean?>(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val _clearAll = MutableStateFlow(ClearAllUiState())
    val clearAll: StateFlow<ClearAllUiState> = _clearAll.asStateFlow()

    private val lockoutCountdown = LockoutCountdown(
        scope = viewModelScope,
        remainingMillis = { lockManager.lockoutRemainingMillis() },
        onTick = { seconds -> _clearAll.update { it.copy(lockoutSeconds = seconds) } },
    )

    /** Step 1: the "this cannot be undone" confirmation. Ignored when there is nothing to delete. */
    fun startClearAll() {
        if (hasEntries.value != true || _clearAll.value.step != ClearAllStep.HIDDEN) return
        _clearAll.update { ClearAllUiState(step = ClearAllStep.CONFIRM, pinResetToken = it.pinResetToken + 1) }
    }

    /** Step 2: ask for the current PIN. A lockout started elsewhere (lock screen) still applies. */
    fun proceedToPin() {
        if (_clearAll.value.step != ClearAllStep.CONFIRM) return
        _clearAll.update { it.copy(step = ClearAllStep.PIN, message = null, failed = false) }
        lockoutCountdown.start()
    }

    fun cancelClearAll() {
        if (_clearAll.value.working) return
        lockoutCountdown.stop()
        _clearAll.update { ClearAllUiState(pinResetToken = it.pinResetToken + 1, justCleared = it.justCleared) }
    }

    /**
     * Step 3: checks [pin] with [LockManager.verifyPin] (counts toward the lockout) and deletes every
     * entry on success. The PIN is only used for this check and is not kept.
     */
    fun confirmClearAll(pin: String) {
        val current = _clearAll.value
        if (current.step != ClearAllStep.PIN || !current.inputEnabled || pin.length != PinHasher.PIN_LENGTH) return
        _clearAll.update { it.copy(working = true, message = null, failed = false) }

        viewModelScope.launch {
            try {
                when (val result = lockManager.verifyPin(pin)) {
                    PinCheckResult.Success -> {
                        entryRepository.clearAll()
                        lockoutCountdown.stop()
                        _clearAll.update { ClearAllUiState(pinResetToken = it.pinResetToken + 1, justCleared = true) }
                    }

                    is PinCheckResult.Wrong -> _clearAll.update {
                        it.copy(
                            working = false,
                            message = PinMessage.WrongPin(result.attemptsLeft),
                            pinResetToken = it.pinResetToken + 1,
                        )
                    }

                    is PinCheckResult.LockedOut -> {
                        _clearAll.update {
                            it.copy(working = false, message = null, pinResetToken = it.pinResetToken + 1)
                        }
                        lockoutCountdown.start()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing is logged (no diary content, no PIN); the dialog just offers a retry.
                _clearAll.update { it.copy(working = false, failed = true) }
            }
        }
    }

    fun onClearedNoticeShown() {
        _clearAll.update { it.copy(justCleared = false) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                SettingsViewModel(
                    settings = container.settingsRepository,
                    apiKeyStore = container.apiKeyStore,
                    entryRepository = container.entryRepository,
                    lockManager = container.lockManager,
                )
            }
        }
    }
}
