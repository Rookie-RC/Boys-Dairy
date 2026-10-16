package com.rookierc.boysdairy.ui.home

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import com.rookierc.boysdairy.R

/** How long since the last "launch", from "sage mode" to "enlightened monk". */
enum class CooldownTier(
    val emoji: String,
    @StringRes val titleRes: Int,
    @ArrayRes val subtitlesRes: Int,
) {
    NONE("😇", R.string.cooldown_none_title, R.array.cooldown_none_subtitles),
    FRESH("🧘", R.string.cooldown_fresh_title, R.array.cooldown_fresh_subtitles),
    RECOVERING("🔋", R.string.cooldown_recovering_title, R.array.cooldown_recovering_subtitles),
    RELOADING("💪", R.string.cooldown_reloading_title, R.array.cooldown_reloading_subtitles),
    ASCETIC("🧊", R.string.cooldown_ascetic_title, R.array.cooldown_ascetic_subtitles),
    SERENE("🍵", R.string.cooldown_serene_title, R.array.cooldown_serene_subtitles),
    MONK("☯️", R.string.cooldown_monk_title, R.array.cooldown_monk_subtitles),
    ;

    companion object {
        /** @param daysSinceLast calendar days since the latest entry, `null` when there are no entries. */
        fun of(daysSinceLast: Long?): CooldownTier = when {
            daysSinceLast == null -> NONE
            daysSinceLast <= 0L -> FRESH
            daysSinceLast <= 2L -> RECOVERING
            daysSinceLast <= 6L -> RELOADING
            daysSinceLast <= 13L -> ASCETIC
            daysSinceLast <= 29L -> SERENE
            else -> MONK
        }
    }
}

/** Gentle "maybe take a break" warnings for consecutive active days. */
enum class StreakLevel(
    val emoji: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
) {
    SUPPLIES("🥤", R.string.streak_supplies_title, R.string.streak_supplies_subtitle),
    SOS("😵", R.string.streak_sos_title, R.string.streak_sos_subtitle),
    PERPETUAL("⚙️", R.string.streak_perpetual_title, R.string.streak_perpetual_subtitle),
    ;

    companion object {
        /** `null` below two consecutive days: no warning card. */
        fun of(consecutiveActiveDays: Int): StreakLevel? = when {
            consecutiveActiveDays >= 7 -> PERPETUAL
            consecutiveActiveDays >= 3 -> SOS
            consecutiveActiveDays >= 2 -> SUPPLIES
            else -> null
        }
    }
}

/** Same index all day long, a different one tomorrow. */
fun pickByDay(epochDay: Long, size: Int): Int = Math.floorMod(epochDay, size.toLong()).toInt()
