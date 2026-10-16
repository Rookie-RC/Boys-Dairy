package com.rookierc.boysdairy.ai

/**
 * The services that can write the commentary. [id] is the persistence key (the saved choice and
 * the encrypted API-key slots), so it must never change once shipped.
 */
enum class AiProvider(val id: String) {
    DEEPSEEK("deepseek"),
    CLAUDE("claude"),
    ;

    /** What this provider is called with. Fixed in code: the user only supplies an API key. */
    val spec: AiProviderSpec
        get() = when (this) {
            DEEPSEEK -> AiProviderSpec(
                model = "deepseek-v4-flash",
                effort = AiEffort.HIGH,
                endpointHost = "api.deepseek.com",
            )
            CLAUDE -> AiProviderSpec(
                model = "claude-haiku-5-5",
                effort = AiEffort.HIGH,
                endpointHost = "api.anthropic.com",
            )
        }

    companion object {
        val DEFAULT = DEEPSEEK

        /** The provider stored under [id], or null for an unknown / missing id. */
        fun fromId(id: String?): AiProvider? = entries.firstOrNull { it.id == id }
    }
}

/** How hard the model should think. [wire] is the value the HTTP APIs expect. */
enum class AiEffort(val wire: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    MAX("max"),
}

/**
 * The fixed request settings of a provider.
 *
 * @property endpointHost host the request goes to; only used to tell the user where data is sent.
 */
data class AiProviderSpec(
    val model: String,
    val effort: AiEffort,
    val endpointHost: String,
)

/** The provider the next analysis will use, and whether its API key is present. */
data class AiSelection(val provider: AiProvider, val hasKey: Boolean)
