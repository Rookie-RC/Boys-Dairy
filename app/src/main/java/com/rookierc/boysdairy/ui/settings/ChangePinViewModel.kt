package com.rookierc.boysdairy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rookierc.boysdairy.appContainer
import com.rookierc.boysdairy.security.LockManager
import com.rookierc.boysdairy.security.PinCheckResult
import com.rookierc.boysdairy.security.PinHasher
import com.rookierc.boysdairy.ui.lock.LockoutCountdown
import com.rookierc.boysdairy.ui.lock.PinMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Change-PIN flow: current PIN -> new PIN -> confirm new PIN. */
class ChangePinViewModel(private val lockManager: LockManager) : ViewModel() {

    enum class Step { CURRENT, NEW, CONFIRM }

    data class UiState(
        val step: Step = Step.CURRENT,
        val enteredCount: Int = 0,
        val message: PinMessage? = null,
        val lockoutSeconds: Int = 0,
        val finished: Boolean = false,
        /** Bumped on every wrong PIN / mismatch so the UI can play the shake animation. */
        val shake: Int = 0,
    ) {
        val inputEnabled: Boolean get() = lockoutSeconds == 0 && !finished
    }

    private val buffer = StringBuilder()
    private var newPin: String? = null
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
        // A lockout started on the lock screen still applies here.
        countdown.start()
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
            Step.CURRENT -> {
                busy = true
                viewModelScope.launch {
                    try {
                        when (val result = lockManager.verifyPin(pin)) {
                            PinCheckResult.Success ->
                                _uiState.update { it.copy(step = Step.NEW, enteredCount = 0, message = null) }
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

            Step.NEW -> {
                newPin = pin
                _uiState.update { it.copy(step = Step.CONFIRM, enteredCount = 0, message = null) }
            }

            Step.CONFIRM -> {
                val first = newPin
                if (first != null && first == pin) {
                    busy = true
                    viewModelScope.launch {
                        try {
                            lockManager.changePin(pin)
                            newPin = null
                            _uiState.update { it.copy(enteredCount = 0, message = null, finished = true) }
                        } finally {
                            busy = false
                        }
                    }
                } else {
                    newPin = null
                    _uiState.update {
                        it.copy(
                            step = Step.NEW,
                            enteredCount = 0,
                            message = PinMessage.Mismatch,
                            shake = it.shake + 1,
                        )
                    }
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ChangePinViewModel(appContainer().lockManager) }
        }
    }
}
