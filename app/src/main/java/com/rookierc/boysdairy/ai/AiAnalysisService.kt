package com.rookierc.boysdairy.ai

import com.rookierc.boysdairy.data.EntryRepository
import com.rookierc.boysdairy.data.SettingsRepository
import com.rookierc.boysdairy.domain.Entry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.ZoneId

/**
 * Orchestrates one analysis: reads the diary, builds the prompt, and calls the model of the
 * selected provider through its [AiTextGenerator]. Nothing is sent unless [analyze] is called (an
 * explicit user action). The prompt is the same for every provider; only the key, the fixed model
 * and effort ([AiProvider.spec]) and the generator differ.
 */
class AiAnalysisService internal constructor(
    private val loadEntries: suspend () -> List<Entry>,
    private val loadProvider: suspend () -> AiProvider,
    private val loadApiKey: suspend (AiProvider) -> String?,
    private val generators: Map<AiProvider, AiTextGenerator>,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) {
    constructor(
        entryRepository: EntryRepository,
        settingsRepository: SettingsRepository,
        apiKeyStore: ApiKeyStore,
        generators: Map<AiProvider, AiTextGenerator>,
        clock: () -> Long = System::currentTimeMillis,
        zone: () -> ZoneId = ZoneId::systemDefault,
    ) : this(
        loadEntries = { entryRepository.snapshot() },
        loadProvider = { settingsRepository.aiProvider.first() },
        loadApiKey = { provider -> apiKeyStore.load(provider) },
        generators = generators,
        clock = clock,
        zone = zone,
    )

    /**
     * Exactly the system prompt and user message [analyze] would send; the app no longer shows it,
     * but tests use it to check what goes out. Null when there are no entries. Independent of the
     * provider and of any key.
     */
    suspend fun previewPayload(options: AiOptions): AiPreview? {
        val entries = loadEntries()
        if (entries.isEmpty()) return null
        return AiPreview(
            system = AiPromptBuilder.systemPrompt(options),
            user = AiPromptBuilder.userMessage(entries, options, clock(), zone()),
        )
    }

    /** NO_DATA without entries, NO_API_KEY without a key for the selected provider; otherwise the model's answer. */
    suspend fun analyze(options: AiOptions): AiResult {
        val entries = loadEntries()
        if (entries.isEmpty()) return AiResult.Failure(AiError.NO_DATA)
        val provider = loadProvider()
        val apiKey = loadApiKey(provider)?.trim()
        if (apiKey.isNullOrEmpty()) return AiResult.Failure(AiError.NO_API_KEY)
        val generator = generators[provider] ?: return AiResult.Failure(AiError.UNKNOWN)
        val spec = provider.spec

        return generator.generate(
            AiRequest(
                apiKey = apiKey,
                model = spec.model,
                effort = spec.effort,
                system = AiPromptBuilder.systemPrompt(options),
                user = AiPromptBuilder.userMessage(entries, options, clock(), zone()),
            ),
        )
    }
}

/**
 * The selected provider together with whether it has a key, re-emitted whenever the provider or its
 * key changes. Shared by the Settings and AI screens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun aiSelectionFlow(settings: SettingsRepository, apiKeyStore: ApiKeyStore): Flow<AiSelection> =
    settings.aiProvider.flatMapLatest { provider ->
        apiKeyStore.hasKey(provider).map { hasKey -> AiSelection(provider, hasKey) }
    }
