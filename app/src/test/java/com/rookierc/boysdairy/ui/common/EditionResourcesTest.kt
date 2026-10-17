package com.rookierc.boysdairy.ui.common

import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.data.Edition
import com.rookierc.boysdairy.domain.Achievement
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.ui.home.CooldownTier
import com.rookierc.boysdairy.ui.home.StreakLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Edition-aware resource resolution, the girl-variant table and the per-edition emoji. */
class EditionResourcesTest {

    // ---- resolution -----------------------------------------------------------------------------

    private val variants = mapOf(1 to 101, 2 to 102)

    @Test
    fun theBoyEditionAlwaysKeepsTheBaseId() {
        assertEquals(1, resolveEditionRes(1, Edition.BOY, variants))
        assertEquals(3, resolveEditionRes(3, Edition.BOY, variants))
    }

    @Test
    fun theGirlEditionUsesTheVariantWhenThereIsOne() {
        assertEquals(101, resolveEditionRes(1, Edition.GIRL, variants))
        assertEquals(102, resolveEditionRes(2, Edition.GIRL, variants))
    }

    @Test
    fun theGirlEditionFallsBackToTheBaseIdWithoutAVariant() {
        assertEquals(3, resolveEditionRes(3, Edition.GIRL, variants))
        assertEquals(3, resolveEditionRes(3, Edition.GIRL, emptyMap()))
    }

    @Test
    fun theTypedResolversReadTheirOwnTable() {
        fun check(table: Map<Int, Int>, resolve: (Int, Edition) -> Int) {
            table.forEach { (base, girl) ->
                assertEquals(base, resolve(base, Edition.BOY))
                assertEquals(girl, resolve(base, Edition.GIRL))
            }
        }
        check(GirlVariants.strings, ::editionStringRes)
        check(GirlVariants.arrays, ::editionArrayRes)
        check(GirlVariants.plurals, ::editionPluralsRes)

        // Not in any table: unchanged in both editions.
        assertEquals(R.string.action_ok, editionStringRes(R.string.action_ok, Edition.GIRL))
    }

    // ---- the girl-variant table -----------------------------------------------------------------

    @Test
    fun theLauncherNameHasNoGirlVariant() {
        assertFalse(GirlVariants.strings.containsKey(R.string.app_name))
        assertEquals(R.string.app_name, editionStringRes(R.string.app_name, Edition.GIRL))
        assertFalse(names(R.string::class.java).containsKey("app_name_girl"))
    }

    /** name -> id of one generated R class (R.string, R.array, ...). */
    private fun names(rClass: Class<*>): Map<String, Int> =
        rClass.fields.associate { field -> field.name to field.getInt(null) }

    // Naming convention, registration and format arguments of the table: GirlVariantsTest.

    // ---- per-edition emoji ----------------------------------------------------------------------

    @Test
    fun editionEmojiPicksTheEditionsForm() {
        EditionEmoji.entries.forEach { emoji ->
            assertEquals(emoji.boy, emoji.of(Edition.BOY))
            assertEquals(emoji.girl, emoji.of(Edition.GIRL))
            assertNotEquals("${emoji.name} is listed but identical", emoji.boy, emoji.girl)
        }
    }

    @Test
    fun theBoyEditionKeepsTheOriginalEmoji() {
        assertEquals("🕹️", EntryType.SOLO.emoji(Edition.BOY))
        assertEquals("💞", EntryType.PARTNER.emoji(Edition.BOY))
        assertEquals("🧘", CooldownTier.FRESH.emoji(Edition.BOY))
        assertEquals("💪", CooldownTier.RELOADING.emoji(Edition.BOY))
        assertEquals("☯️", CooldownTier.MONK.emoji(Edition.BOY))
        assertEquals("🥤", StreakLevel.SUPPLIES.emoji(Edition.BOY))
        assertEquals("🐓", Achievement.EARLY_BIRD.labels(Edition.BOY).emoji)
        assertEquals("🏃", Achievement.MARATHON.labels(Edition.BOY).emoji)
    }

    @Test
    fun theGirlEditionSwapsTheGenderedEmojiAndSharesTheRest() {
        assertEquals(EditionEmoji.SOLO.girl, EntryType.SOLO.emoji(Edition.GIRL))
        assertEquals("💞", EntryType.PARTNER.emoji(Edition.GIRL))
        assertEquals(EditionEmoji.MEDITATE.girl, CooldownTier.FRESH.emoji(Edition.GIRL))
        assertEquals("🔋", CooldownTier.RECOVERING.emoji(Edition.GIRL))
        assertEquals(EditionEmoji.EARLY_BIRD.girl, Achievement.EARLY_BIRD.labels(Edition.GIRL).emoji)
        // Names and descriptions come from strings (and their girl variants), not from here.
        Achievement.entries.forEach { achievement ->
            assertEquals(achievement.labels(Edition.BOY).nameRes, achievement.labels(Edition.GIRL).nameRes)
            assertEquals(achievement.labels(Edition.BOY).descRes, achievement.labels(Edition.GIRL).descRes)
        }
    }
}
