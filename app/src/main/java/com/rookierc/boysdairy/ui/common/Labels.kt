package com.rookierc.boysdairy.ui.common

import androidx.annotation.StringRes
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.Mood

@StringRes
fun EntryType.labelRes(): Int = when (this) {
    EntryType.SOLO -> R.string.entry_type_solo
    EntryType.PARTNER -> R.string.entry_type_partner
}

@StringRes
fun EntryType.hintRes(): Int = when (this) {
    EntryType.SOLO -> R.string.entry_type_solo_hint
    EntryType.PARTNER -> R.string.entry_type_partner_hint
}

fun EntryType.emoji(): String = when (this) {
    EntryType.SOLO -> "🕹️"
    EntryType.PARTNER -> "💞"
}

@StringRes
fun Mood.labelRes(): Int = when (this) {
    Mood.ANXIOUS -> R.string.mood_anxious
    Mood.BORED -> R.string.mood_bored
    Mood.EXCITED -> R.string.mood_excited
    Mood.STRESSED -> R.string.mood_stressed
    Mood.NEUTRAL -> R.string.mood_neutral
    Mood.RELAXED -> R.string.mood_relaxed
    Mood.GUILTY -> R.string.mood_guilty
    Mood.EMPTY -> R.string.mood_empty
    Mood.ENERGETIC -> R.string.mood_energetic
}

/** Emoji chosen from long-established Unicode blocks so they also render on API 26-28 devices. */
fun Mood.emoji(): String = when (this) {
    Mood.ANXIOUS -> "😰"
    Mood.BORED -> "😴"
    Mood.EXCITED -> "🤩"
    Mood.STRESSED -> "😫"
    Mood.NEUTRAL -> "😐"
    Mood.RELAXED -> "😌"
    Mood.GUILTY -> "😔"
    Mood.EMPTY -> "😶"
    Mood.ENERGETIC -> "⚡"
}

/** Reactive label for the duration slider (and history cards). */
@StringRes
fun durationTierRes(minutes: Int): Int = when {
    minutes <= 3 -> R.string.duration_tier_flash
    minutes <= 15 -> R.string.duration_tier_standard
    minutes <= 40 -> R.string.duration_tier_endurance
    else -> R.string.duration_tier_marathon
}

/** Reactive label for the pleasure slider (and history cards). */
@StringRes
fun pleasureTierRes(score: Int): Int = when {
    score <= 2 -> R.string.pleasure_tier_meh
    score <= 4 -> R.string.pleasure_tier_ok
    score <= 6 -> R.string.pleasure_tier_nice
    score <= 8 -> R.string.pleasure_tier_great
    else -> R.string.pleasure_tier_peak
}
