package com.rookierc.boysdairy.ui.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the lock screen opens the fingerprint prompt by itself. */
class BiometricAutoPromptTest {

    private val gate = BiometricAutoPrompt()

    @Test
    fun coldStartPromptsOnceWhicheverEventComesFirst() {
        assertFalse(gate.onLocked()) // screen not visible yet
        assertTrue(gate.onScreenStarted())

        val other = BiometricAutoPrompt()
        assertFalse(other.onScreenStarted()) // lock state not known yet
        assertTrue(other.onLocked())
    }

    @Test
    fun cancellingMeansNoMoreAutomaticPromptsUntilTheScreenShowsAgain() {
        gate.onLocked()
        assertTrue(gate.onScreenStarted())
        gate.onPromptClosed() // "use code"

        assertFalse(gate.onLocked())

        // The 👆 key still works.
        assertTrue(gate.tryManual())
    }

    @Test
    fun comingBackFromTheBackgroundPromptsAgain() {
        gate.onLocked()
        assertTrue(gate.onScreenStarted())
        gate.onPromptClosed()

        gate.onScreenStopped(changingConfigurations = false)
        assertTrue(gate.onScreenStarted())
    }

    @Test
    fun rotatingIsNotANewAppearance() {
        gate.onLocked()
        assertTrue(gate.onScreenStarted())
        gate.onPromptClosed()

        gate.onScreenStopped(changingConfigurations = true)
        assertFalse(gate.onScreenStarted())

        // A real trip to the background afterwards still counts.
        gate.onScreenStopped(changingConfigurations = false)
        assertTrue(gate.onScreenStarted())
    }

    @Test
    fun neverTwoPromptsAtOnce() {
        gate.onLocked()
        assertTrue(gate.onScreenStarted())

        assertFalse(gate.tryManual())
        gate.onScreenStopped(changingConfigurations = false)
        assertFalse(gate.onScreenStarted())
        assertFalse(gate.onLocked())

        gate.onPromptClosed()
        assertTrue(gate.tryManual())
        assertFalse(gate.tryManual())
    }

    @Test
    fun lockingWhileInUsePromptsWhenTheLockScreenShows() {
        gate.onLocked()
        assertTrue(gate.onScreenStarted())
        gate.onPromptClosed()

        // Unlocked: the lock screen leaves.
        gate.onNotLocked()
        gate.onScreenStopped(changingConfigurations = false)

        // "Lock now" (or back from the background): locked first, then the lock screen is composed.
        assertFalse(gate.onLocked())
        assertTrue(gate.onScreenStarted())
    }

    @Test
    fun nothingWhileNotLocked() {
        assertFalse(gate.onScreenStarted()) // e.g. first-run setup
        assertFalse(gate.tryManual())

        assertTrue(gate.onLocked()) // the screen is already visible

        // Unlocked while that attempt was still pending: it is forgotten, no stuck "in flight".
        gate.onNotLocked()
        assertFalse(gate.tryManual())
        assertTrue(gate.onLocked())
    }
}
