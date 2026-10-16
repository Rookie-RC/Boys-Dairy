package com.rookierc.boysdairy.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Today's date as observable state. It is refreshed every time the screen is resumed and
 * again right after local midnight while the screen stays open, so "today" highlights and
 * "days since" never go stale.
 */
@Composable
fun rememberToday(): State<LocalDate> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val today = remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val now = LocalDateTime.now()
                today.value = now.toLocalDate()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
                val waitMillis = Duration.between(now, nextMidnight).toMillis() + MIDNIGHT_SLACK_MILLIS
                delay(waitMillis.coerceAtLeast(MIN_WAIT_MILLIS))
            }
        }
    }
    return today
}

private const val MIDNIGHT_SLACK_MILLIS = 500L
private const val MIN_WAIT_MILLIS = 1_000L
