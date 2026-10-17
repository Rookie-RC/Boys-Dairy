package com.rookierc.boysdairy.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rookierc.boysdairy.R
import java.time.Instant
import java.time.ZoneId

/**
 * "Last export: <date>" or "Never exported", for the Settings data section and the home backup
 * reminder. [lastExportAt] is epoch millis, `null` = never.
 */
@Composable
fun lastExportText(lastExportAt: Long?): String {
    val formatter = rememberFormatter(R.string.pattern_date_weekday)
    if (lastExportAt == null) return stringResource(R.string.backup_never_exported)
    val date = Instant.ofEpochMilli(lastExportAt).atZone(ZoneId.systemDefault()).format(formatter)
    return stringResource(R.string.backup_last_export, date)
}
