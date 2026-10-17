package com.rookierc.boysdairy.ui.common

import androidx.annotation.StringRes
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.data.Edition
import com.rookierc.boysdairy.domain.Achievement
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

fun EntryType.emoji(edition: Edition): String = when (this) {
    EntryType.SOLO -> EditionEmoji.SOLO.of(edition)
    EntryType.PARTNER -> "💞"
}

@StringRes
fun Edition.labelRes(): Int = when (this) {
    Edition.BOY -> R.string.edition_boy_name
    Edition.GIRL -> R.string.edition_girl_name
}

@StringRes
fun Edition.taglineRes(): Int = when (this) {
    Edition.BOY -> R.string.edition_boy_tagline
    Edition.GIRL -> R.string.edition_girl_tagline
}

/** The edition's own face (chooser, Settings): the same whichever edition is active. */
fun Edition.emoji(): String = when (this) {
    Edition.BOY -> "👦"
    Edition.GIRL -> "👧"
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

/** Reactive label for the duration slider. */
@StringRes
fun durationTierRes(minutes: Int): Int = when {
    minutes <= 3 -> R.string.duration_tier_flash
    minutes <= 15 -> R.string.duration_tier_standard
    minutes <= 40 -> R.string.duration_tier_endurance
    else -> R.string.duration_tier_marathon
}

/** Reactive label for the pleasure slider. */
@StringRes
fun pleasureTierRes(score: Int): Int = when {
    score <= 2 -> R.string.pleasure_tier_meh
    score <= 4 -> R.string.pleasure_tier_ok
    score <= 6 -> R.string.pleasure_tier_nice
    score <= 8 -> R.string.pleasure_tier_great
    else -> R.string.pleasure_tier_peak
}

/** Sticker emoji, name and description of an [Achievement] (achievement wall, unlock snackbar). */
internal class AchievementLabels(val emoji: String, @StringRes val nameRes: Int, @StringRes val descRes: Int)

internal fun Achievement.labels(edition: Edition): AchievementLabels = when (this) {
    Achievement.FIRST_ENTRY -> AchievementLabels(
        "🐣", R.string.achievement_first_entry_name, R.string.achievement_first_entry_desc,
    )
    Achievement.TOTAL_10 -> AchievementLabels(
        "🔟", R.string.achievement_total_10_name, R.string.achievement_total_10_desc,
    )
    Achievement.TOTAL_50 -> AchievementLabels(
        "🎖️", R.string.achievement_total_50_name, R.string.achievement_total_50_desc,
    )
    Achievement.TOTAL_100 -> AchievementLabels(
        "💯", R.string.achievement_total_100_name, R.string.achievement_total_100_desc,
    )
    Achievement.STREAK_3 -> AchievementLabels(
        "🔥", R.string.achievement_streak_3_name, R.string.achievement_streak_3_desc,
    )
    Achievement.STREAK_7 -> AchievementLabels(
        "📅", R.string.achievement_streak_7_name, R.string.achievement_streak_7_desc,
    )
    Achievement.GAP_7 -> AchievementLabels(
        EditionEmoji.MEDITATE.of(edition),
        R.string.achievement_gap_7_name,
        R.string.achievement_gap_7_desc,
    )
    Achievement.GAP_30 -> AchievementLabels(
        "🏔️", R.string.achievement_gap_30_name, R.string.achievement_gap_30_desc,
    )
    Achievement.PARTNER_10 -> AchievementLabels(
        "💞", R.string.achievement_partner_10_name, R.string.achievement_partner_10_desc,
    )
    Achievement.NIGHT_OWL -> AchievementLabels(
        "🦉", R.string.achievement_night_owl_name, R.string.achievement_night_owl_desc,
    )
    Achievement.EARLY_BIRD -> AchievementLabels(
        EditionEmoji.EARLY_BIRD.of(edition),
        R.string.achievement_early_bird_name,
        R.string.achievement_early_bird_desc,
    )
    Achievement.MARATHON -> AchievementLabels(
        EditionEmoji.RUNNER.of(edition),
        R.string.achievement_marathon_name,
        R.string.achievement_marathon_desc,
    )
    Achievement.PEAK_PLEASURE -> AchievementLabels(
        EditionEmoji.PLEASURE.of(edition),
        R.string.achievement_peak_pleasure_name,
        R.string.achievement_peak_pleasure_desc,
    )
    Achievement.SAGE_MODE -> AchievementLabels(
        "🔮", R.string.achievement_sage_mode_name, R.string.achievement_sage_mode_desc,
    )
}
