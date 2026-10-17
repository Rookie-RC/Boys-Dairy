package com.rookierc.boysdairy.ui.lock

/**
 * Decides when the lock screen opens the fingerprint prompt by itself: once every time the lock
 * screen shows up (app opened or brought back, or locked while in use), and never again until it
 * shows up anew, so "use code" / cancel really means "let me type". Never two prompts at once.
 *
 * Plain state machine (no Android, no coroutines) so the rules are unit-tested. Every `true`
 * returned by [onLocked], [onScreenStarted] or [tryManual] must be followed by exactly one
 * [onPromptClosed] once that attempt is over (shown and closed, or not shown at all).
 */
class BiometricAutoPrompt {
    private var locked = false
    private var visible = false
    private var armed = false
    private var inFlight = false

    /** A configuration change (rotation, language) recreates the screen; that is not a new appearance. */
    private var skipNextStart = false

    /** The app became locked (PIN exists); the lock screen asks for it. Repeated calls change nothing. */
    fun onLocked(): Boolean {
        if (!locked) {
            locked = true
            if (!inFlight) armed = true
        }
        return take()
    }

    /** First-run setup or unlocked: no prompt, and whatever was pending is forgotten. */
    fun onNotLocked() {
        locked = false
        armed = false
        inFlight = false
    }

    /** The lock screen became visible (its lifecycle reached STARTED). */
    fun onScreenStarted(): Boolean {
        visible = true
        if (skipNextStart) {
            skipNextStart = false
        } else if (!inFlight) {
            armed = true
        }
        return take()
    }

    /** The lock screen went away (app in the background, screen left, or being recreated). */
    fun onScreenStopped(changingConfigurations: Boolean) {
        visible = false
        if (changingConfigurations) skipNextStart = true
    }

    /** The user tapped the 👆 key: allowed whenever no prompt is already open. */
    fun tryManual(): Boolean {
        if (!locked || inFlight) return false
        armed = false
        inFlight = true
        return true
    }

    /** The current attempt is over. Does not re-arm: that only happens when the screen shows again. */
    fun onPromptClosed() {
        inFlight = false
    }

    private fun take(): Boolean {
        if (!(armed && visible && locked) || inFlight) return false
        armed = false
        inFlight = true
        return true
    }
}
