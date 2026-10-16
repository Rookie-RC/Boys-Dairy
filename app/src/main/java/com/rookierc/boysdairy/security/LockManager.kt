package com.rookierc.boysdairy.security

import com.rookierc.boysdairy.data.PinCredentials
import com.rookierc.boysdairy.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface LockState {
    /** The stored PIN has not been read yet (first few milliseconds after process start). */
    data object Loading : LockState

    /** No PIN exists yet; the user must create one. */
    data object NeedsSetup : LockState

    data object Locked : LockState

    data object Unlocked : LockState
}

sealed interface PinCheckResult {
    data object Success : PinCheckResult
    data class Wrong(val attemptsLeft: Int) : PinCheckResult
    data class LockedOut(val remainingMillis: Long) : PinCheckResult
}

/**
 * App-wide lock state holder. A single instance lives in the DI container so that the
 * process lifecycle observer and the UI share the same state.
 */
class LockManager(
    private val settings: SettingsRepository,
    scope: CoroutineScope,
    private val limiter: PinAttemptLimiter,
) {
    private val _state = MutableStateFlow<LockState>(LockState.Loading)
    val state: StateFlow<LockState> = _state.asStateFlow()

    private val mutex = Mutex()

    init {
        scope.launch {
            val hasPin = settings.loadPinCredentials() != null
            _state.update { current ->
                when {
                    current != LockState.Loading -> current
                    hasPin -> LockState.Locked
                    else -> LockState.NeedsSetup
                }
            }
        }
    }

    /** Stores the first PIN and unlocks. */
    suspend fun setupPin(pin: String) {
        storePin(pin)
        _state.value = LockState.Unlocked
    }

    /** Replaces the stored PIN; the app stays unlocked. */
    suspend fun changePin(newPin: String) {
        storePin(newPin)
    }

    /** Checks [pin] against the stored hash and unlocks on success. */
    suspend fun unlock(pin: String): PinCheckResult {
        val result = verifyPin(pin)
        if (result is PinCheckResult.Success) {
            _state.value = LockState.Unlocked
        }
        return result
    }

    /** Checks [pin] without changing the lock state. Counts toward the lockout limit. */
    suspend fun verifyPin(pin: String): PinCheckResult = mutex.withLock {
        val lockout = limiter.remainingLockoutMillis()
        if (lockout > 0L) {
            return@withLock PinCheckResult.LockedOut(lockout)
        }
        val credentials = settings.loadPinCredentials()
        val matches = credentials != null && runCatching {
            PinHasher.verify(PinHasher.decodeSalt(credentials.saltBase64), pin, credentials.hashHex)
        }.getOrDefault(false)

        if (matches) {
            limiter.recordSuccess()
            PinCheckResult.Success
        } else {
            limiter.recordFailure()
            val remaining = limiter.remainingLockoutMillis()
            if (remaining > 0L) PinCheckResult.LockedOut(remaining) else PinCheckResult.Wrong(limiter.attemptsLeft)
        }
    }

    fun lockoutRemainingMillis(): Long = limiter.remainingLockoutMillis()

    /** Locks the app if it is currently unlocked (which implies a PIN exists). */
    fun lock() {
        _state.update { current -> if (current == LockState.Unlocked) LockState.Locked else current }
    }

    private suspend fun storePin(pin: String) {
        val salt = PinHasher.generateSalt()
        settings.savePinCredentials(
            PinCredentials(
                saltBase64 = PinHasher.encodeSalt(salt),
                hashHex = PinHasher.hash(salt, pin),
            ),
        )
    }
}
