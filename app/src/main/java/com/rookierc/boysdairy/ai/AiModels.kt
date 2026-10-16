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
    INSUFFICIENT_BALANCE,

    /** The provider rejected the request itself (HTTP 400 / 404 / 422); with the model fixed, usually an account problem. */
    BAD_REQUEST,
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

/**
 * Everything one model call needs. The prompts are provider-independent; [model] and [effort]
 * are the selected provider's fixed values ([AiProvider.spec]).
 */
data class AiRequest(
    val apiKey: String,
    val model: String,
    val effort: AiEffort,
    val system: String,
    val user: String,
) {
    /** Never prints the key, and not the prompts either (they hold the user's diary). */
    override fun toString(): String =
        "AiRequest(model=$model, effort=$effort, apiKey=<redacted>, " +
            "system=<${system.length} chars>, user=<${user.length} chars>)"
}

/** Both prompts exactly as [AiAnalysisService.analyze] would send them, for the transparency preview. */
data class AiPreview(val system: String, val user: String)

/** Abstraction over the model call so [AiAnalysisService] can be unit-tested with a fake. */
interface AiTextGenerator {
    suspend fun generate(request: AiRequest): AiResult
}
