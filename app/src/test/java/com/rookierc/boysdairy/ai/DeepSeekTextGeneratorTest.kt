package com.rookierc.boysdairy.ai

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
import java.util.concurrent.TimeUnit

/**
 * Drives the real [DeepSeekTextGenerator] against a loopback [MockWebServer] so the exact request
 * the app would send (path, headers, JSON body) and the handling of each response kind are
 * verified without touching the network.
 */
class DeepSeekTextGeneratorTest {

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

    private fun baseUrl() = server.url("/").toString().trimEnd('/')

    private fun request(effort: AiEffort = AiEffort.HIGH, model: String = "deepseek-v4-flash") =
        AiRequest(apiKey = KEY, model = model, effort = effort, system = "SYSTEM-PROMPT", user = "USER-MESSAGE")

    private fun call(request: AiRequest = request(), baseUrl: String = baseUrl()): AiResult = runBlocking {
        DeepSeekTextGenerator(baseUrl = baseUrl).generate(request)
    }

    private fun reply(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun takeRequest(): RecordedRequest =
        server.takeRequest(5, TimeUnit.SECONDS) ?: throw AssertionError("no request reached the server")

    private fun errorBody(message: String = "nope") =
        """{"error":{"message":"$message","type":"invalid_request_error"}}"""

    // ---- request ------------------------------------------------------------------------------

    @Test
    fun sendsTheExpectedRequest() {
        reply(200, successBody("ok"))
        call()

        val recorded = takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/chat/completions", recorded.requestUrl!!.encodedPath)
        assertEquals("Bearer $KEY", recorded.getHeader("Authorization"))
        assertTrue(recorded.getHeader("Content-Type").orEmpty().startsWith("application/json"))

        val json = JSONObject(recorded.body.readUtf8())
        assertEquals("deepseek-v4-flash", json.getString("model"))
        assertEquals("enabled", json.getJSONObject("thinking").getString("type"))
        assertEquals("high", json.getString("reasoning_effort"))
        assertEquals(32_000, json.getInt("max_tokens"))
        assertFalse(json.getBoolean("stream"))

        val messages = json.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("SYSTEM-PROMPT", messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("USER-MESSAGE", messages.getJSONObject(1).getString("content"))
    }

    @Test
    fun modelAndEffortComeFromTheRequest() {
        reply(200, successBody("ok"))
        reply(200, successBody("ok"))
        call(request(effort = AiEffort.MAX, model = "deepseek-custom"))
        call(request(effort = AiEffort.LOW))

        val first = JSONObject(takeRequest().body.readUtf8())
        assertEquals("deepseek-custom", first.getString("model"))
        assertEquals("max", first.getString("reasoning_effort"))
        assertEquals("low", JSONObject(takeRequest().body.readUtf8()).getString("reasoning_effort"))
    }

    @Test
    fun anEffortDeepSeekDoesNotOfferFallsBackToHigh() {
        reply(200, successBody("ok"))
        call(request(effort = AiEffort.MEDIUM))

        assertEquals("high", JSONObject(takeRequest().body.readUtf8()).getString("reasoning_effort"))
    }

    @Test
    fun doesNotSendSamplingParameters() {
        reply(200, successBody("ok"))
        call()

        val json = JSONObject(takeRequest().body.readUtf8())
        for (forbidden in listOf("temperature", "top_p", "presence_penalty", "frequency_penalty")) {
            assertFalse("$forbidden must not be sent", json.has(forbidden))
        }
    }

    @Test
    fun theApiKeyOnlyTravelsInTheHeader() {
        reply(200, successBody("ok"))
        call()

        assertFalse(takeRequest().body.readUtf8().contains(KEY))
    }

    @Test
    fun aTrailingSlashOnTheBaseUrlIsHarmless() {
        reply(200, successBody("ok"))
        call(baseUrl = baseUrl() + "/")

        assertEquals("/chat/completions", takeRequest().requestUrl!!.encodedPath)
    }

    @Test
    fun theDefaultClientHasTheDocumentedTimeoutsAndNeverFollowsRedirects() {
        val client = DeepSeekTextGenerator.defaultClient()

        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(180_000, client.readTimeoutMillis)
        assertEquals(200_000, client.callTimeoutMillis)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    @Test
    fun aRedirectIsNotFollowedAndNothingIsResent() {
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", server.url("/elsewhere").toString()))
        reply(200, successBody("should never be read"))

        assertEquals(AiResult.Failure(AiError.UNKNOWN), call())
        takeRequest()
        assertEquals(1, server.requestCount)
    }

    @Test
    fun theKeyAndPromptsNeverShowUpInTheRequestObjectsToString() {
        val text = request().toString()

        assertFalse(text.contains(KEY))
        assertFalse(text.contains("SYSTEM-PROMPT"))
        assertFalse(text.contains("USER-MESSAGE"))
    }

    // ---- responses ----------------------------------------------------------------------------

    @Test
    fun parsesTheAnswerAndTheAnsweringModel() {
        reply(200, successBody("  Roast served.\n", model = "deepseek-v4-flash-0801"))

        assertEquals(AiResult.Success("Roast served.", "deepseek-v4-flash-0801"), call())
    }

    @Test
    fun theChainOfThoughtIsNeverReturned() {
        reply(200, successBody("final answer", reasoning = "SECRET-REASONING"))

        val result = call()
        assertEquals(AiResult.Success("final answer", "deepseek-v4-flash"), result)
        assertFalse((result as AiResult.Success).text.contains("SECRET-REASONING"))
    }

    @Test
    fun aMissingResponseModelFallsBackToTheRequestedOne() {
        reply(200, """{"choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"hi"}}]}""")

        assertEquals(AiResult.Success("hi", "deepseek-custom"), call(request(model = "deepseek-custom")))
    }

    @Test
    fun contentFilterIsRefusedEvenWithPartialText() {
        reply(200, successBody("half an answer", finishReason = "content_filter"))

        assertEquals(AiResult.Failure(AiError.REFUSED), call())
    }

    @Test
    fun lengthStopWithContentIsStillASuccess() {
        reply(200, successBody("a long answer that was cut", finishReason = "length"))

        assertEquals(AiResult.Success("a long answer that was cut", "deepseek-v4-flash"), call())
    }

    @Test
    fun lengthStopWithoutContentIsUnknown() {
        reply(200, successBody("", finishReason = "length", reasoning = "thought for ages"))

        assertEquals(AiResult.Failure(AiError.UNKNOWN), call())
    }

    @Test
    fun insufficientSystemResourceIsOverloaded() {
        reply(200, successBody("", finishReason = "insufficient_system_resource"))

        assertEquals(AiResult.Failure(AiError.OVERLOADED), call())
    }

    @Test
    fun emptyOrBlankContentIsUnknown() {
        reply(200, successBody(""))
        assertEquals(AiResult.Failure(AiError.UNKNOWN), call())

        reply(200, successBody("  \n "))
        assertEquals(AiResult.Failure(AiError.UNKNOWN), call())
    }

    @Test
    fun nullContentIsUnknownNotTheTextNull() {
        reply(
            200,
            """{"model":"m","choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":null}}]}""",
        )

        assertEquals(AiResult.Failure(AiError.UNKNOWN), call())
    }

    @Test
    fun noChoicesIsUnknown() {
        reply(200, """{"model":"m","choices":[]}""")
        assertEquals(AiResult.Failure(AiError.UNKNOWN), call())

        reply(200, """{"model":"m"}""")
        assertEquals(AiResult.Failure(AiError.UNKNOWN), call())
    }

    @Test
    fun malformedJsonIsUnknown() {
        reply(200, "<html>proxy login page</html>")
        assertEquals(AiResult.Failure(AiError.UNKNOWN), call())

        reply(200, "")
        assertEquals(AiResult.Failure(AiError.UNKNOWN), call())

        reply(200, "[1,2,3]")
        assertEquals(AiResult.Failure(AiError.UNKNOWN), call())
    }

    @Test
    fun httpErrorsAreMapped() {
        val cases = listOf(
            401 to AiError.INVALID_API_KEY,
            403 to AiError.INVALID_API_KEY,
            402 to AiError.INSUFFICIENT_BALANCE,
            429 to AiError.RATE_LIMITED,
            500 to AiError.SERVER,
            502 to AiError.SERVER,
            503 to AiError.OVERLOADED,
            400 to AiError.BAD_REQUEST,
            422 to AiError.BAD_REQUEST,
            404 to AiError.BAD_REQUEST,
            413 to AiError.UNKNOWN,
        )
        for ((code, expected) in cases) {
            reply(code, errorBody())
            assertEquals("HTTP $code", AiResult.Failure(expected), call())
        }
    }

    @Test
    fun anErrorStatusIsNeverTreatedAsAnAnswerEvenIfTheBodyLooksLikeOne() {
        reply(500, successBody("looks fine"))

        assertEquals(AiResult.Failure(AiError.SERVER), call())
    }

    @Test
    fun connectionFailureIsNetwork() {
        val url = baseUrl()
        server.shutdown() // nothing is listening any more

        assertEquals(AiResult.Failure(AiError.NETWORK), call(baseUrl = url))
    }

    @Test
    fun anUnparsableBaseUrlIsUnknownNotACrash() {
        assertEquals(AiResult.Failure(AiError.UNKNOWN), call(baseUrl = "not a url"))
    }

    @Test
    fun aKeyThatCannotBeAHeaderValueIsUnknownNotACrash() {
        assertEquals(AiResult.Failure(AiError.UNKNOWN), call(request().copy(apiKey = "sk-bad\nkey")))
    }

    // ---- pure mapping -------------------------------------------------------------------------

    @Test
    fun statusTableMatchesTheDocumentedCodes() {
        assertEquals(AiError.INVALID_API_KEY, DeepSeekErrors.fromStatus(401))
        assertEquals(AiError.INSUFFICIENT_BALANCE, DeepSeekErrors.fromStatus(402))
        assertEquals(AiError.RATE_LIMITED, DeepSeekErrors.fromStatus(429))
        assertEquals(AiError.SERVER, DeepSeekErrors.fromStatus(500))
        assertEquals(AiError.OVERLOADED, DeepSeekErrors.fromStatus(503))
        assertEquals(AiError.BAD_REQUEST, DeepSeekErrors.fromStatus(400))
        assertEquals(AiError.BAD_REQUEST, DeepSeekErrors.fromStatus(404))
        assertEquals(AiError.BAD_REQUEST, DeepSeekErrors.fromStatus(422))
        assertEquals(AiError.UNKNOWN, DeepSeekErrors.fromStatus(413))
        assertEquals(AiError.UNKNOWN, DeepSeekErrors.fromStatus(302))
    }

    @Test
    fun transportExceptionsAreMapped() {
        assertEquals(AiError.NETWORK, DeepSeekErrors.map(java.io.IOException("broken pipe")))
        assertEquals(AiError.NETWORK, DeepSeekErrors.map(java.net.SocketTimeoutException("timeout")))
        assertEquals(AiError.NETWORK, DeepSeekErrors.map(java.net.UnknownHostException("api.deepseek.com")))
        assertEquals(AiError.UNKNOWN, DeepSeekErrors.map(IllegalStateException("boom")))
    }

    private companion object {
        const val KEY = "sk-deepseek-test-0123456789"

        fun successBody(
            content: String?,
            model: String = "deepseek-v4-flash",
            finishReason: String = "stop",
            reasoning: String? = null,
        ): String {
            val message = JSONObject().put("role", "assistant").put("content", content ?: JSONObject.NULL)
            if (reasoning != null) message.put("reasoning_content", reasoning)
            return JSONObject()
                .put("id", "chatcmpl-test")
                .put("object", "chat.completion")
                .put("model", model)
                .put(
                    "choices",
                    JSONArray().put(
                        JSONObject().put("index", 0).put("message", message).put("finish_reason", finishReason),
                    ),
                )
                .put("usage", JSONObject().put("prompt_tokens", 10).put("completion_tokens", 20))
                .toString()
        }
    }
}
