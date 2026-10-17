package com.rookierc.boysdairy.ui.common

import com.rookierc.boysdairy.R

/**
 * Girl-edition copy: key = the (boy edition) resource id used in code, value = its `<name>_girl`
 * variant. Anything not listed is shared by both editions. Resolved by [editionStringRes] and
 * friends, so code always names the base id. `R.string.app_name` (launcher disguise) and the
 * `lock_*` strings (the lock screen stays neutral) never get a variant; GirlVariantsTest checks.
 */
internal object GirlVariants {
    val strings: Map<Int, Int> = mapOf(
        // Entry types
        R.string.entry_type_solo_hint to R.string.entry_type_solo_hint_girl,
        R.string.entry_type_partner_hint to R.string.entry_type_partner_hint_girl,
        // Reactive labels (duration / pleasure)
        R.string.duration_tier_flash to R.string.duration_tier_flash_girl,
        R.string.duration_tier_standard to R.string.duration_tier_standard_girl,
        R.string.duration_tier_endurance to R.string.duration_tier_endurance_girl,
        R.string.duration_tier_marathon to R.string.duration_tier_marathon_girl,
        R.string.pleasure_tier_great to R.string.pleasure_tier_great_girl,
        R.string.pleasure_tier_peak to R.string.pleasure_tier_peak_girl,
        // Home
        R.string.home_title to R.string.home_title_girl,
        R.string.home_subtitle to R.string.home_subtitle_girl,
        R.string.home_cooldown_label to R.string.home_cooldown_label_girl,
        R.string.home_last_entry to R.string.home_last_entry_girl,
        // Cooldown tiers
        R.string.cooldown_none_title to R.string.cooldown_none_title_girl,
        R.string.cooldown_fresh_title to R.string.cooldown_fresh_title_girl,
        R.string.cooldown_reloading_title to R.string.cooldown_reloading_title_girl,
        R.string.cooldown_ascetic_title to R.string.cooldown_ascetic_title_girl,
        R.string.cooldown_monk_title to R.string.cooldown_monk_title_girl,
        // Streak warning
        R.string.streak_supplies_title to R.string.streak_supplies_title_girl,
        R.string.streak_supplies_subtitle to R.string.streak_supplies_subtitle_girl,
        R.string.streak_sos_title to R.string.streak_sos_title_girl,
        R.string.streak_perpetual_title to R.string.streak_perpetual_title_girl,
        // Add entry
        R.string.add_mood_after_label to R.string.add_mood_after_label_girl,
        R.string.add_pick_time_title to R.string.add_pick_time_title_girl,
        // Insights
        R.string.achievement_gap_30_desc to R.string.achievement_gap_30_desc_girl,
        R.string.achievement_marathon_name to R.string.achievement_marathon_name_girl,
        R.string.achievement_peak_pleasure_name to R.string.achievement_peak_pleasure_name_girl,
        R.string.achievement_sage_mode_name to R.string.achievement_sage_mode_name_girl,
        R.string.achievement_sage_mode_desc to R.string.achievement_sage_mode_desc_girl,
        // Insights round 3
        R.string.insights_range_empty to R.string.insights_range_empty_girl,
    )

    val arrays: Map<Int, Int> = mapOf(
        // Cooldown tiers
        R.array.cooldown_none_subtitles to R.array.cooldown_none_subtitles_girl,
        R.array.cooldown_fresh_subtitles to R.array.cooldown_fresh_subtitles_girl,
        R.array.cooldown_recovering_subtitles to R.array.cooldown_recovering_subtitles_girl,
        R.array.cooldown_reloading_subtitles to R.array.cooldown_reloading_subtitles_girl,
        R.array.cooldown_ascetic_subtitles to R.array.cooldown_ascetic_subtitles_girl,
        R.array.cooldown_serene_subtitles to R.array.cooldown_serene_subtitles_girl,
        R.array.cooldown_monk_subtitles to R.array.cooldown_monk_subtitles_girl,
        // Daily tip
        R.array.daily_tips to R.array.daily_tips_girl,
        // Add entry
        R.array.save_toasts to R.array.save_toasts_girl,
        // AI roast
        R.array.ai_loading_messages to R.array.ai_loading_messages_girl,
    )

    /** No quantity string carries boy-only wording yet; add `R.plurals.x to R.plurals.x_girl` here when one does. */
    val plurals: Map<Int, Int> = mapOf()
}
