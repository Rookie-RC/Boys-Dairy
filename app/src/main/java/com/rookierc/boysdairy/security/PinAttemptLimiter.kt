package com.rookierc.boysdairy.security

/**
 * Counts consecutive wrong PINs and enforces a temporary lockout after [maxAttempts] of them.
 * State is in-memory only. [now] must be monotonic milliseconds (e.g. elapsedRealtime).
 * Not thread-safe; callers serialize access.
 */
class PinAttemptLimiter(
    private val maxAttempts: Int = MAX_ATTEMPTS,
    private val lockoutMillis: Long = LOCKOUT_MILLIS,
    private val now: () -> Long,
) {
    private var failures = 0
    private var lockedUntil: Long? = null

    /** Wrong attempts still allowed before the next lockout starts. */
    val attemptsLeft: Int
        get() = (maxAttempts - failures).coerceAtLeast(0)

    /** Milliseconds until PIN entry is allowed again; 0 when not locked out. */
    fun remainingLockoutMillis(): Long {
        val until = lockedUntil ?: return 0L
        val remaining = until - now()
        if (remaining <= 0L) {
            lockedUntil = null
            return 0L
        }
        return remaining
    }

    fun recordFailure() {
        failures++
        if (failures >= maxAttempts) {
            failures = 0
            lockedUntil = now() + lockoutMillis
        }
    }

    fun recordSuccess() {
        failures = 0
        lockedUntil = null
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 30_000L
    }
}
