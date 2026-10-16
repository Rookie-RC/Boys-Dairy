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
    var apiKey: String? = null
        private set
    var system: String? = null
        private set
    var user: String? = null
        private set

    override suspend fun generate(apiKey: String, system: String, user: String): AiResult {
        calls++
        this.apiKey = apiKey
        this.system = system
        this.user = user
        return result
    }
}

class AiAnalysisServiceTest {

    private val now = millisOf(2026, 10, 16, 12, 0)
    private val options = AiOptions(languageTag = "en")

    private var entries: List<Entry> = listOf(testEntry(now - 3_600_000L, note = "PRIVATE-NOTE"))
    private var key: String? = "sk-ant-test-key"
    private val generator = FakeGenerator()

    private fun service() = AiAnalysisService(
        loadEntries = { entries },
        loadApiKey = { key },
        generator = generator,
        clock = { now },
        zone = { TEST_ZONE },
    )

    @Test
    fun noEntriesMeansNoDataAndNothingIsSent() = runBlocking {
        entries = emptyList()
        assertEquals(AiResult.Failure(AiError.NO_DATA), service().analyze(options))
        assertEquals(0, generator.calls)
    }

    @Test
    fun noDataTakesPrecedenceOverAMissingKey() = runBlocking {
        entries = emptyList()
        key = null
        assertEquals(AiResult.Failure(AiError.NO_DATA), service().analyze(options))
    }

    @Test
    fun missingOrBlankKeyMeansNoApiKeyAndNothingIsSent() = runBlocking {
        key = null
        assertEquals(AiResult.Failure(AiError.NO_API_KEY), service().analyze(options))
        key = "   "
        assertEquals(AiResult.Failure(AiError.NO_API_KEY), service().analyze(options))
        assertEquals(0, generator.calls)
    }

    @Test
    fun successPassesKeySystemPromptAndPayloadToTheGenerator() = runBlocking {
        val result = service().analyze(options)

        assertEquals(AiResult.Success("roast", "fake-model"), result)
        assertEquals(1, generator.calls)
        assertEquals("sk-ant-test-key", generator.apiKey)
        assertEquals(AiPromptBuilder.systemPrompt(options), generator.system)
        assertEquals(AiPromptBuilder.userMessage(entries, options, now, TEST_ZONE), generator.user)
    }

    @Test
    fun theKeyIsTrimmedBeforeUse() = runBlocking {
        key = "  sk-ant-padded \n"
        service().analyze(options)
        assertEquals("sk-ant-padded", generator.apiKey)
    }

    @Test
    fun generatorFailuresAreReturnedUnchanged() = runBlocking {
        for (error in AiError.entries) {
            generator.result = AiResult.Failure(error)
            assertEquals(AiResult.Failure(error), service().analyze(options))
        }
    }

    @Test
    fun previewIsNullWithoutEntries() = runBlocking {
        entries = emptyList()
        assertNull(service().previewPayload(options))
    }

    @Test
    fun previewIsExactlyTheUserMessageThatAnalyzeSends() = runBlocking {
        for (includeNotes in listOf(false, true)) {
            val opts = options.copy(includeNotes = includeNotes)
            val preview = service().previewPayload(opts)
            service().analyze(opts)
            assertNotNull(preview)
            assertEquals(preview, generator.user)
        }
    }

    @Test
    fun previewDoesNotNeedAKeyAndNeverCallsTheGenerator() = runBlocking {
        key = null
        assertNotNull(service().previewPayload(options))
        assertEquals(0, generator.calls)
    }

    @Test
    fun notesStayOutOfThePayloadUnlessOptedIn() = runBlocking {
        service().analyze(options.copy(includeNotes = false))
        assertFalse(generator.user!!.contains("PRIVATE-NOTE"))

        service().analyze(options.copy(includeNotes = true))
        assertTrue(generator.user!!.contains("PRIVATE-NOTE"))
    }

    @Test
    fun theSystemPromptFollowsTheOptions() = runBlocking {
        service().analyze(AiOptions(languageTag = "zh-CN", roastLevel = RoastLevel.GENTLE))
        assertTrue(generator.system!!.contains("Simplified Chinese"))
        assertTrue(generator.system!!.contains("GENTLE"))
    }

    @Test
    fun theInjectedClockAndZoneDecideWhatToday() = runBlocking {
        val later = AiAnalysisService(
            loadEntries = { entries },
            loadApiKey = { key },
            generator = generator,
            clock = { now + 3 * 86_400_000L },
            zone = { TEST_ZONE },
        )
        later.analyze(options)
        assertTrue(generator.user!!.contains("Days since the last entry: 3"))
    }
}
