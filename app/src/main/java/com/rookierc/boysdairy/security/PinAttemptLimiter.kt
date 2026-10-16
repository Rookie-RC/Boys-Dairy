package com.rookierc.boysdairy.security

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the limiter persists. A `null` record means "nothing to remember": no pending wrong
 * attempts and no active lockout.
 *
 * @property failures wrong PINs entered since the last success / lockout.
 * @property lockedUntilMillis wall-clock deadline of the active lockout, 0 when none.
 * @property updatedAtMillis wall-clock time of the last write; a value in the future means the
 *   system clock was moved back since then.
 */
data class PinLockoutState(
    val failures: Int,
    val lockedUntilMillis: Long,
    val updatedAtMillis: Long,
)

/** Durable storage for [PinLockoutState] so the lockout survives process death and reboots. */
interface PinLockoutStore {
    suspend fun read(): PinLockoutState?

    /** Persists [state]; `null` clears the record. */
    suspend fun write(state: PinLockoutState?)
}

/**
 * Counts consecutive wrong PINs and enforces a temporary lockout after [maxAttempts] of them.
 *
 * The counters live in memory (so [remainingLockoutMillis] and [attemptsLeft] stay cheap,
 * non-suspending reads for the UI) and are written through to [store] on every change. Call
 * [load] once before use; [recordFailure] and [recordSuccess] load lazily if that was skipped.
 *
 * [now] is wall-clock time (`System.currentTimeMillis`) because the deadline has to remain
 * meaningful after a reboot. If the stored last-update time lies in the future the clock was
 * moved back, which could be an attempt to shorten the lockout: the user is then locked out for
 * a full [lockoutMillis] starting now.
 */
class PinAttemptLimiter(
    private val store: PinLockoutStore,
    private val now: () -> Long,
    private val maxAttempts: Int = MAX_ATTEMPTS,
    private val lockoutMillis: Long = LOCKOUT_MILLIS,
) {
    private val mutex = Mutex()

    @Volatile
    private var loaded = false

    @Volatile
    private var failures = 0

    @Volatile
    private var lockedUntil = 0L

    /** True while [store] may hold a record for us (so it is worth a write to clear it). */
    private var hasStoredRecord = false

    /** Wrong attempts still allowed before the next lockout starts. */
    val attemptsLeft: Int
        get() = (maxAttempts - failures).coerceAtLeast(0)

    /** Loads the persisted state. Idempotent; later calls are no-ops. */
    suspend fun load() {
        mutex.withLock { loadLocked() }
    }

    /**
     * Milliseconds until PIN entry is allowed again; 0 when not locked out (or not loaded yet).
     * Never exceeds [lockoutMillis], so a clock moved back while locked out cannot stretch the
     * wait beyond one lockout period.
     */
    fun remainingLockoutMillis(): Long {
        if (!loaded) return 0L
        val until = lockedUntil
        if (until == 0L) return 0L
        val remaining = until - now()
        return if (remaining <= 0L) 0L else minOf(remaining, lockoutMillis)
    }

    suspend fun recordFailure() {
        mutex.withLock {
            loadLocked()
            val time = now()
            failures++
            if (failures >= maxAttempts) {
                failures = 0
                lockedUntil = time + lockoutMillis
            }
            persistLocked(time)
        }
    }

    suspend fun recordSuccess() {
        mutex.withLock {
            loadLocked()
            failures = 0
            lockedUntil = 0L
            persistLocked(now())
        }
    }

    private suspend fun loadLocked() {
        if (loaded) return
        val stored = store.read()
        val time = now()
        if (stored == null) {
            failures = 0
            lockedUntil = 0L
            hasStoredRecord = false
        } else if (stored.updatedAtMillis > time) {
            // The clock went backwards since the last write: do not trust the stored deadline.
            failures = 0
            lockedUntil = time + lockoutMillis
            hasStoredRecord = true
            persistLocked(time)
        } else {
            failures = stored.failures.coerceIn(0, maxAttempts - 1)
            lockedUntil = if (stored.lockedUntilMillis > time) stored.lockedUntilMillis else 0L
            hasStoredRecord = true
        }
        loaded = true
    }

    /** Writes the in-memory state through; clears the record once there is nothing to remember. */
    private suspend fun persistLocked(time: Long) {
        if (lockedUntil <= time) lockedUntil = 0L
        if (failures == 0 && lockedUntil == 0L) {
            if (hasStoredRecord) {
                store.write(null)
                hasStoredRecord = false
            }
        } else {
            store.write(PinLockoutState(failures, lockedUntil, updatedAtMillis = time))
            hasStoredRecord = true
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 30_000L
    }
}
