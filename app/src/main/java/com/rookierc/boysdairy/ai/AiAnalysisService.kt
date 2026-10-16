package com.rookierc.boysdairy.ai

import com.rookierc.boysdairy.data.EntryRepository
import com.rookierc.boysdairy.domain.Entry
import java.time.ZoneId

/**
 * Orchestrates one analysis: reads the diary, builds the prompt, and calls the model through an
 * [AiTextGenerator]. Nothing is sent unless [analyze] is called (an explicit user action).
 */
class AiAnalysisService internal constructor(
    private val loadEntries: suspend () -> List<Entry>,
    private val loadApiKey: suspend () -> String?,
    private val generator: AiTextGenerator,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) {
    constructor(
        entryRepository: EntryRepository,
        apiKeyStore: ApiKeyStore,
        generator: AiTextGenerator,
        clock: () -> Long = System::currentTimeMillis,
        zone: () -> ZoneId = ZoneId::systemDefault,
    ) : this(
        loadEntries = { entryRepository.snapshot() },
        loadApiKey = { apiKeyStore.load() },
        generator = generator,
        clock = clock,
        zone = zone,
    )

    /**
     * Exactly the user-message text [analyze] would send, for a transparency preview.
     * Null when there are no entries.
     */
    suspend fun previewPayload(options: AiOptions): String? {
        val entries = loadEntries()
        if (entries.isEmpty()) return null
        return AiPromptBuilder.userMessage(entries, options, clock(), zone())
    }

    /** NO_DATA without entries, NO_API_KEY without a stored key; otherwise the model's answer. */
    suspend fun analyze(options: AiOptions): AiResult {
        val entries = loadEntries()
        if (entries.isEmpty()) return AiResult.Failure(AiError.NO_DATA)
        val apiKey = loadApiKey()?.trim()
        if (apiKey.isNullOrEmpty()) return AiResult.Failure(AiError.NO_API_KEY)

        return generator.generate(
            apiKey = apiKey,
            system = AiPromptBuilder.systemPrompt(options),
            user = AiPromptBuilder.userMessage(entries, options, clock(), zone()),
        )
    }
}
