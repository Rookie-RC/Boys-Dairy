package com.rookierc.boysdairy.domain

import com.rookierc.boysdairy.TEST_ZONE
import com.rookierc.boysdairy.millisOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class BackupReminderTest {

    private val now = millisOf(2026, 10, 17, hour = 20, minute = 0)
    private val enough = BackupReminder.MIN_ENTRIES

    private fun remind(
        entryCount: Int = enough,
        lastExportAt: Long? = null,
        snoozedUntil: Long? = null,
        nowMillis: Long = now,
        zone: ZoneId = TEST_ZONE,
    ) = shouldRemindBackup(entryCount, lastExportAt, snoozedUntil, nowMillis, zone)

    // ---- entry count --------------------------------------------------------------------------

    @Test
    fun noEntriesNoReminder() {
        assertFalse(remind(entryCount = 0))
    }

    @Test
    fun nineEntriesAreNotWorthNaggingAbout() {
        assertFalse(remind(entryCount = 9))
    }

    @Test
    fun exactlyTenEntriesNeverExportedReminds() {
        assertTrue(remind(entryCount = 10))
    }

    @Test
    fun manyEntriesNeverExportedReminds() {
        assertTrue(remind(entryCount = 500))
    }

    // ---- days since the last export (calendar days) ------------------------------------------

    @Test
    fun exportedTodayDoesNotRemind() {
        assertFalse(remind(lastExportAt = millisOf(2026, 10, 17, hour = 8)))
    }

    @Test
    fun exportedExactly30CalendarDaysAgoDoesNotRemindYet() {
        assertFalse(remind(lastExportAt = millisOf(2026, 9, 17, hour = 20)))
    }

    @Test
    fun exported31CalendarDaysAgoReminds() {
        assertTrue(remind(lastExportAt = millisOf(2026, 9, 16, hour = 20)))
    }

    @Test
    fun daysAreCalendarDaysNot24HourBlocks() {
        // 30 calendar days although it is almost 31 x 24 h.
        assertFalse(
            remind(
                lastExportAt = millisOf(2026, 9, 17, hour = 0, minute = 1),
                nowMillis = millisOf(2026, 10, 17, hour = 23, minute = 59),
            ),
        )
        // 31 calendar days although it is barely more than 30 x 24 h.
        assertTrue(
            remind(
                lastExportAt = millisOf(2026, 9, 16, hour = 23, minute = 59),
                nowMillis = millisOf(2026, 10, 17, hour = 0, minute = 1),
            ),
        )
    }

    @Test
    fun calendarDaysFollowTheGivenZone() {
        // 2026-09-16 23:30 in Shanghai is 2026-09-17 00:30 in Tokyo: 31 days in one zone, 30 in the other.
        val export = millisOf(2026, 9, 16, hour = 23, minute = 30)
        val laterNow = millisOf(2026, 10, 17, hour = 12) // 13:00 in Tokyo, same date
        assertTrue(remind(lastExportAt = export, nowMillis = laterNow, zone = TEST_ZONE))
        assertFalse(remind(lastExportAt = export, nowMillis = laterNow, zone = ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun anOldExportDoesNotMatterBelowTenEntries() {
        assertFalse(remind(entryCount = 9, lastExportAt = millisOf(2025, 1, 1)))
    }

    // ---- clock turned back: lastExportAt in the future -----------------------------------------

    @Test
    fun anExportLaterTodayCountsAsJustExported() {
        // Small skew, e.g. an NTP correction right after exporting.
        assertFalse(remind(lastExportAt = now + 5_000L))
    }

    @Test
    fun anExportOnAFutureDayIsNotTrustedAndReminds() {
        assertTrue(remind(lastExportAt = millisOf(2026, 10, 18, hour = 9)))
        assertTrue(remind(lastExportAt = millisOf(2030, 1, 1)))
    }

    // ---- snooze --------------------------------------------------------------------------------

    @Test
    fun aRunningSnoozeHidesTheReminder() {
        assertFalse(remind(snoozedUntil = now + 1L))
        assertFalse(remind(snoozedUntil = now + 3 * 24 * 60 * 60 * 1000L))
    }

    @Test
    fun theReminderIsBackTheMomentTheSnoozeEnds() {
        assertTrue(remind(snoozedUntil = now))
        assertTrue(remind(snoozedUntil = now - 1L))
    }

    @Test
    fun aFreshSnoozeLastsSevenDays() {
        val snoozedAt = now
        val until = backupReminderSnoozeEnd(snoozedAt)

        assertEquals(snoozedAt + 7L * 24 * 60 * 60 * 1000, until)
        assertFalse(remind(snoozedUntil = until, nowMillis = snoozedAt))
        assertFalse(remind(snoozedUntil = until, nowMillis = until - 1L))
        assertTrue(remind(snoozedUntil = until, nowMillis = until))
    }

    @Test
    fun aSnoozeFurtherAheadThanOneSnoozeMeansTheClockWentBackAndIsIgnored() {
        assertTrue(remind(snoozedUntil = now + BackupReminder.SNOOZE_MILLIS + 1L))
        assertTrue(remind(snoozedUntil = millisOf(2030, 1, 1)))
    }

    @Test
    fun anExpiredSnoozeDoesNotRemindAboutAFreshExport() {
        assertFalse(remind(lastExportAt = millisOf(2026, 10, 1), snoozedUntil = now - 1L))
    }
}
