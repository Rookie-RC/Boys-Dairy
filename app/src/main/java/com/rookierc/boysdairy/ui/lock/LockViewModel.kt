package com.rookierc.boysdairy.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rookierc.boysdairy.appContainer
import com.rookierc.boysdairy.security.LockManager
import com.rookierc.boysdairy.security.LockState
import com.rookierc.boysdairy.security.PinCheckResult
import com.rookierc.boysdairy.security.PinHasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the full-screen lock UI: first-run PIN setup (enter + confirm) and normal unlock.
 * The entered digits live only in this class and are never exposed; the UI only sees a count.
 */
class LockViewModel(private val lockManager: LockManager) : ViewModel() {

    enum class Step { SETUP_ENTER, SETUP_CONFIRM, UNLOCK }

    data class UiState(
        val step: Step = Step.UNLOCK,
        val enteredCount: Int = 0,
        val message: PinMessage? = null,
        val lockoutSeconds: Int = 0,
    ) {
        val inputEnabled: Boolean get() = lockoutSeconds == 0
    }

    private val buffer = StringBuilder()
    private var firstEntry: String? = null
    private var busy = false

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

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
                        _uiState.value = UiState(step = Step.SETUP_ENTER)
                    }
                    LockState.Locked -> {
                        _uiState.value = UiState(step = Step.UNLOCK)
                        countdown.start()
                    }
                    LockState.Unlocked, LockState.Loading -> {
                        countdown.stop()
                        _uiState.value = UiState(step = Step.UNLOCK)
                    }
                }
            }
        }
    }

    fun onDigit(digit: Char) {
        if (busy || !_uiState.value.inputEnabled || buffer.length >= PinHasher.PIN_LENGTH) return
        buffer.append(digit)
        _uiState.update { it.copy(enteredCount = buffer.length, message = null) }
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
                    _uiState.value = UiState(step = Step.SETUP_ENTER, message = PinMessage.Mismatch)
                }
            }

            Step.UNLOCK -> {
                busy = true
                viewModelScope.launch {
                    try {
                        when (val result = lockManager.unlock(pin)) {
                            PinCheckResult.Success -> Unit // lock state flow resets the UI
                            is PinCheckResult.Wrong -> _uiState.update {
                                it.copy(enteredCount = 0, message = PinMessage.WrongPin(result.attemptsLeft))
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LockViewModel(appContainer().lockManager) }
        }
    }
}
