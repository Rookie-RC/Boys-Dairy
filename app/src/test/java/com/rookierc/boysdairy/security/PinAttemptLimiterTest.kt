package com.rookierc.boysdairy.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinAttemptLimiterTest {

    private var clock = 1_000L
    private val limiter = PinAttemptLimiter(now = { clock })

    @Test
    fun fourFailuresDoNotLockOut() {
        repeat(4) { limiter.recordFailure() }
        assertEquals(0L, limiter.remainingLockoutMillis())
        assertEquals(1, limiter.attemptsLeft)
    }

    @Test
    fun fifthFailureStartsThirtySecondLockout() {
        repeat(5) { limiter.recordFailure() }
        assertEquals(30_000L, limiter.remainingLockoutMillis())

        clock += 29_999L
        assertEquals(1L, limiter.remainingLockoutMillis())

        clock += 1L
        assertEquals(0L, limiter.remainingLockoutMillis())
    }

    @Test
    fun attemptsResetAfterLockout() {
        repeat(5) { limiter.recordFailure() }
        clock += 30_000L
        assertEquals(0L, limiter.remainingLockoutMillis())
        assertEquals(5, limiter.attemptsLeft)
    }

    @Test
    fun successResetsFailureCount() {
        repeat(4) { limiter.recordFailure() }
        limiter.recordSuccess()
        assertEquals(5, limiter.attemptsLeft)
        limiter.recordFailure()
        assertTrue(limiter.remainingLockoutMillis() == 0L)
    }
}
