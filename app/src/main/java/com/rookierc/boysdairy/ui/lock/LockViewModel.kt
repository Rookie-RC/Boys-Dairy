package com.rookierc.boysdairy.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rookierc.boysdairy.appContainer
import com.rookierc.boysdairy.security.BiometricUnlock
import com.rookierc.boysdairy.security.BiometricUnlockPreparation
import com.rookierc.boysdairy.security.LockManager
import com.rookierc.boysdairy.security.LockState
import com.rookierc.boysdairy.security.PinCheckResult
import com.rookierc.boysdairy.security.PinHasher
import com.rookierc.boysdairy.ui.common.BiometricOutcome
import com.rookierc.boysdairy.ui.common.BiometricPromptRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Line under the PIN dots about the fingerprint (shown when there is no PIN message). */
enum class BiometricNotice {
    /** The enrolled fingerprints changed, so fingerprint unlock was switched off. */
    KEY_CHANGED,

    /** The system blocks fingerprints for a while (too many tries). */
    LOCKED_OUT,

    /** Fingerprint did not work this time (hardware / Keystore trouble). */
    UNAVAILABLE,
}

/**
 * Drives the full-screen lock UI: first-run PIN setup (enter + confirm) and normal unlock.
 * The entered digits live only in this class and are never exposed; the UI only sees a count.
 *
 * Optional fingerprint unlock (only in [Step.UNLOCK]): the prompt opens by itself once each time
 * the lock screen shows up ([BiometricAutoPrompt]) and again from the 👆 key. A fingerprint only
 * unlocks after the Keystore accepted the authenticated cipher ([BiometricUnlock.verify]).
 */
class LockViewModel(
    private val lockManager: LockManager,
    private val biometric: BiometricUnlock,
) : ViewModel() {

    enum class Step { SETUP_ENTER, SETUP_CONFIRM, UNLOCK }

    data class UiState(
        val step: Step = Step.UNLOCK,
        val enteredCount: Int = 0,
        val message: PinMessage? = null,
        val lockoutSeconds: Int = 0,
        /** Bumped on every wrong PIN / mismatch so the UI can play the shake animation. */
        val shake: Int = 0,
        /** Show the 👆 key (fingerprint unlock is on and usable). Also during a PIN lockout. */
        val biometricKey: Boolean = false,
        val biometricNotice: BiometricNotice? = null,
    ) {
        val inputEnabled: Boolean get() = lockoutSeconds == 0
    }

    private val buffer = StringBuilder()
    private var firstEntry: String? = null
    private var busy = false

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val autoPrompt = BiometricAutoPrompt()

    private val _promptRequest = MutableStateFlow<BiometricPromptRequest?>(null)

    /** The fingerprint prompt the UI should show now; it calls [onPromptRequestHandled] once taken. */
    val promptRequest: StateFlow<BiometricPromptRequest?> = _promptRequest.asStateFlow()

    private val countdown = LockoutCountdown(
        scope = viewModelScope,
        remainingMillis = { lockManager.lockoutRemainingMillis() },
        onTick = { seconds ->
            _uiState.update { current ->
                current.copy(
                    lockoutSeconds = seconds,
                    enteredCount = if (seconds > 0) 0 else current.enteredCount,
                )
            }
        },
    )

    init {
        viewModelScope.launch {
            lockManager.state.collect { lockState ->
                buffer.clear()
                firstEntry = null
                when (lockState) {
                    LockState.NeedsSetup -> {
                        countdown.stop()
                        autoPrompt.onNotLocked()
                        _promptRequest.value = null
                        _uiState.value = UiState(step = Step.SETUP_ENTER)
                    }
                    LockState.Locked -> {
                        // Keep the 👆 key while its state is re-read, so it does not flicker.
                        _uiState.value = UiState(step = Step.UNLOCK, biometricKey = _uiState.value.biometricKey)
                        countdown.start()
                        refreshBiometric(prompt = autoPrompt.onLocked())
                    }
                    LockState.Unlocked, LockState.Loading -> {
                        countdown.stop()
                        autoPrompt.onNotLocked()
                        _promptRequest.value = null
                        _uiState.value = UiState(step = Step.UNLOCK)
                    }
                }
            }
        }
    }

    fun onDigit(digit: Char) {
        if (busy || !_uiState.value.inputEnabled || buffer.length >= PinHasher.PIN_LENGTH) return
        buffer.append(digit)
        _uiState.update {
            it.copy(
                enteredCount = buffer.length,
                message = null,
                // "Fingerprints changed" stays until unlocked; the other notes go once the user types.
                biometricNotice = it.biometricNotice.takeIf { notice -> notice == BiometricNotice.KEY_CHANGED },
            )
        }
        if (buffer.length == PinHasher.PIN_LENGTH) {
            submit()
        }
    }

    fun onBackspace() {
        if (busy || !_uiState.value.inputEnabled || buffer.isEmpty()) return
        buffer.setLength(buffer.length - 1)
        _uiState.update { it.copy(enteredCount = buffer.length) }
    }

    private fun submit() {
        val pin = buffer.toString()
        buffer.clear()

        when (_uiState.value.step) {
            Step.SETUP_ENTER -> {
                firstEntry = pin
                _uiState.value = UiState(step = Step.SETUP_CONFIRM)
            }

            Step.SETUP_CONFIRM -> {
                val first = firstEntry
                if (first != null && first == pin) {
                    busy = true
                    viewModelScope.launch {
                        try {
                            lockManager.setupPin(pin)
                        } finally {
                            busy = false
                        }
                    }
                } else {
                    firstEntry = null
                    _uiState.value = UiState(
                        step = Step.SETUP_ENTER,
                        message = PinMessage.Mismatch,
                        shake = _uiState.value.shake + 1,
                    )
                }
            }

            Step.UNLOCK -> {
                busy = true
                viewModelScope.launch {
                    try {
                        when (val result = lockManager.unlock(pin)) {
                            PinCheckResult.Success -> Unit // lock state flow resets the UI
                            is PinCheckResult.Wrong -> _uiState.update {
                                it.copy(
                                    enteredCount = 0,
                                    message = PinMessage.WrongPin(result.attemptsLeft),
                                    shake = it.shake + 1,
                                )
                            }
                            is PinCheckResult.LockedOut -> {
                                _uiState.update { it.copy(enteredCount = 0, message = null) }
                                countdown.start()
                            }
                        }
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }

    // ---- Fingerprint unlock ------------------------------------------------------------------

    /** The lock screen became visible (lifecycle STARTED); a new appearance opens the prompt once. */
    fun onScreenStarted() {
        val prompt = autoPrompt.onScreenStarted()
        if (lockManager.state.value == LockState.Locked) {
            refreshBiometric(prompt)
        } else if (prompt) {
            autoPrompt.onPromptClosed()
        }
    }

    /** The lock screen went away; [changingConfigurations] = it is only being recreated. */
    fun onScreenStopped(changingConfigurations: Boolean) {
        autoPrompt.onScreenStopped(changingConfigurations)
    }

    /** The 👆 key. Works during a PIN lockout too. */
    fun onBiometricKey() {
        if (lockManager.state.value != LockState.Locked || !autoPrompt.tryManual()) return
        viewModelScope.launch {
            val preparation = prepare()
            if (preparation is BiometricUnlockPreparation.Ready && lockManager.state.value == LockState.Locked) {
                _promptRequest.value = BiometricPromptRequest(preparation.cipher)
                return@launch
            }
            autoPrompt.onPromptClosed()
            when (preparation) {
                is BiometricUnlockPreparation.Ready -> Unit // unlocked meanwhile
                BiometricUnlockPreparation.NotOffered -> _uiState.update { it.copy(biometricKey = false) }
                BiometricUnlockPreparation.KeyChanged -> _uiState.update {
                    it.copy(biometricKey = false, biometricNotice = BiometricNotice.KEY_CHANGED)
                }
                BiometricUnlockPreparation.Failed -> _uiState.update {
                    it.copy(biometricNotice = BiometricNotice.UNAVAILABLE)
                }
            }
        }
    }

    fun onPromptRequestHandled(request: BiometricPromptRequest) {
        _promptRequest.compareAndSet(request, null)
    }

    /** Result of the prompt shown for the last request ([BiometricOutcome.Failed] if it could not be shown). */
    fun onPromptOutcome(outcome: BiometricOutcome) {
        when (outcome) {
            is BiometricOutcome.Succeeded -> viewModelScope.launch {
                val proven = biometric.verify(outcome.cipher)
                autoPrompt.onPromptClosed()
                if (proven) {
                    lockManager.unlockWithBiometric() // the lock state flow resets the UI
                } else {
                    _uiState.update { it.copy(biometricNotice = BiometricNotice.UNAVAILABLE) }
                }
            }
            BiometricOutcome.Cancelled -> autoPrompt.onPromptClosed()
            BiometricOutcome.LockedOut -> {
                autoPrompt.onPromptClosed()
                _uiState.update { it.copy(biometricNotice = BiometricNotice.LOCKED_OUT) }
            }
            BiometricOutcome.Failed -> {
                autoPrompt.onPromptClosed()
                _uiState.update { it.copy(biometricNotice = BiometricNotice.UNAVAILABLE) }
            }
        }
    }

    /**
     * Re-reads whether the 👆 key is offered (setting, device, key still valid) and, when [prompt]
     * (an attempt taken from [autoPrompt]), opens the prompt right away.
     */
    private fun refreshBiometric(prompt: Boolean) {
        viewModelScope.launch {
            val preparation = prepare()
            if (lockManager.state.value != LockState.Locked) {
                if (prompt) autoPrompt.onPromptClosed()
                return@launch
            }
            if (prompt && preparation !is BiometricUnlockPreparation.Ready) autoPrompt.onPromptClosed()
            when (preparation) {
                is BiometricUnlockPreparation.Ready -> {
                    _uiState.update { it.copy(biometricKey = true) }
                    if (prompt) _promptRequest.value = BiometricPromptRequest(preparation.cipher)
                }
                BiometricUnlockPreparation.NotOffered -> _uiState.update { it.copy(biometricKey = false) }
                BiometricUnlockPreparation.KeyChanged -> _uiState.update {
                    it.copy(biometricKey = false, biometricNotice = BiometricNotice.KEY_CHANGED)
                }
                // On and usable, but the Keystore hiccupped: keep the key so a tap can retry.
                BiometricUnlockPreparation.Failed -> _uiState.update { it.copy(biometricKey = true) }
            }
        }
    }

    private suspend fun prepare(): BiometricUnlockPreparation = try {
        biometric.prepareUnlock()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Unexpected trouble (e.g. reading the setting): no fingerprint this time, the PIN still works.
        BiometricUnlockPreparation.NotOffered
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                LockViewModel(container.lockManager, container.biometricUnlock)
            }
        }
    }
}
