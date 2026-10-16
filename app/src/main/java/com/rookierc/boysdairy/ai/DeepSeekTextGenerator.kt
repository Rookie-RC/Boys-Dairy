package com.rookierc.boysdairy.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Duration

/** Single place for the values that identify and tune the DeepSeek request. */
object DeepSeekConfig {
    const val BASE_URL = "https://api.deepseek.com"
    const val CHAT_PATH = "/chat/completions"

    const val MAX_TOKENS = 32_000

    /** The model reasons before answering, so a response can take minutes; hence the long read timeout. */
    val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(15)
    val READ_TIMEOUT: Duration = Duration.ofSeconds(180)
    val CALL_TIMEOUT: Duration = Duration.ofSeconds(200)
}

/**
 * [AiTextGenerator] for DeepSeek's OpenAI-compatible chat API. One blocking, non-streaming POST per
 * [generate], run on the IO dispatcher; no retries. The API key is passed per call and never
 * stored or logged here, and neither are the request and response bodies.
 *
 * Thinking is switched on explicitly; the chain of thought in `reasoning_content` is ignored and
 * never surfaces. There is no temperature/top_p (they have no effect in thinking mode).
 *
 * @param baseUrl scheme and host of the API; a seam for tests.
 * @param clientFactory builds the HTTP client once, on first use; a seam for tests.
 */
class DeepSeekTextGenerator(
    baseUrl: String = DeepSeekConfig.BASE_URL,
    clientFactory: () -> OkHttpClient = ::defaultClient,
) : AiTextGenerator {

    private val endpoint = baseUrl.trimEnd('/') + DeepSeekConfig.CHAT_PATH
    private val client: OkHttpClient by lazy(clientFactory)

    override suspend fun generate(request: AiRequest): AiResult =
        try {
            // Interruptible so cancelling the coroutine aborts the in-flight HTTP call.
            runInterruptible(Dispatchers.IO) { execute(request) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AiResult.Failure(DeepSeekErrors.map(e))
        }

    private fun execute(request: AiRequest): AiResult {
        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${request.apiKey}")
            .post(buildBody(request).toString().toRequestBody(JSON))
            .build()
        client.newCall(httpRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return DeepSeekResponse.interpret(response.code, body, request.model)
        }
    }

    internal companion object {
        private val JSON = "application/json".toMediaType()

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(DeepSeekConfig.CONNECT_TIMEOUT)
                .readTimeout(DeepSeekConfig.READ_TIMEOUT)
                .callTimeout(DeepSeekConfig.CALL_TIMEOUT)
                // The request carries the diary summary and the key: never follow it to another place.
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build()

        /** DeepSeek offers low, high and max only; MEDIUM is sent as the nearest one, high. */
        private fun AiEffort.toDeepSeekEffort(): String = when (this) {
            AiEffort.LOW -> "low"
            AiEffort.MEDIUM, AiEffort.HIGH -> "high"
            AiEffort.MAX -> "max"
        }

        /** The JSON body for [request]. */
        fun buildBody(request: AiRequest): JSONObject =
            JSONObject()
                .put("model", request.model)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", request.system))
                        .put(JSONObject().put("role", "user").put("content", request.user)),
                )
                .put("thinking", JSONObject().put("type", "enabled"))
                .put("reasoning_effort", request.effort.toDeepSeekEffort())
                .put("max_tokens", DeepSeekConfig.MAX_TOKENS)
                .put("stream", false)
    }
}

/** Turns an HTTP status and body into an [AiResult]; pure so it can be unit-tested without a server. */
internal object DeepSeekResponse {
    const val FINISH_CONTENT_FILTER = "content_filter"
    const val FINISH_NO_RESOURCE = "insufficient_system_resource"

    /**
     * @param requestedModel reported as the answering model when the response omits its own `model`.
     *
     * The finish reason is checked before the content: a filtered answer is a failure even if it
     * carries some text. A `length` stop with text is still a usable (shortened) answer.
     */
    fun interpret(status: Int, body: String, requestedModel: String): AiResult {
        if (status !in 200..299) return AiResult.Failure(DeepSeekErrors.fromStatus(status))
        return try {
            val root = JSONObject(body)
            val choice = root.optJSONArray("choices")?.optJSONObject(0)
                ?: return AiResult.Failure(AiError.UNKNOWN)
            when (choice.stringOrNull("finish_reason")) {
                FINISH_CONTENT_FILTER -> return AiResult.Failure(AiError.REFUSED)
                FINISH_NO_RESOURCE -> return AiResult.Failure(AiError.OVERLOADED)
            }
            // `reasoning_content` sits next to `content` and is deliberately never read.
            val content = choice.optJSONObject("message")?.stringOrNull("content")?.trim().orEmpty()
            if (content.isEmpty()) return AiResult.Failure(AiError.UNKNOWN)
            val model = root.stringOrNull("model")?.takeIf { it.isNotBlank() } ?: requestedModel
            AiResult.Success(text = content, model = model)
        } catch (_: JSONException) {
            AiResult.Failure(AiError.UNKNOWN)
        }
    }

    /** org.json's `optString` returns the text "null" for a JSON null on Android; this returns null. */
    private fun JSONObject.stringOrNull(name: String): String? = if (isNull(name)) null else optString(name)
}

/** Maps DeepSeek HTTP statuses and transport exceptions to [AiError]. */
internal object DeepSeekErrors {
    fun map(error: Throwable): AiError = when (error) {
        is IOException -> AiError.NETWORK
        else -> AiError.UNKNOWN
    }

    fun fromStatus(status: Int): AiError = when {
        status == 401 || status == 403 -> AiError.INVALID_API_KEY
        status == 402 -> AiError.INSUFFICIENT_BALANCE
        status == 429 -> AiError.RATE_LIMITED
        // 400 bad body, 422 invalid parameters; 404 is what an unknown model id can come back as.
        status == 400 || status == 404 || status == 422 -> AiError.BAD_REQUEST
        status == 503 -> AiError.OVERLOADED
        status in 500..599 -> AiError.SERVER
        else -> AiError.UNKNOWN
    }
}
