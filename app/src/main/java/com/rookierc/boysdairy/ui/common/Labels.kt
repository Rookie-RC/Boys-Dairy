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
