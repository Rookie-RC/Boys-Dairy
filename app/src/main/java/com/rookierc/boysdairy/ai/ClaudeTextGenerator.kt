package com.rookierc.boysdairy.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicRetryableException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.errors.UnprocessableEntityException
import com.anthropic.models.beta.messages.BetaMessage
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.IOException
import java.time.Duration

/** Single place for the values that tune the Claude request (the model itself comes from the request). */
object ClaudeConfig {
    /** Beta header that enables server-side fallback when the primary model refuses. */
    const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

    /**
     * Models that accept the server-side fallback request. Other models (Haiku 5.5, for one)
     * answer a request that carries it with HTTP 400, so it is only sent for these.
     */
    val FALLBACK_MODELS: Set<String> = setOf("claude-opus-5-5")

    const val MAX_TOKENS = 16_000L
    val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(120)
    const val MAX_RETRIES = 1
}

/**
 * [AiTextGenerator] backed by the official Anthropic Java SDK. One blocking, non-streaming call to
 * the beta Messages endpoint per [generate], run on the IO dispatcher. The API key is passed per
 * call and never stored or logged here.
 *
 * Model notes: thinking is adaptive and on by default, so no `thinking` parameter is sent; the
 * model and the effort come from the [AiRequest]; there is no temperature/top_p and no assistant
 * prefill.
 *
 * @param useRefusalFallback when true (the default) a request for a model in
 *   [ClaudeConfig.FALLBACK_MODELS] opts in to server-side fallback (`fallbacks: "default"` + beta
 *   header), so a refusal by the primary model can be answered by a fallback model instead of
 *   failing. Other models never get it.
 * @param clientFactory builds the SDK client for an API key; a seam for tests.
 */
class ClaudeTextGenerator(
    private val useRefusalFallback: Boolean = true,
    private val clientFactory: (apiKey: String) -> AnthropicClient = ::defaultClient,
) : AiTextGenerator {

    override suspend fun generate(request: AiRequest): AiResult =
        try {
            // Interruptible so cancelling the coroutine aborts the in-flight HTTP call.
            runInterruptible(Dispatchers.IO) {
                val client = clientFactory(request.apiKey)
                try {
                    val response = client.beta().messages().create(buildParams(request, useRefusalFallback))
                    ClaudeResponse.interpret(
                        stopReason = response.stopReason().map { it.asString() }.orElse(null),
                        texts = response.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) },
                        model = response.model().asString(),
                    )
                } finally {
                    client.close()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AiResult.Failure(ClaudeErrors.map(e))
        }

    internal companion object {
        /** The SDK request for [request]; pure so the model/effort/fallback rules can be unit-tested. */
        fun buildParams(request: AiRequest, useRefusalFallback: Boolean = true): MessageCreateParams {
            val builder = MessageCreateParams.builder()
                .model(request.model)
                .maxTokens(ClaudeConfig.MAX_TOKENS)
                .outputConfig(BetaOutputConfig.builder().effort(request.effort.toClaudeEffort()).build())
                .system(request.system)
                .addUserMessage(request.user)
            if (useRefusalFallback && request.model in ClaudeConfig.FALLBACK_MODELS) {
                builder
                    .addBeta(ClaudeConfig.FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            }
            return builder.build()
        }

        private fun AiEffort.toClaudeEffort(): BetaOutputConfig.Effort = when (this) {
            AiEffort.LOW -> BetaOutputConfig.Effort.LOW
            AiEffort.MEDIUM -> BetaOutputConfig.Effort.MEDIUM
            AiEffort.HIGH -> BetaOutputConfig.Effort.HIGH
            AiEffort.MAX -> BetaOutputConfig.Effort.MAX
        }

        fun defaultClient(apiKey: String): AnthropicClient =
            AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(ClaudeConfig.REQUEST_TIMEOUT)
                .maxRetries(ClaudeConfig.MAX_RETRIES)
                .build()
    }
}

/** Turns a decoded response into an [AiResult]; pure so it can be unit-tested without the SDK. */
internal object ClaudeResponse {
    const val STOP_REFUSAL = "refusal"

    /**
     * The stop reason is checked before the content: a refusal is a failure even if the response
     * carries some text. All text blocks are concatenated; thinking blocks never reach [texts].
     */
    fun interpret(stopReason: String?, texts: List<String>, model: String): AiResult {
        if (stopReason == STOP_REFUSAL) return AiResult.Failure(AiError.REFUSED)
        val text = texts.joinToString(separator = "").trim()
        if (text.isEmpty()) return AiResult.Failure(AiError.UNKNOWN)
        return AiResult.Success(text = text, model = model)
    }
}

/** Maps SDK and transport exceptions to [AiError]. */
internal object ClaudeErrors {
    private const val ERROR_TYPE_OVERLOADED = "overloaded_error"

    fun map(error: Throwable): AiError = when (error) {
        // Specific classes first.
        is UnauthorizedException, is PermissionDeniedException -> AiError.INVALID_API_KEY
        is RateLimitException -> AiError.RATE_LIMITED
        is BadRequestException, is NotFoundException, is UnprocessableEntityException -> AiError.BAD_REQUEST
        is AnthropicServiceException -> {
            val overloadedType = error.errorType().map { it.asString() }.orElse(null) == ERROR_TYPE_OVERLOADED
            if (overloadedType) AiError.OVERLOADED else fromStatus(error.statusCode())
        }
        is AnthropicIoException, is AnthropicRetryableException, is IOException -> AiError.NETWORK
        is AnthropicException -> AiError.UNKNOWN
        else -> AiError.UNKNOWN
    }

    fun fromStatus(status: Int): AiError = when {
        status == 401 || status == 403 -> AiError.INVALID_API_KEY
        // 402 billing_error: a problem with the account's billing or payment details.
        status == 402 -> AiError.INSUFFICIENT_BALANCE
        status == 429 -> AiError.RATE_LIMITED
        // An unknown or mistyped model id is a 404 not_found_error; a malformed request is a 400.
        status == 400 || status == 404 || status == 422 -> AiError.BAD_REQUEST
        status == 529 -> AiError.OVERLOADED
        status in 500..599 -> AiError.SERVER
        else -> AiError.UNKNOWN
    }
}
