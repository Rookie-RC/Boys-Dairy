package com.rookierc.boysdairy.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderTest {

    @Test
    fun providerIdsAreStablePersistenceKeys() {
        assertEquals("deepseek", AiProvider.DEEPSEEK.id)
        assertEquals("claude", AiProvider.CLAUDE.id)
        assertEquals(setOf("deepseek", "claude"), AiProvider.entries.map { it.id }.toSet())
    }

    @Test
    fun deepSeekIsTheDefaultProvider() {
        assertEquals(AiProvider.DEEPSEEK, AiProvider.DEFAULT)
    }

    @Test
    fun fromIdFindsProvidersAndRejectsUnknownIds() {
        assertEquals(AiProvider.DEEPSEEK, AiProvider.fromId("deepseek"))
        assertEquals(AiProvider.CLAUDE, AiProvider.fromId("claude"))
        assertNull(AiProvider.fromId("openai"))
        assertNull(AiProvider.fromId(""))
        assertNull(AiProvider.fromId(null))
    }

    @Test
    fun effortWireNamesMatchTheApis() {
        assertEquals(
            listOf("low", "medium", "high", "max"),
            AiEffort.entries.map { it.wire },
        )
    }

    @Test
    fun deepSeekSpecIsFixed() {
        val spec = AiProvider.DEEPSEEK.spec
        assertEquals("deepseek-v4-flash", spec.model)
        assertEquals(AiEffort.HIGH, spec.effort)
        assertEquals("api.deepseek.com", spec.endpointHost)
    }

    @Test
    fun claudeSpecIsFixed() {
        val spec = AiProvider.CLAUDE.spec
        assertEquals("claude-haiku-5-5", spec.model)
        assertEquals(AiEffort.HIGH, spec.effort)
        assertEquals("api.anthropic.com", spec.endpointHost)
    }

    // ---- AiRequest ----------------------------------------------------------------------------

    @Test
    fun theRequestToStringHidesTheKeyAndThePrompts() {
        val request = AiRequest("sk-secret-key", "m", AiEffort.LOW, "SYSTEM-TEXT", "DIARY-TEXT")
        val text = request.toString()

        assertFalse(text.contains("sk-secret-key"))
        assertFalse(text.contains("SYSTEM-TEXT"))
        assertFalse(text.contains("DIARY-TEXT"))
        assertTrue(text.contains("m"))
        assertTrue(text.contains("LOW"))
    }
}
