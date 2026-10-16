package com.rookierc.boysdairy.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.time.format.DateTimeFormatter

/**
 * A [DateTimeFormatter] for the pattern stored in string resource [patternRes], using the
 * currently active (possibly per-app) locale so month and weekday names are localized.
 */
@Composable
fun rememberFormatter(@StringRes patternRes: Int): DateTimeFormatter {
    val pattern = stringResource(patternRes)
    val locale = LocalConfiguration.current.locales.get(0)
    return remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
}
