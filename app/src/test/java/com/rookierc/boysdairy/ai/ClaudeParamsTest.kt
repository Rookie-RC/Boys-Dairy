package com.rookierc.boysdairy.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The request the SDK is asked to send, checked without any HTTP: model, effort and fallback rules. */
class ClaudeParamsTest {

    private fun request(model: String, effort: AiEffort = AiEffort.HIGH) =
        AiRequest(apiKey = "k", model = model, effort = effort, system = "SYS", user = "USR")

    private fun betas(model: String, useFallback: Boolean = true): List<String> =
        ClaudeTextGenerator.buildParams(request(model), useFallback)
            .betas().orElse(emptyList()).map { it.asString() }

    private fun fallbackBody(model: String, useFallback: Boolean = true) =
        ClaudeTextGenerator.buildParams(request(model), useFallback)._additionalBodyProperties()["fallbacks"]

    @Test
    fun fallbackModelsAreExactlyOpus() {
        assertEquals(setOf("claude-opus-5-5"), ClaudeConfig.FALLBACK_MODELS)
    }

    @Test
    fun theFallbackIsSentForOpus() {
        assertEquals(listOf(ClaudeConfig.FALLBACK_BETA), betas("claude-opus-5-5"))
        assertEquals("default", fallbackBody("claude-opus-5-5")!!.asString().get())
    }

    @Test
    fun theFallbackIsNotSentForHaikuWhichAnswersItWithHttp400() {
        assertTrue(betas("claude-haiku-5-5").isEmpty())
        assertNull(fallbackBody("claude-haiku-5-5"))
    }

    @Test
    fun theFallbackIsNotSentForAnyOtherModel() {
        assertTrue(betas("claude-sonnet-5-5").isEmpty())
        assertTrue(betas("claude-opus-5-5-preview").isEmpty())
        assertNull(fallbackBody("something-else"))
    }

    @Test
    fun theFallbackCanBeSwitchedOffEvenForOpus() {
        assertTrue(betas("claude-opus-5-5", useFallback = false).isEmpty())
        assertNull(fallbackBody("claude-opus-5-5", useFallback = false))
    }

    @Test
    fun modelAndEffortComeFromTheRequest() {
        val params = ClaudeTextGenerator.buildParams(request("claude-custom", AiEffort.MAX))

        assertEquals("claude-custom", params.model().asString())
        assertEquals("max", params.outputConfig().get().effort().get().asString())
    }

    @Test
    fun everyEffortMapsToItsWireName() {
        for (effort in AiEffort.entries) {
            val params = ClaudeTextGenerator.buildParams(request("claude-haiku-5-5", effort))
            assertEquals(effort.wire, params.outputConfig().get().effort().get().asString())
        }
    }

    @Test
    fun neverSendsAThinkingParameter() {
        // Sampling parameters are covered on the wire in ClaudeTextGeneratorHttpTest.
        val params = ClaudeTextGenerator.buildParams(request("claude-haiku-5-5"))

        assertFalse(params.thinking().isPresent)
        assertEquals(ClaudeConfig.MAX_TOKENS, params.maxTokens())
    }
}
