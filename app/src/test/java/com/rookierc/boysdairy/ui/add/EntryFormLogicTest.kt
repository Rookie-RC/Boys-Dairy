package com.rookierc.boysdairy.ui.add

import com.rookierc.boysdairy.TEST_ZONE
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.Mood
import com.rookierc.boysdairy.millisOf
import com.rookierc.boysdairy.testEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class EntryFormLogicTest {

    // ---- resolveTimestamp ---------------------------------------------------------------------

    private val now = millisOf(2026, 10, 17, 20, 0)

    @Test
    fun untouchedTimeKeepsTheOriginalTimestampToTheMillisecond() {
        // 12:34:56.789 on 3 Oct: the form only shows 12:34.
        val original = testEntry(timestamp = millisOf(2026, 10, 3, 12, 34) + 56_789, id = 7)
        val shown = original.timestamp.toFormDateTime(TEST_ZONE)
        assertEquals(LocalDateTime.of(2026, 10, 3, 12, 34), shown)

        assertEquals(original.timestamp, resolveTimestamp(shown, original, TEST_ZONE, now))
    }

    @Test
    fun untouchedTimeIsKeptEvenIfItLiesInTheFuture() {
        // An imported entry from a device with a wrong clock: editing other fields must not move it.
        val original = testEntry(timestamp = now + 3_600_000L + 123, id = 7)
        val shown = original.timestamp.toFormDateTime(TEST_ZONE)

        assertEquals(original.timestamp, resolveTimestamp(shown, original, TEST_ZONE, now))
    }

    @Test
    fun changedTimeIsRecomputedFromTheChosenMinute() {
        val original = testEntry(timestamp = millisOf(2026, 10, 3, 12, 34) + 56_789, id = 7)
        val chosen = LocalDateTime.of(2026, 10, 3, 12, 35)

        assertEquals(millisOf(2026, 10, 3, 12, 35), resolveTimestamp(chosen, original, TEST_ZONE, now))
    }

    @Test
    fun changedTimeCannotBeInTheFuture() {
        val original = testEntry(timestamp = millisOf(2026, 10, 3, 12, 0), id = 7)
        val chosen = LocalDateTime.of(2026, 10, 17, 23, 0)

        assertEquals(now, resolveTimestamp(chosen, original, TEST_ZONE, now))
        assertEquals(now, resolveTimestamp(chosen, original = null, zone = TEST_ZONE, nowMillis = now))
    }

    @Test
    fun newEntryUsesTheChosenMinute() {
        val chosen = LocalDateTime.of(2026, 10, 17, 8, 15)
        assertEquals(millisOf(2026, 10, 17, 8, 15), resolveTimestamp(chosen, null, TEST_ZONE, now))
    }

    @Test
    fun originalIsComparedInTheGivenZone() {
        val original = testEntry(timestamp = millisOf(2026, 10, 3, 12, 34) + 500, id = 7)
        val tokyo = ZoneId.of("Asia/Tokyo")
        val shownInTokyo = original.timestamp.toFormDateTime(tokyo)
        assertEquals(LocalDateTime.of(2026, 10, 3, 13, 34), shownInTokyo)

        assertEquals(original.timestamp, resolveTimestamp(shownInTokyo, original, tokyo, now))
    }

    // ---- backfillDateTime ---------------------------------------------------------------------

    @Test
    fun backfillUsesThatDayAtTheCurrentTimeOfDay() {
        val now = LocalDateTime.of(2026, 10, 17, 21, 42, 33)
        assertEquals(
            LocalDateTime.of(2026, 10, 5, 21, 42),
            backfillDateTime(LocalDate.of(2026, 10, 5), now),
        )
    }

    @Test
    fun backfillForTodayIsNow() {
        val now = LocalDateTime.of(2026, 10, 17, 21, 42, 33)
        assertEquals(LocalDateTime.of(2026, 10, 17, 21, 42), backfillDateTime(LocalDate.of(2026, 10, 17), now))
    }

    @Test
    fun backfillNeverEndsUpInTheFuture() {
        val now = LocalDateTime.of(2026, 10, 17, 21, 42, 33)
        assertEquals(LocalDateTime.of(2026, 10, 17, 21, 42), backfillDateTime(LocalDate.of(2026, 10, 20), now))
    }

    // ---- note length --------------------------------------------------------------------------

    @Test
    fun noteMayGrowUpToTheLimit() {
        val current = "a".repeat(999)
        assertEquals(current + "b", acceptNoteEdit(current, current + "b", maxLength = 1000))
    }

    @Test
    fun noteCannotGrowPastTheLimit() {
        val current = "a".repeat(1000)
        assertSame(current, acceptNoteEdit(current, current + "b", maxLength = 1000))
        // A paste that would overshoot is refused as a whole, nothing gets cut off.
        val almost = "a".repeat(990)
        assertSame(almost, acceptNoteEdit(almost, almost + "b".repeat(20), maxLength = 1000))
    }

    @Test
    fun anOverlongOldNoteCanBeShortenedButNotGrown() {
        val old = "x".repeat(1500)
        assertEquals("x".repeat(1499), acceptNoteEdit(old, "x".repeat(1499), maxLength = 1000))
        assertEquals("y" + "x".repeat(1499), acceptNoteEdit(old, "y" + "x".repeat(1499), maxLength = 1000))
        assertSame(old, acceptNoteEdit(old, old + "!", maxLength = 1000))
    }

    @Test
    fun noteToStoreTrimsNewNotesButKeepsAnUnchangedOriginal() {
        assertEquals("hello", noteToStore("  hello \n", originalNote = null))
        // Only surrounding blanks differ: the stored note stays exactly as it was.
        assertEquals(" hello ", noteToStore("hello", originalNote = " hello "))
        assertEquals("hello world", noteToStore(" hello world ", originalNote = " hello "))
    }

    // ---- unsaved changes ----------------------------------------------------------------------

    private val baseline = EntryFormValues(
        dateTime = LocalDateTime.of(2026, 10, 3, 12, 34),
        type = EntryType.SOLO,
        durationMinutes = 10,
        moodBefore = Mood.NEUTRAL,
        moodAfter = Mood.RELAXED,
        pleasure = 5,
        note = "note",
    )

    @Test
    fun identicalFormHasNoChanges() {
        assertFalse(baseline.copy().differsFrom(baseline))
    }

    @Test
    fun surroundingBlanksInTheNoteAreNotAChange() {
        assertFalse(baseline.copy(note = " note \n").differsFrom(baseline))
    }

    @Test
    fun everyFieldCountsAsAChange() {
        assertTrue(baseline.copy(dateTime = baseline.dateTime.plusMinutes(1)).differsFrom(baseline))
        assertTrue(baseline.copy(type = EntryType.PARTNER).differsFrom(baseline))
        assertTrue(baseline.copy(durationMinutes = 11).differsFrom(baseline))
        assertTrue(baseline.copy(moodBefore = Mood.BORED).differsFrom(baseline))
        assertTrue(baseline.copy(moodAfter = Mood.GUILTY).differsFrom(baseline))
        assertTrue(baseline.copy(pleasure = 6).differsFrom(baseline))
        assertTrue(baseline.copy(note = "note!").differsFrom(baseline))
    }

    @Test
    fun changingSomethingBackIsNoChange() {
        val edited = baseline.copy(durationMinutes = 11).copy(durationMinutes = 10)
        assertFalse(edited.differsFrom(baseline))
    }

    @Test
    fun aSoloKeywordCountsAsAChange() {
        assertTrue(baseline.copy(keyword = "Mia").differsFrom(baseline))
        val withKeyword = baseline.copy(keyword = "Mia")
        assertTrue(withKeyword.copy(keyword = "mia").differsFrom(withKeyword)) // case is a real edit
        assertTrue(withKeyword.copy(keyword = "").differsFrom(withKeyword))
    }

    @Test
    fun surroundingBlanksInTheKeywordAreNotAChange() {
        val withKeyword = baseline.copy(keyword = "Mia")
        assertFalse(withKeyword.copy(keyword = "  Mia ").differsFrom(withKeyword))
        assertFalse(baseline.copy(keyword = "   ").differsFrom(baseline))
    }

    @Test
    fun aHiddenKeywordOfAPartnerFormIsNoChange() {
        // Started as partner (latest entry), typed a keyword under solo, switched back to partner:
        // nothing different would be saved.
        val partner = baseline.copy(type = EntryType.PARTNER)
        assertFalse(partner.copy(keyword = "Mia").differsFrom(partner))
        // Switching solo -> partner is still a change (the type), whatever the keyword.
        assertTrue(baseline.copy(keyword = "Mia").copy(type = EntryType.PARTNER).differsFrom(baseline))
    }

    // ---- keyword input / storing --------------------------------------------------------------

    @Test
    fun keywordMayGrowUpToTheLimitButNotPastIt() {
        val current = "a".repeat(29)
        assertEquals(current + "b", acceptKeywordEdit(current, current + "b", maxLength = 30))
        val full = "a".repeat(30)
        assertSame(full, acceptKeywordEdit(full, full + "b", maxLength = 30))
        // A paste that would overshoot is refused as a whole.
        assertSame(current, acceptKeywordEdit(current, current + "bcd", maxLength = 30))
    }

    @Test
    fun anOverlongOldKeywordCanBeShortenedButNotGrown() {
        val old = "x".repeat(40)
        assertEquals("x".repeat(39), acceptKeywordEdit(old, "x".repeat(39), maxLength = 30))
        assertSame(old, acceptKeywordEdit(old, old + "!", maxLength = 30))
    }

    @Test
    fun pastedLineBreaksInTheKeywordBecomeSpaces() {
        assertEquals("Mia Lux", acceptKeywordEdit("", "Mia\nLux", maxLength = 30))
        assertEquals("Mia Lux ", acceptKeywordEdit("", "Mia\r\nLux\r", maxLength = 30))
        assertEquals("", acceptKeywordEdit("Mia", "", maxLength = 30))
    }

    @Test
    fun keywordToStoreTrimsAndDropsBlanks() {
        assertEquals("Mia", keywordToStore("  Mia ", EntryType.SOLO, originalKeyword = null))
        assertNull(keywordToStore("", EntryType.SOLO, originalKeyword = null))
        assertNull(keywordToStore("  \n", EntryType.SOLO, originalKeyword = null))
        // Never shortened when saving: the limit is an input rule only.
        assertEquals("x".repeat(40), keywordToStore("x".repeat(40), EntryType.SOLO, originalKeyword = null))
    }

    @Test
    fun keywordIsOnlyStoredForSoloEntries() {
        assertNull(keywordToStore("Mia", EntryType.PARTNER, originalKeyword = null))
        // Editing a solo entry into a partner one drops its keyword.
        assertNull(keywordToStore("Mia", EntryType.PARTNER, originalKeyword = "Mia"))
    }

    @Test
    fun editingKeepsAnUnchangedOriginalKeywordToTheCharacter() {
        val original = " Mia "
        assertSame(original, keywordToStore("Mia", EntryType.SOLO, originalKeyword = original))
        assertEquals("Zoe", keywordToStore(" Zoe", EntryType.SOLO, originalKeyword = "Mia"))
        assertNull(keywordToStore("", EntryType.SOLO, originalKeyword = "Mia"))
    }

    // ---- quick-time chips ---------------------------------------------------------------------

    /** 20:47:38.250 on 17 Oct. */
    private val quickNow = millisOf(2026, 10, 17, 20, 47) + 38_250

    @Test
    fun quickTimeOffsetsAreJustNowHalfAnHourOneAndTwoHours() {
        assertEquals(listOf(0, 30, 60, 120), QUICK_TIME_OFFSETS_MINUTES)
    }

    @Test
    fun quickTimeIsNowMinusTheOffsetCutToTheMinute() {
        assertEquals(LocalDateTime.of(2026, 10, 17, 20, 47), quickTimeDateTime(quickNow, 0, TEST_ZONE))
        assertEquals(LocalDateTime.of(2026, 10, 17, 20, 17), quickTimeDateTime(quickNow, 30, TEST_ZONE))
        assertEquals(LocalDateTime.of(2026, 10, 17, 19, 47), quickTimeDateTime(quickNow, 60, TEST_ZONE))
        assertEquals(LocalDateTime.of(2026, 10, 17, 18, 47), quickTimeDateTime(quickNow, 120, TEST_ZONE))
    }

    @Test
    fun quickTimeCrossesMidnightIntoTheDayBefore() {
        val justAfterMidnight = millisOf(2026, 10, 18, 0, 20) + 59_999
        assertEquals(LocalDateTime.of(2026, 10, 18, 0, 20), quickTimeDateTime(justAfterMidnight, 0, TEST_ZONE))
        assertEquals(LocalDateTime.of(2026, 10, 17, 23, 50), quickTimeDateTime(justAfterMidnight, 30, TEST_ZONE))
        assertEquals(LocalDateTime.of(2026, 10, 17, 22, 20), quickTimeDateTime(justAfterMidnight, 120, TEST_ZONE))
        // ... and into the previous month / year.
        val newYear = millisOf(2027, 1, 1, 0, 5)
        assertEquals(LocalDateTime.of(2026, 12, 31, 23, 5), quickTimeDateTime(newYear, 60, TEST_ZONE))
    }

    @Test
    fun quickTimeCountsRealMinutesAcrossADaylightSavingChange() {
        // Berlin falls back from 03:00 CEST to 02:00 CET on 25 Oct 2026. Two real hours before
        // 03:30 CET is 02:30 CEST (on the wall clock: 02:30, not 01:30).
        val berlin = ZoneId.of("Europe/Berlin")
        val now = millisOf(2026, 10, 25, 3, 30, zone = berlin)
        val twoHoursAgo = quickTimeDateTime(now, 120, berlin)
        assertEquals(LocalDateTime.of(2026, 10, 25, 2, 30), twoHoursAgo)
        // Storing it lands exactly two hours back.
        assertEquals(now - 2 * 3_600_000L, resolveTimestamp(twoHoursAgo, null, berlin, now))
    }

    @Test
    fun matchingQuickTimeFindsTheChipOfTheShownTime() {
        assertEquals(0, matchingQuickTime(LocalDateTime.of(2026, 10, 17, 20, 47), quickNow, TEST_ZONE))
        assertEquals(30, matchingQuickTime(LocalDateTime.of(2026, 10, 17, 20, 17), quickNow, TEST_ZONE))
        assertEquals(120, matchingQuickTime(LocalDateTime.of(2026, 10, 17, 18, 47), quickNow, TEST_ZONE))
    }

    @Test
    fun matchingQuickTimeIsNullForAnyOtherTime() {
        assertNull(matchingQuickTime(LocalDateTime.of(2026, 10, 17, 20, 46), quickNow, TEST_ZONE))
        assertNull(matchingQuickTime(LocalDateTime.of(2026, 10, 16, 20, 47), quickNow, TEST_ZONE))
        // A chip tapped a minute ago no longer matches.
        assertNull(matchingQuickTime(LocalDateTime.of(2026, 10, 17, 20, 17), quickNow + 60_000, TEST_ZONE))
    }

    // ---- undo after editing -------------------------------------------------------------------

    @Test
    fun undoOfAnEditWritesBackTheOriginalExactly() {
        val original = testEntry(timestamp = millisOf(2026, 10, 3, 12, 34) + 56_789, id = 7, note = " hi ")
        val saved = original.copy(timestamp = millisOf(2026, 10, 3, 13, 0), pleasure = 9, note = "hi there")

        val revert = revertOfEdit(original, saved)
        assertSame(original, revert)
        assertEquals(7L, revert?.id)
        assertEquals(millisOf(2026, 10, 3, 12, 34) + 56_789, revert?.timestamp)
    }

    @Test
    fun anEditThatChangedNothingHasNoUndo() {
        val original = testEntry(timestamp = millisOf(2026, 10, 3, 12, 34) + 56_789, id = 7)
        assertNull(revertOfEdit(original, original.copy()))
    }

    @Test
    fun anEditOfOnlyTheKeywordCanBeUndone() {
        val original = testEntry(timestamp = millisOf(2026, 10, 3, 12, 34), id = 7, keyword = "Mia")
        assertSame(original, revertOfEdit(original, original.copy(keyword = "Zoe")))
        assertSame(original, revertOfEdit(original, original.copy(keyword = null)))
        assertNull(revertOfEdit(original, original.copy(keyword = "Mia")))
    }
}
