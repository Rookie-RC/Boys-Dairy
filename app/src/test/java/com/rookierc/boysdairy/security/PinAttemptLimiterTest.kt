package com.rookierc.boysdairy.security

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** In-memory [PinLockoutStore] that counts reads and writes. */
class FakeLockoutStore(var state: PinLockoutState? = null) : PinLockoutStore {
    var reads = 0
        private set
    var writes = 0
        private set

    override suspend fun read(): PinLockoutState? {
        reads++
        return state
    }

    override suspend fun write(state: PinLockoutState?) {
        writes++
        this.state = state
    }
}

class PinAttemptLimiterTest {

    private var clock = 1_000L
    private val store = FakeLockoutStore()

    /** A fresh limiter on the shared store and clock, i.e. what a process restart produces. */
    private fun newLimiter() = PinAttemptLimiter(store = store, now = { clock })

    private fun loadedLimiter() = newLimiter().also { runBlocking { it.load() } }

    @Test
    fun fourFailuresDoNotLockOut() = runBlocking {
        val limiter = loadedLimiter()
        repeat(4) { limiter.recordFailure() }
        assertEquals(0L, limiter.remainingLockoutMillis())
        assertEquals(1, limiter.attemptsLeft)
    }

    @Test
    fun fifthFailureStartsThirtySecondLockout() = runBlocking {
        val limiter = loadedLimiter()
        repeat(5) { limiter.recordFailure() }
        assertEquals(30_000L, limiter.remainingLockoutMillis())

        clock += 29_999L
        assertEquals(1L, limiter.remainingLockoutMillis())

        clock += 1L
        assertEquals(0L, limiter.remainingLockoutMillis())
    }

    @Test
    fun attemptsResetAfterLockout() = runBlocking {
        val limiter = loadedLimiter()
        repeat(5) { limiter.recordFailure() }
        clock += 30_000L
        assertEquals(0L, limiter.remainingLockoutMillis())
        assertEquals(5, limiter.attemptsLeft)
    }

    @Test
    fun successResetsFailureCount() = runBlocking {
        val limiter = loadedLimiter()
        repeat(4) { limiter.recordFailure() }
        limiter.recordSuccess()
        assertEquals(5, limiter.attemptsLeft)
        limiter.recordFailure()
        assertEquals(0L, limiter.remainingLockoutMillis())
        assertEquals(4, limiter.attemptsLeft)
    }

    // ---- persistence --------------------------------------------------------------------------

    @Test
    fun failureCountSurvivesRestart() = runBlocking {
        val first = loadedLimiter()
        repeat(3) { first.recordFailure() }

        val second = loadedLimiter()
        assertEquals(2, second.attemptsLeft)
        assertEquals(0L, second.remainingLockoutMillis())

        // The two remaining attempts after the "restart" still trigger the lockout.
        repeat(2) { second.recordFailure() }
        assertEquals(30_000L, second.remainingLockoutMillis())
    }

    @Test
    fun lockoutSurvivesRestartWithRemainingTimeCounted() = runBlocking {
        val first = loadedLimiter()
        repeat(5) { first.recordFailure() }

        clock += 12_000L // process is killed and restarted 12 s later
        val second = loadedLimiter()
        assertEquals(18_000L, second.remainingLockoutMillis())

        clock += 18_000L
        assertEquals(0L, second.remainingLockoutMillis())
    }

    @Test
    fun lockoutThatExpiredWhileAppWasDeadIsGone() = runBlocking {
        val first = loadedLimiter()
        repeat(5) { first.recordFailure() }

        clock += 3_600_000L // an hour later (e.g. after a reboot)
        val second = loadedLimiter()
        assertEquals(0L, second.remainingLockoutMillis())
        assertEquals(5, second.attemptsLeft)
    }

    @Test
    fun lockoutIsPersistedWithWallClockDeadline() = runBlocking {
        val limiter = loadedLimiter()
        repeat(5) { limiter.recordFailure() }
        assertEquals(PinLockoutState(failures = 0, lockedUntilMillis = 31_000L, updatedAtMillis = 1_000L), store.state)
    }

    @Test
    fun failuresAreWrittenThroughImmediately() = runBlocking {
        val limiter = loadedLimiter()
        limiter.recordFailure()
        // No further call needed: a force-stop right now must not lose the attempt.
        assertEquals(PinLockoutState(failures = 1, lockedUntilMillis = 0L, updatedAtMillis = 1_000L), store.state)
    }

    @Test
    fun successClearsPersistedState() = runBlocking {
        val limiter = loadedLimiter()
        repeat(3) { limiter.recordFailure() }
        limiter.recordSuccess()
        assertNull(store.state)
        assertEquals(5, loadedLimiter().attemptsLeft)
    }

    @Test
    fun successWithNothingStoredDoesNotTouchTheStore() = runBlocking {
        val limiter = loadedLimiter()
        val writesBefore = store.writes
        limiter.recordSuccess()
        limiter.recordSuccess()
        assertEquals(writesBefore, store.writes)
    }

    // ---- clock manipulation -------------------------------------------------------------------

    @Test
    fun clockMovedBackAfterLockoutLocksOutForAFullPeriodFromNow() = runBlocking {
        clock = 1_000_000L
        val first = loadedLimiter()
        repeat(5) { first.recordFailure() }

        clock = 500_000L // user rolls the clock back by ~8 minutes, then restarts the app
        val second = loadedLimiter()
        assertEquals(30_000L, second.remainingLockoutMillis())

        // The new deadline is persisted too: a further restart 10 s later sees 20 s left.
        clock += 10_000L
        assertEquals(20_000L, loadedLimiter().remainingLockoutMillis())
    }

    @Test
    fun clockMovedBackWithOnlyPendingFailuresAlsoLocksOut() = runBlocking {
        clock = 1_000_000L
        val first = loadedLimiter()
        repeat(3) { first.recordFailure() }

        clock = 999_999L
        val second = loadedLimiter()
        assertEquals(30_000L, second.remainingLockoutMillis())
    }

    @Test
    fun clockMovingForwardNormallyIsNotMistakenForTampering() = runBlocking {
        clock = 1_000_000L
        val first = loadedLimiter()
        repeat(3) { first.recordFailure() }

        clock = 1_000_001L
        val second = loadedLimiter()
        assertEquals(0L, second.remainingLockoutMillis())
        assertEquals(2, second.attemptsLeft)
    }

    @Test
    fun clockMovedBackWhileRunningNeverStretchesLockoutBeyondOnePeriod() = runBlocking {
        clock = 100_000L
        val limiter = loadedLimiter()
        repeat(5) { limiter.recordFailure() }

        clock = 0L // clock rolled back by 100 s while the app keeps running
        assertEquals(30_000L, limiter.remainingLockoutMillis())
    }

    @Test
    fun cleanStoreAfterSuccessDoesNotLockOutWhenClockIsLaterSetBack() = runBlocking {
        clock = 1_000_000L
        val first = loadedLimiter()
        repeat(2) { first.recordFailure() }
        first.recordSuccess()

        clock = 10L
        assertEquals(0L, loadedLimiter().remainingLockoutMillis())
    }

    // ---- loading ------------------------------------------------------------------------------

    @Test
    fun remainingIsZeroBeforeLoad() {
        store.state = PinLockoutState(failures = 0, lockedUntilMillis = 31_000L, updatedAtMillis = 1_000L)
        assertEquals(0L, newLimiter().remainingLockoutMillis())
    }

    @Test
    fun recordFailureLoadsLazily() = runBlocking {
        store.state = PinLockoutState(failures = 4, lockedUntilMillis = 0L, updatedAtMillis = 900L)
        val limiter = newLimiter() // never explicitly loaded
        limiter.recordFailure()
        assertEquals(30_000L, limiter.remainingLockoutMillis())
    }

    @Test
    fun loadIsIdempotent() = runBlocking {
        val limiter = newLimiter()
        limiter.load()
        limiter.load()
        assertEquals(1, store.reads)
    }

    @Test
    fun corruptStoredFailureCountIsClampedBelowTheLimit() = runBlocking {
        store.state = PinLockoutState(failures = 99, lockedUntilMillis = 0L, updatedAtMillis = 900L)
        val limiter = loadedLimiter()
        assertEquals(1, limiter.attemptsLeft)
        assertEquals(0L, limiter.remainingLockoutMillis())
    }
}
