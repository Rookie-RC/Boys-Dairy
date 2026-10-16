package com.rookierc.boysdairy.ui.lock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Polls [remainingMillis] and reports whole seconds left through [onTick] until the lockout is
 * over. The final callback is always `onTick(0)`.
 */
class LockoutCountdown(
    private val scope: CoroutineScope,
    private val remainingMillis: () -> Long,
    private val onTick: (secondsLeft: Int) -> Unit,
) {
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            while (true) {
                val millis = remainingMillis()
                if (millis <= 0L) {
                    onTick(0)
                    break
                }
                onTick(((millis + 999L) / 1000L).toInt())
                delay(TICK_MILLIS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val TICK_MILLIS = 250L
    }
}
