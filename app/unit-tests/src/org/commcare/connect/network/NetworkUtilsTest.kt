package org.commcare.connect.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import org.commcare.CommCareTestApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.HttpException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class NetworkUtilsTest {

    // ── getErrorBody ─────────────────────────────────────────────────────────

    @Test
    fun getErrorBody_nullStream_returnsEmptyString() {
        assertEquals("", NetworkUtils.getErrorBody(null))
    }

    @Test
    fun getErrorBody_validUtf8Stream_returnsDecodedString() {
        val content = "error response content"
        val stream = ByteArrayInputStream(content.toByteArray(StandardCharsets.UTF_8))
        assertEquals(content, NetworkUtils.getErrorBody(stream))
    }

    @Test
    fun getErrorBody_utf8ContentWithSpecialChars_returnsDecodedString() {
        val content = """{"error_code":"invalid","message":"résumé"}"""
        val stream = ByteArrayInputStream(content.toByteArray(StandardCharsets.UTF_8))
        assertEquals(content, NetworkUtils.getErrorBody(stream))
    }

    // ── getErrorCodes ────────────────────────────────────────────────────────

    @Test
    fun getErrorCodes_validJsonWithBothFields_returnsBothCodes() {
        val json = """{"error_code":"invalid_token","error_sub_code":"expired"}"""
        val result = NetworkUtils.getErrorCodes(json)
        assertEquals("invalid_token", result.first)
        assertEquals("expired", result.second)
    }

    @Test
    fun getErrorCodes_jsonMissingSubCode_returnsCodeAndEmptySubCode() {
        val json = """{"error_code":"auth_error"}"""
        val result = NetworkUtils.getErrorCodes(json)
        assertEquals("auth_error", result.first)
        assertEquals("", result.second)
    }

    @Test
    fun getErrorCodes_jsonMissingErrorCode_returnsEmptyCodeAndSubCode() {
        val json = """{"error_sub_code":"expired"}"""
        val result = NetworkUtils.getErrorCodes(json)
        assertEquals("", result.first)
        assertEquals("expired", result.second)
    }

    @Test
    fun getErrorCodes_emptyJsonObject_returnsBothEmpty() {
        val result = NetworkUtils.getErrorCodes("{}")
        assertEquals("", result.first)
        assertEquals("", result.second)
    }

    @Test
    fun getErrorCodes_emptyString_returnsBothEmpty() {
        val result = NetworkUtils.getErrorCodes("")
        assertEquals("", result.first)
        assertEquals("", result.second)
    }

    @Test
    fun getErrorCodes_invalidJson_returnsBothEmpty() {
        val result = NetworkUtils.getErrorCodes("not valid json")
        assertEquals("", result.first)
        assertEquals("", result.second)
    }

    // ── logFailedResponse ────────────────────────────────────────────────────

    @Test
    fun logFailedResponse_400_doesNotThrow() {
        NetworkUtils.logFailedResponse("Bad Request", 400, "https://example.com/api", "")
    }

    @Test
    fun logFailedResponse_401_doesNotThrow() {
        NetworkUtils.logFailedResponse("Unauthorized", 401, "https://example.com/api", "token expired")
    }

    @Test
    fun logFailedResponse_404_doesNotThrow() {
        NetworkUtils.logFailedResponse("Not Found", 404, "https://example.com/api", "")
    }

    @Test
    fun logFailedResponse_500_doesNotThrow() {
        NetworkUtils.logFailedResponse("Server Error", 500, "https://example.com/api", "")
    }

    @Test
    fun logFailedResponse_otherCode_doesNotThrow() {
        NetworkUtils.logFailedResponse("Forbidden", 403, "https://example.com/api", "forbidden body")
    }

    // ── logNetworkError ──────────────────────────────────────────────────────

    @Test
    fun logNetworkError_IOException_doesNotThrow() {
        NetworkUtils.logNetworkError(IOException("connection timeout"), "https://example.com/api")
    }

    @Test
    fun logNetworkError_HttpException_doesNotThrow() {
        val httpException = mockk<HttpException>()
        every { httpException.message } returns "HTTP 401 Unauthorized"
        NetworkUtils.logNetworkError(httpException, "https://example.com/api")
    }

    @Test
    fun logNetworkError_genericException_doesNotThrow() {
        NetworkUtils.logNetworkError(RuntimeException("unknown error"), "https://example.com/api")
    }
}
