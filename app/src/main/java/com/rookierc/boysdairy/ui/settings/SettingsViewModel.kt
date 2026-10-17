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
import com.rookierc.boysdairy.data.Edition
import com.rookierc.boysdairy.data.EntryRepository
import com.rookierc.boysdairy.data.SettingsRepository
import com.rookierc.boysdairy.data.ThemeMode
import com.rookierc.boysdairy.security.BiometricCapability
import com.rookierc.boysdairy.security.BiometricSettingRow
import com.rookierc.boysdairy.security.BiometricUnlock
import com.rookierc.boysdairy.security.LockManager
import com.rookierc.boysdairy.security.PinCheckResult
import com.rookierc.boysdairy.security.PinHasher
import com.rookierc.boysdairy.security.biometricSettingRow
import com.rookierc.boysdairy.ui.common.BiometricOutcome
import com.rookierc.boysdairy.ui.common.BiometricPromptRequest
import com.rookierc.boysdairy.ui.lock.LockoutCountdown
import com.rookierc.boysdairy.ui.lock.PinMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

/** The last successful export as read from settings; [atMillis] `null` = never exported. */
data class LastExport(val atMillis: Long?)

/** The "Fingerprint unlock" row. [busy] = turning it on (new key, confirmation prompt open). */
data class BiometricSettingUiState(
    val row: BiometricSettingRow = BiometricSettingRow.HIDDEN,
    val busy: Boolean = false,
) {
    val checked: Boolean get() = row == BiometricSettingRow.ON
    val toggleEnabled: Boolean get() = !busy && (row == BiometricSettingRow.ON || row == BiometricSettingRow.OFF)
}

/** One-shot snackbar after trying to turn fingerprint unlock on. */
enum class BiometricSettingMessage { ENABLED, ENABLE_FAILED }

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val entryRepository: EntryRepository,
    private val lockManager: LockManager,
    private val biometricUnlock: BiometricUnlock,
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

    // ---- Boy / girl edition: picking the other one asks first --------------------------------

    private val editionSwitch = EditionSwitch { edition ->
        // The whole unlocked UI switches as soon as it is stored.
        viewModelScope.launch { settings.setEdition(edition) }
    }

    /** The edition waiting in the "are you sure" dialog; `null` = no dialog. */
    val pendingEdition: StateFlow<Edition?> = editionSwitch.pending

    /** A tap on an edition chip: only opens the confirmation, nothing is stored yet. */
    fun requestEdition(target: Edition, current: Edition) = editionSwitch.request(target, current)

    fun confirmEditionSwitch() = editionSwitch.confirm()

    fun cancelEditionSwitch() = editionSwitch.cancel()

    fun selectProvider(provider: AiProvider) {
        viewModelScope.launch { settings.setAiProvider(provider) }
    }

    fun saveApiKey(provider: AiProvider, key: String) {
        viewModelScope.launch { apiKeyStore.save(provider, key.trim()) }
    }

    fun clearApiKey(provider: AiProvider) {
        viewModelScope.launch { apiKeyStore.clear(provider) }
    }

    /**
     * "Send my notes too" for the AI roast (off by default); `null` until the first read, so the
     * switch does not flash. The AI tab reads the same setting when the roast button is tapped.
     */
    val aiIncludeNotes: StateFlow<Boolean?> = settings.aiIncludeNotes.stateIn<Boolean?>(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    fun setAiIncludeNotes(include: Boolean) {
        viewModelScope.launch { settings.setAiIncludeNotes(include) }
    }

    /** Shown under "Export backup"; `null` until the first read so "never" does not flash. */
    val lastExport: StateFlow<LastExport?> = settings.lastExportAt
        .map { LastExport(it) }
        .stateIn<LastExport?>(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** "Lock now": back to the PIN screen right away; the session (and this ViewModel) is torn down. */
    fun lockNow() {
        lockManager.lock()
    }

    // ---- Fingerprint unlock -------------------------------------------------------------------

    /** `null` until read; the row stays hidden meanwhile. */
    private val biometricCapability = MutableStateFlow<BiometricCapability?>(null)
    private val biometricBusy = MutableStateFlow(false)

    val biometric: StateFlow<BiometricSettingUiState> = combine(
        biometricUnlock.enabled,
        biometricCapability,
        biometricBusy,
    ) { enabled, capability, busy ->
        BiometricSettingUiState(
            row = capability?.let { biometricSettingRow(it, enabled) } ?: BiometricSettingRow.HIDDEN,
            busy = busy,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = BiometricSettingUiState(),
    )

    private val _biometricPrompt = MutableStateFlow<BiometricPromptRequest?>(null)

    /** The confirmation prompt to show while turning the feature on. */
    val biometricPrompt: StateFlow<BiometricPromptRequest?> = _biometricPrompt.asStateFlow()

    private val _biometricMessage = MutableStateFlow<BiometricSettingMessage?>(null)
    val biometricMessage: StateFlow<BiometricSettingMessage?> = _biometricMessage.asStateFlow()

    /** Re-reads what the device offers (fingerprints may have been enrolled meanwhile). */
    fun refreshBiometric() {
        viewModelScope.launch { biometricCapability.value = biometricUnlock.capability() }
    }

    /**
     * On: a fresh key, then one fingerprint check; the setting is only stored once that check went
     * through ([onBiometricPromptOutcome]). Off: immediately, and the key is deleted.
     */
    fun setBiometricUnlock(on: Boolean) {
        if (!on) {
            viewModelScope.launch { quietly { biometricUnlock.disable() } }
            return
        }
        if (biometricBusy.value || biometric.value.row != BiometricSettingRow.OFF) return
        biometricBusy.value = true
        viewModelScope.launch {
            val cipher = quietly { biometricUnlock.prepareEnable() }
            if (cipher == null) {
                biometricBusy.value = false
                _biometricMessage.value = BiometricSettingMessage.ENABLE_FAILED
            } else {
                _biometricPrompt.value = BiometricPromptRequest(cipher)
            }
        }
    }

    fun onBiometricPromptHandled(request: BiometricPromptRequest) {
        _biometricPrompt.compareAndSet(request, null)
    }

    fun onBiometricPromptOutcome(outcome: BiometricOutcome) {
        viewModelScope.launch {
            when (outcome) {
                is BiometricOutcome.Succeeded -> {
                    val enabled = quietly { biometricUnlock.completeEnable(outcome.cipher) } == true
                    _biometricMessage.value =
                        if (enabled) BiometricSettingMessage.ENABLED else BiometricSettingMessage.ENABLE_FAILED
                }
                BiometricOutcome.Cancelled -> quietly { biometricUnlock.abortEnable() }
                BiometricOutcome.LockedOut, BiometricOutcome.Failed -> {
                    quietly { biometricUnlock.abortEnable() }
                    _biometricMessage.value = BiometricSettingMessage.ENABLE_FAILED
                }
            }
            biometricBusy.value = false
        }
    }

    fun onBiometricMessageShown() {
        _biometricMessage.value = null
    }

    /** Runs [block]; storage / Keystore trouble yields `null` (nothing is logged). */
    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
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
                    biometricUnlock = container.biometricUnlock,
                )
            }
        }
    }
}
