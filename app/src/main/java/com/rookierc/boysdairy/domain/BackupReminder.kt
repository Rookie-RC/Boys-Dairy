package com.rookierc.boysdairy.domain

import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Thresholds of the home screen's "back up your diary" reminder. */
object BackupReminder {
    /** Below this many entries there is not much to lose yet, so no nagging. */
    const val MIN_ENTRIES = 10

    /** Remind when the last export is more than this many calendar days ago. */
    const val MAX_DAYS_SINCE_EXPORT = 30L

    /** "Remind me later" hides the reminder for this long. */
    const val SNOOZE_MILLIS = 7L * 24 * 60 * 60 * 1000
}

/** When a snooze started at [nowMillis] ends. */
fun backupReminderSnoozeEnd(nowMillis: Long): Long = nowMillis + BackupReminder.SNOOZE_MILLIS

/**
 * Whether the home screen should show the backup reminder: at least [BackupReminder.MIN_ENTRIES]
 * entries, never exported or last exported more than [BackupReminder.MAX_DAYS_SINCE_EXPORT]
 * calendar days ago (in [zone]), and not snoozed.
 *
 * All times are wall-clock epoch millis, so the system clock may have jumped since they were
 * stored. Both stored values are checked against that, and when in doubt we remind: a reminder
 * too many costs one tap on "remind me later", a silenced one can cost the whole diary.
 *  - [lastExportAt] on a *later calendar day* than today means the clock was wrong at export time
 *    or has been turned back since. Trusting it would silence the reminder for as long as the
 *    clock was off (possibly years), so it is treated as untrustworthy and we remind. A small skew
 *    within the same day (e.g. an NTP correction right after exporting) still counts as fresh.
 *  - [snoozedUntil] further ahead than one snooze ([BackupReminder.SNOOZE_MILLIS]) can only come
 *    from a clock that was turned back, so that snooze is ignored.
 *
 * The snooze ends exactly at [snoozedUntil]: at that moment the reminder is due again.
 */
fun shouldRemindBackup(
    entryCount: Int,
    lastExportAt: Long?,
    snoozedUntil: Long?,
    nowMillis: Long,
    zone: ZoneId,
): Boolean {
    if (entryCount < BackupReminder.MIN_ENTRIES) return false

    if (snoozedUntil != null) {
        val snoozeLeft = snoozedUntil - nowMillis
        if (snoozeLeft > 0 && snoozeLeft <= BackupReminder.SNOOZE_MILLIS) return false
    }

    if (lastExportAt == null) return true
    val days = ChronoUnit.DAYS.between(lastExportAt.toLocalDate(zone), nowMillis.toLocalDate(zone))
    return days < 0 || days > BackupReminder.MAX_DAYS_SINCE_EXPORT
}
