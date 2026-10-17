package com.rookierc.boysdairy.ui.add

import com.rookierc.boysdairy.TEST_ZONE
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.Mood
import com.rookierc.boysdairy.millisOf
import com.rookierc.boysdairy.testEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
