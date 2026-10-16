package com.rookierc.boysdairy.ui.common

import androidx.annotation.StringRes
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.ai.AiProvider

/** Display helpers shared by the Settings and AI screens. */

@StringRes
fun AiProvider.nameRes(): Int = when (this) {
    AiProvider.DEEPSEEK -> R.string.ai_provider_deepseek
    AiProvider.CLAUDE -> R.string.ai_provider_claude
}

@StringRes
fun AiProvider.taglineRes(): Int = when (this) {
    AiProvider.DEEPSEEK -> R.string.ai_provider_deepseek_tagline
    AiProvider.CLAUDE -> R.string.ai_provider_claude_tagline
}

fun AiProvider.emoji(): String = when (this) {
    AiProvider.DEEPSEEK -> "🐳"
    AiProvider.CLAUDE -> "🧡"
}
