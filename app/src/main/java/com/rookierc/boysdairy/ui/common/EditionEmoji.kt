package com.rookierc.boysdairy.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.rookierc.boysdairy.data.Edition

/**
 * Every decorative emoji (set in code, not in strings.xml) that differs between the boy and girl
 * editions; everything else is shared. Callers outside composition use [of] with the edition they
 * captured, composables read [current].
 */
enum class EditionEmoji(val boy: String, val girl: String) {
    /** Home screen title. */
    DIARY("📒", "📔"),

    /** History tab and title, "new entry" form title, calendar day sheet. */
    TAPE("📼", "💌"),

    /** [com.rookierc.boysdairy.domain.EntryType.SOLO]. */
    SOLO("🕹️", "🦋"),

    /** "Pick a mode" card of the entry form. */
    MODE_PICKER("🎮", "🎀"),

    /** Calm / abstinence: fresh cooldown, "longest gap", gap achievement, empty history. */
    MEDITATE("🧘", "🧘‍♀️"),

    /** Cooldown tier "reloading" (3–6 days). */
    RELOADING("💪", "💅"),

    /** Cooldown tier "monk" (30+ days). */
    MONK("☯️", "🧚‍♀️"),

    /** Streak warning "mind your supplies". */
    SUPPLIES("🥤", "☕"),

    /** Pleasure: home stats, report overview, peak-pleasure achievement. */
    PLEASURE("🎆", "✨"),

    /** Early-bird achievement (a rooster is a boy). */
    EARLY_BIRD("🐓", "🐤"),

    /** Marathon achievement. */
    RUNNER("🏃", "🏃‍♀️"),
    ;

    fun of(edition: Edition): String = when (edition) {
        Edition.BOY -> boy
        Edition.GIRL -> girl
    }

    /** The form for the edition of the current composition ([LocalEdition]). */
    val current: String
        @Composable
        @ReadOnlyComposable
        get() = of(LocalEdition.current)
}
