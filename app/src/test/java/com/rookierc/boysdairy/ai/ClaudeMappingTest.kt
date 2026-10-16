package com.rookierc.boysdairy.ai

import com.anthropic.core.JsonValue
import com.anthropic.core.http.Headers
import com.anthropic.errors.AnthropicException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicRetryableException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.errors.UnexpectedStatusCodeException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ClaudeMappingTest {

    private val headers = Headers.builder().build()
    private val body = JsonValue.from(emptyMap<String, Any>())

    // ---- responses ----------------------------------------------------------------------------

    @Test
    fun refusalStopReasonIsRefusedEvenWhenTextIsPresent() {
        val result = ClaudeResponse.interpret("refusal", listOf("I can't help with that."), "m")
        assertEquals(AiResult.Failure(AiError.REFUSED), result)
    }

    @Test
    fun textBlocksAreConcatenatedInOrder() {
        val result = ClaudeResponse.interpret("end_turn", listOf("Hello", ", ", "world"), "claude-opus-5-5")
        assertEquals(AiResult.Success("Hello, world", "claude-opus-5-5"), result)
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        val result = ClaudeResponse.interpret("end_turn", listOf("\n  hi \n"), "m")
        assertEquals(AiResult.Success("hi", "m"), result)
    }

    @Test
    fun noTextAtAllIsUnknown() {
        assertEquals(AiResult.Failure(AiError.UNKNOWN), ClaudeResponse.interpret("end_turn", emptyList(), "m"))
        assertEquals(AiResult.Failure(AiError.UNKNOWN), ClaudeResponse.interpret("max_tokens", listOf("  "), "m"))
    }

    @Test
    fun truncatedButNonEmptyTextIsStillReturned() {
        val result = ClaudeResponse.interpret("max_tokens", listOf("partial"), "m")
        assertEquals(AiResult.Success("partial", "m"), result)
    }

    @Test
    fun missingStopReasonIsTreatedAsNormal() {
        assertEquals(AiResult.Success("ok", "m"), ClaudeResponse.interpret(null, listOf("ok"), "m"))
    }

    // ---- HTTP status table --------------------------------------------------------------------

    @Test
    fun statusCodesMapToErrors() {
        assertEquals(AiError.INVALID_API_KEY, ClaudeErrors.fromStatus(401))
        assertEquals(AiError.INVALID_API_KEY, ClaudeErrors.fromStatus(403))
        assertEquals(AiError.RATE_LIMITED, ClaudeErrors.fromStatus(429))
        assertEquals(AiError.OVERLOADED, ClaudeErrors.fromStatus(529))
        assertEquals(AiError.SERVER, ClaudeErrors.fromStatus(500))
        assertEquals(AiError.SERVER, ClaudeErrors.fromStatus(502))
        assertEquals(AiError.SERVER, ClaudeErrors.fromStatus(503))
        assertEquals(AiError.SERVER, ClaudeErrors.fromStatus(504))
        assertEquals(AiError.UNKNOWN, ClaudeErrors.fromStatus(400))
        assertEquals(AiError.UNKNOWN, ClaudeErrors.fromStatus(404))
        assertEquals(AiError.UNKNOWN, ClaudeErrors.fromStatus(422))
        assertEquals(AiError.UNKNOWN, ClaudeErrors.fromStatus(200))
    }

    // ---- exceptions ---------------------------------------------------------------------------

    @Test
    fun authenticationAndPermissionErrorsMeanInvalidKey() {
        val unauthorized = UnauthorizedException.builder().headers(headers).body(body).build()
        val forbidden = PermissionDeniedException.builder().headers(headers).body(body).build()
        assertEquals(AiError.INVALID_API_KEY, ClaudeErrors.map(unauthorized))
        assertEquals(AiError.INVALID_API_KEY, ClaudeErrors.map(forbidden))
    }

    @Test
    fun rateLimitIsRateLimited() {
        val error = RateLimitException.builder().headers(headers).body(body).build()
        assertEquals(AiError.RATE_LIMITED, ClaudeErrors.map(error))
    }

    @Test
    fun status529IsOverloadedAndOtherServerErrorsAreServer() {
        fun internal(status: Int) =
            InternalServerException.builder().statusCode(status).headers(headers).body(body).build()

        assertEquals(AiError.OVERLOADED, ClaudeErrors.map(internal(529)))
        assertEquals(AiError.SERVER, ClaudeErrors.map(internal(500)))
        assertEquals(AiError.SERVER, ClaudeErrors.map(internal(503)))
    }

    @Test
    fun overloadedErrorTypeIsOverloadedWhateverTheStatus() {
        val errorBody = JsonValue.from(mapOf("type" to "error", "error" to mapOf("type" to "overloaded_error", "message" to "Overloaded")))
        val error = InternalServerException.builder().statusCode(500).headers(headers).body(errorBody).build()
        assertEquals(AiError.OVERLOADED, ClaudeErrors.map(error))
    }

    @Test
    fun unexpectedStatusCodesAreClassifiedByTheirNumber() {
        fun unexpected(status: Int) =
            UnexpectedStatusCodeException.builder().statusCode(status).headers(headers).body(body).build()

        assertEquals(AiError.OVERLOADED, ClaudeErrors.map(unexpected(529)))
        assertEquals(AiError.SERVER, ClaudeErrors.map(unexpected(504)))
        assertEquals(AiError.UNKNOWN, ClaudeErrors.map(unexpected(418)))
    }

    @Test
    fun connectivityProblemsAreNetwork() {
        assertEquals(AiError.NETWORK, ClaudeErrors.map(AnthropicIoException("io")))
        assertEquals(AiError.NETWORK, ClaudeErrors.map(AnthropicRetryableException("retry")))
        assertEquals(AiError.NETWORK, ClaudeErrors.map(IOException("broken pipe")))
        assertEquals(AiError.NETWORK, ClaudeErrors.map(SocketTimeoutException("timeout")))
        assertEquals(AiError.NETWORK, ClaudeErrors.map(UnknownHostException("api.anthropic.com")))
        assertEquals(AiError.NETWORK, ClaudeErrors.map(ConnectException("refused")))
    }

    @Test
    fun everythingElseIsUnknown() {
        assertEquals(AiError.UNKNOWN, ClaudeErrors.map(AnthropicException("bad data")))
        assertEquals(AiError.UNKNOWN, ClaudeErrors.map(IllegalStateException("boom")))
        assertEquals(AiError.UNKNOWN, ClaudeErrors.map(IllegalArgumentException("bad header value")))
    }
}
