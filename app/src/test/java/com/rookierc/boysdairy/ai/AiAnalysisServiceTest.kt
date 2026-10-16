package com.rookierc.boysdairy.ai

import com.rookierc.boysdairy.TEST_ZONE
import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.millisOf
import com.rookierc.boysdairy.testEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeGenerator(var result: AiResult = AiResult.Success("roast", "fake-model")) : AiTextGenerator {
    var calls = 0
        private set
    var request: AiRequest? = null
        private set

    val apiKey: String? get() = request?.apiKey
    val system: String? get() = request?.system
    val user: String? get() = request?.user

    override suspend fun generate(request: AiRequest): AiResult {
        calls++
        this.request = request
        return result
    }
}

class AiAnalysisServiceTest {

    private val now = millisOf(2026, 10, 16, 12, 0)
    private val options = AiOptions(languageTag = "en")

    private var entries: List<Entry> = listOf(testEntry(now - 3_600_000L, note = "PRIVATE-NOTE"))
    private var provider = AiProvider.DEEPSEEK
    private val keys = mutableMapOf<AiProvider, String?>(
        AiProvider.DEEPSEEK to "sk-deepseek-test-key",
        AiProvider.CLAUDE to "sk-ant-test-key",
    )
    private val deepSeek = FakeGenerator(AiResult.Success("deepseek roast", "deepseek-answered"))
    private val claude = FakeGenerator(AiResult.Success("claude roast", "claude-answered"))

    /** The generator of the provider that is selected right now (most tests only care about that one). */
    private val generator get() = if (provider == AiProvider.DEEPSEEK) deepSeek else claude

    private fun service(clock: () -> Long = { now }) = AiAnalysisService(
        loadEntries = { entries },
        loadProvider = { provider },
        loadApiKey = { keys[it] },
        generators = mapOf(AiProvider.DEEPSEEK to deepSeek, AiProvider.CLAUDE to claude),
        clock = clock,
        zone = { TEST_ZONE },
    )

    // ---- gating -------------------------------------------------------------------------------

    @Test
    fun noEntriesMeansNoDataAndNothingIsSent() = runBlocking {
        entries = emptyList()
        assertEquals(AiResult.Failure(AiError.NO_DATA), service().analyze(options))
        assertEquals(0, deepSeek.calls + claude.calls)
    }

    @Test
    fun noDataTakesPrecedenceOverAMissingKey() = runBlocking {
        entries = emptyList()
        keys[provider] = null
        assertEquals(AiResult.Failure(AiError.NO_DATA), service().analyze(options))
    }

    @Test
    fun missingOrBlankKeyMeansNoApiKeyAndNothingIsSent() = runBlocking {
        keys[provider] = null
        assertEquals(AiResult.Failure(AiError.NO_API_KEY), service().analyze(options))
        keys[provider] = "   "
        assertEquals(AiResult.Failure(AiError.NO_API_KEY), service().analyze(options))
        assertEquals(0, deepSeek.calls + claude.calls)
    }

    @Test
    fun theOtherProvidersKeyDoesNotSatisfyTheSelectedOne() = runBlocking {
        for (selected in AiProvider.entries) {
            provider = selected
            keys[selected] = null // the other provider still has a key
            assertEquals(AiResult.Failure(AiError.NO_API_KEY), service().analyze(options))
        }
        assertEquals(0, deepSeek.calls + claude.calls)
    }

    // ---- routing ------------------------------------------------------------------------------

    @Test
    fun deepSeekSelectedUsesTheDeepSeekGeneratorAndKey() = runBlocking {
        provider = AiProvider.DEEPSEEK

        assertEquals(AiResult.Success("deepseek roast", "deepseek-answered"), service().analyze(options))
        assertEquals(1, deepSeek.calls)
        assertEquals(0, claude.calls)
        assertEquals("sk-deepseek-test-key", deepSeek.apiKey)
    }

    @Test
    fun claudeSelectedUsesTheClaudeGeneratorAndKey() = runBlocking {
        provider = AiProvider.CLAUDE

        assertEquals(AiResult.Success("claude roast", "claude-answered"), service().analyze(options))
        assertEquals(1, claude.calls)
        assertEquals(0, deepSeek.calls)
        assertEquals("sk-ant-test-key", claude.apiKey)
    }

    @Test
    fun theSelectionIsReadOnEveryAnalysis() = runBlocking {
        val service = service()
        provider = AiProvider.DEEPSEEK
        service.analyze(options)
        provider = AiProvider.CLAUDE
        service.analyze(options)

        assertEquals(1, deepSeek.calls)
        assertEquals(1, claude.calls)
    }

    @Test
    fun eachProviderIsCalledWithItsFixedModelAndEffort() = runBlocking {
        provider = AiProvider.DEEPSEEK
        service().analyze(options)
        assertEquals("deepseek-v4-flash", deepSeek.request!!.model)
        assertEquals(AiEffort.HIGH, deepSeek.request!!.effort)

        provider = AiProvider.CLAUDE
        service().analyze(options)
        assertEquals("claude-haiku-5-5", claude.request!!.model)
        assertEquals(AiEffort.HIGH, claude.request!!.effort)
    }

    @Test
    fun aSelectedProviderWithoutAGeneratorIsAnUnknownFailureNotACrash() = runBlocking {
        val service = AiAnalysisService(
            loadEntries = { entries },
            loadProvider = { AiProvider.CLAUDE },
            loadApiKey = { keys[it] },
            generators = mapOf(AiProvider.DEEPSEEK to deepSeek),
            clock = { now },
            zone = { TEST_ZONE },
        )

        assertEquals(AiResult.Failure(AiError.UNKNOWN), service.analyze(options))
    }

    // ---- what is sent -------------------------------------------------------------------------

    @Test
    fun successPassesKeySystemPromptAndPayloadToTheGenerator() = runBlocking {
        val result = service().analyze(options)

        assertEquals(AiResult.Success("deepseek roast", "deepseek-answered"), result)
        assertEquals(1, generator.calls)
        assertEquals("sk-deepseek-test-key", generator.apiKey)
        assertEquals(AiPromptBuilder.systemPrompt(options), generator.system)
        assertEquals(AiPromptBuilder.userMessage(entries, options, now, TEST_ZONE), generator.user)
    }

    @Test
    fun thePromptsAreTheSameForEveryProvider() = runBlocking {
        provider = AiProvider.DEEPSEEK
        service().analyze(options)
        provider = AiProvider.CLAUDE
        service().analyze(options)

        assertEquals(deepSeek.system, claude.system)
        assertEquals(deepSeek.user, claude.user)
    }

    @Test
    fun theKeyIsTrimmedBeforeUse() = runBlocking {
        keys[provider] = "  sk-padded \n"
        service().analyze(options)
        assertEquals("sk-padded", generator.apiKey)
    }

    @Test
    fun generatorFailuresAreReturnedUnchanged() = runBlocking {
        for (error in AiError.entries) {
            generator.result = AiResult.Failure(error)
            assertEquals(AiResult.Failure(error), service().analyze(options))
        }
    }

    // ---- preview ------------------------------------------------------------------------------

    @Test
    fun previewIsNullWithoutEntries() = runBlocking {
        entries = emptyList()
        assertNull(service().previewPayload(options))
    }

    @Test
    fun previewIsExactlyTheSystemPromptAndUserMessageThatAnalyzeSends() = runBlocking {
        for (includeNotes in listOf(false, true)) {
            val opts = options.copy(includeNotes = includeNotes)
            val preview = service().previewPayload(opts)
            service().analyze(opts)
            assertNotNull(preview)
            assertEquals(generator.system, preview!!.system)
            assertEquals(generator.user, preview.user)
        }
    }

    @Test
    fun previewShowsTheSystemPromptTheOptionsProduce() = runBlocking {
        val opts = AiOptions(languageTag = "zh-CN", roastLevel = RoastLevel.GENTLE)
        val preview = service().previewPayload(opts)!!

        assertEquals(AiPromptBuilder.systemPrompt(opts), preview.system)
        assertTrue(preview.system.contains("Simplified Chinese"))
        assertTrue(preview.system.contains("GENTLE"))
    }

    @Test
    fun previewDoesNotDependOnTheProviderOrNeedAKey() = runBlocking {
        keys[AiProvider.DEEPSEEK] = null
        keys[AiProvider.CLAUDE] = null
        provider = AiProvider.DEEPSEEK
        val forDeepSeek = service().previewPayload(options)
        provider = AiProvider.CLAUDE
        val forClaude = service().previewPayload(options)

        assertNotNull(forDeepSeek)
        assertEquals(forDeepSeek, forClaude)
        assertEquals(0, deepSeek.calls + claude.calls)
    }

    @Test
    fun notesStayOutOfThePayloadUnlessOptedIn() = runBlocking {
        service().analyze(options.copy(includeNotes = false))
        assertFalse(generator.user!!.contains("PRIVATE-NOTE"))
        assertFalse(service().previewPayload(options.copy(includeNotes = false))!!.user.contains("PRIVATE-NOTE"))

        service().analyze(options.copy(includeNotes = true))
        assertTrue(generator.user!!.contains("PRIVATE-NOTE"))
        assertTrue(service().previewPayload(options.copy(includeNotes = true))!!.user.contains("PRIVATE-NOTE"))
    }

    @Test
    fun theSystemPromptFollowsTheOptions() = runBlocking {
        service().analyze(AiOptions(languageTag = "zh-CN", roastLevel = RoastLevel.GENTLE))
        assertTrue(generator.system!!.contains("Simplified Chinese"))
        assertTrue(generator.system!!.contains("GENTLE"))
    }

    @Test
    fun theInjectedClockAndZoneDecideWhatToday() = runBlocking {
        service(clock = { now + 3 * 86_400_000L }).analyze(options)
        assertTrue(generator.user!!.contains("Days since the last entry: 3"))
    }
}
