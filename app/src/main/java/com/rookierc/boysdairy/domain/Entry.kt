package com.rookierc.boysdairy.domain

enum class EntryType { SOLO, PARTNER }

enum class Mood {
    ANXIOUS,
    BORED,
    EXCITED,
    STRESSED,
    NEUTRAL,
    RELAXED,
    GUILTY,
    EMPTY,
    ENERGETIC;

    companion object {
        /** Moods offered for "before"; order is the display order. */
        val beforeOptions: List<Mood> = listOf(ANXIOUS, BORED, EXCITED, STRESSED, NEUTRAL)

        /** Moods offered for "after"; order is the display order. */
        val afterOptions: List<Mood> = listOf(RELAXED, GUILTY, EMPTY, ENERGETIC, NEUTRAL)
    }
}

/** A single diary entry. [id] is 0 for entries that have not been stored yet. */
data class Entry(
    val id: Long = 0,
    val timestamp: Long,
    val type: EntryType,
    val durationMinutes: Int,
    val moodBefore: Mood,
    val moodAfter: Mood,
    val pleasure: Int,
    val note: String,
)
