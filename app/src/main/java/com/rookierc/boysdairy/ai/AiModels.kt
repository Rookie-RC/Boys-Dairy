package com.rookierc.boysdairy.ai

enum class RoastLevel { GENTLE, SPICY }

/**
 * @property includeNotes whether the user's free-text notes are sent. Off unless the user opts in.
 * @property languageTag BCP-47 tag of the language the commentary should be written in (e.g. "zh-CN", "en").
 */
data class AiOptions(
    val includeNotes: Boolean = false,
    val languageTag: String,
    val roastLevel: RoastLevel = RoastLevel.SPICY,
)

/** Why an analysis failed; the UI maps each value to a localized message. */
enum class AiError {
    NO_API_KEY,
    NO_DATA,
    INVALID_API_KEY,
    RATE_LIMITED,
    OVERLOADED,
    NETWORK,
    REFUSED,
    SERVER,
    UNKNOWN,
}

sealed interface AiResult {
    /** [model] is the model id that produced [text] (the response's model, which can differ from the requested one when a fallback served it). */
    data class Success(val text: String, val model: String) : AiResult
    data class Failure(val error: AiError) : AiResult
}

/** Abstraction over the model call so [AiAnalysisService] can be unit-tested with a fake. */
interface AiTextGenerator {
    suspend fun generate(apiKey: String, system: String, user: String): AiResult
}
