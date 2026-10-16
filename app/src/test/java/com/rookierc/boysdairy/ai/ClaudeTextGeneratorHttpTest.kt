package com.rookierc.boysdairy.ai

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Drives the real Anthropic SDK client against a loopback [MockWebServer] so the exact request the
 * app would send (path, headers, JSON body) and the handling of each response kind are verified
 * without touching the network.
 */
class ClaudeTextGeneratorHttpTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun generator(useRefusalFallback: Boolean = true) =
        ClaudeTextGenerator(useRefusalFallback = useRefusalFallback) { apiKey ->
            AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(server.url("/").toString().trimEnd('/'))
                .maxRetries(0)
                .timeout(Duration.ofSeconds(10))
                .build()
        }

    private fun reply(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun call(useRefusalFallback: Boolean = true): AiResult = runBlocking {
        generator(useRefusalFallback).generate(apiKey = KEY, system = "SYSTEM-PROMPT", user = "USER-MESSAGE")
    }

    private fun takeRequest(): RecordedRequest =
        server.takeRequest(5, TimeUnit.SECONDS) ?: throw AssertionError("no request reached the server")

    private fun errorBody(type: String) =
        """{"type":"error","error":{"type":"$type","message":"nope"}}"""

    // ---- request ------------------------------------------------------------------------------

    @Test
    fun sendsTheExpectedRequest() {
        reply(200, successBody(listOf("ok")))
        call()

        val request = takeRequest()
        assertEquals("/v1/messages", request.requestUrl!!.encodedPath)
        assertEquals(KEY, request.getHeader("x-api-key"))

        val json = JSONObject(request.body.readUtf8())
        assertEquals(ClaudeConfig.MODEL, json.getString("model"))
        assertEquals("claude-opus-5-5", json.getString("model"))
        assertEquals(16_000, json.getInt("max_tokens"))
        assertEquals("SYSTEM-PROMPT", json.getString("system"))
        assertEquals("medium", json.getJSONObject("output_config").getString("effort"))

        val messages = json.getJSONArray("messages")
        assertEquals("exactly one user turn, no assistant prefill", 1, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        assertTrue(messages.getJSONObject(0).toString().contains("USER-MESSAGE"))
    }

    @Test
    fun doesNotSendSamplingOrThinkingParameters() {
        reply(200, successBody(listOf("ok")))
        call()

        val json = JSONObject(takeRequest().body.readUtf8())
        for (forbidden in listOf("thinking", "temperature", "top_p", "top_k")) {
            assertFalse("$forbidden must not be sent", json.has(forbidden))
        }
        assertFalse(json.optBoolean("stream", false))
    }

    @Test
    fun requestsServerSideFallbackByDefault() {
        reply(200, successBody(listOf("ok")))
        call()

        val request = takeRequest()
        assertTrue(request.getHeader("anthropic-beta").orEmpty().contains("server-side-fallback-2026-07-01"))
        assertEquals("default", JSONObject(request.body.readUtf8()).getString("fallbacks"))
    }

    @Test
    fun fallbackCanBeSwitchedOff() {
        reply(200, successBody(listOf("ok")))
        call(useRefusalFallback = false)

        val request = takeRequest()
        assertFalse(request.getHeader("anthropic-beta").orEmpty().contains("server-side-fallback"))
        assertFalse(JSONObject(request.body.readUtf8()).has("fallbacks"))
    }

    @Test
    fun theApiKeyOnlyTravelsInTheHeader() {
        reply(200, successBody(listOf("ok")))
        call()

        assertFalse(takeRequest().body.readUtf8().contains(KEY))
    }

    // ---- responses ----------------------------------------------------------------------------

    @Test
    fun concatenatesTextBlocksAndIgnoresThinkingBlocks() {
        reply(200, successBody(listOf("Hello", " world"), withThinking = true))

        assertEquals(AiResult.Success("Hello world", "claude-opus-5-5"), call())
    }

    @Test
    fun reportsTheModelThatActuallyAnswered() {
        reply(200, successBody(listOf("served by the fallback"), model = "claude-fallback-x"))

        assertEquals(AiResult.Success("served by the fallback", "claude-fallback-x"), call())
    }

    @Test
    fun refusalStopReasonIsRefused() {
        reply(200, successBody(listOf("I can't help with that."), stopReason = "refusal"))

        assertEquals(AiResult.Failure(AiError.REFUSED), call())
    }

    @Test
    fun emptyAnswerIsUnknown() {
        reply(200, successBody(emptyList()))

        assertEquals(AiResult.Failure(AiError.UNKNOWN), call())
    }

    @Test
    fun httpErrorsAreMapped() {
        val cases = listOf(
            Triple(401, "authentication_error", AiError.INVALID_API_KEY),
            Triple(403, "permission_error", AiError.INVALID_API_KEY),
            Triple(429, "rate_limit_error", AiError.RATE_LIMITED),
            Triple(529, "overloaded_error", AiError.OVERLOADED),
            Triple(500, "api_error", AiError.SERVER),
            Triple(503, "api_error", AiError.SERVER),
            Triple(400, "invalid_request_error", AiError.UNKNOWN),
            Triple(404, "not_found_error", AiError.UNKNOWN),
        )
        for ((code, type, expected) in cases) {
            reply(code, errorBody(type))
            assertEquals("HTTP $code", AiResult.Failure(expected), call())
        }
    }

    @Test
    fun connectionFailureIsNetwork() {
        server.shutdown() // nothing is listening any more

        assertEquals(AiResult.Failure(AiError.NETWORK), call())
    }

    @Test
    fun garbageResponseBodyIsNotACrash() {
        reply(200, "<html>proxy login page</html>")

        assertTrue(call() is AiResult.Failure)
    }

    private companion object {
        const val KEY = "sk-ant-test-0123456789"

        fun successBody(
            texts: List<String>,
            model: String = "claude-opus-5-5",
            stopReason: String = "end_turn",
            withThinking: Boolean = false,
        ): String {
            val blocks = JSONArray()
            if (withThinking) {
                blocks.put(JSONObject().put("type", "thinking").put("thinking", "reasoning...").put("signature", "sig"))
            }
            texts.forEach { blocks.put(JSONObject().put("type", "text").put("text", it)) }
            return JSONObject()
                .put("id", "msg_test")
                .put("type", "message")
                .put("role", "assistant")
                .put("model", model)
                .put("content", blocks)
                .put("stop_reason", stopReason)
                .put("stop_sequence", JSONObject.NULL)
                .put("usage", JSONObject().put("input_tokens", 10).put("output_tokens", 20))
                .toString()
        }
    }
}
