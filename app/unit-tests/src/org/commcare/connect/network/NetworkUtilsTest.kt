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
    fun `getErrorBody returns an empty string when given a null stream`() {
        assertEquals("", NetworkUtils.getErrorBody(null))
    }

    @Test
    fun `getErrorBody returns the decoded string for a valid UTF-8 stream`() {
        val content = "error response content"
        val stream = ByteArrayInputStream(content.toByteArray(StandardCharsets.UTF_8))
        assertEquals(content, NetworkUtils.getErrorBody(stream))
    }

    @Test
    fun `getErrorBody decodes UTF-8 content containing special characters`() {
        val content = """{"error_code":"invalid","message":"résumé"}"""
        val stream = ByteArrayInputStream(content.toByteArray(StandardCharsets.UTF_8))
        assertEquals(content, NetworkUtils.getErrorBody(stream))
    }

    // ── getErrorCodes ────────────────────────────────────────────────────────

    @Test
    fun `getErrorCodes returns both codes when the JSON contains error_code and error_sub_code`() {
        val json = """{"error_code":"invalid_token","error_sub_code":"expired"}"""
        val result = NetworkUtils.getErrorCodes(json)
        assertEquals("invalid_token", result.first)
        assertEquals("expired", result.second)
    }

    @Test
    fun `getErrorCodes returns the code and an empty sub-code when the sub-code is missing`() {
        val json = """{"error_code":"auth_error"}"""
        val result = NetworkUtils.getErrorCodes(json)
        assertEquals("auth_error", result.first)
        assertEquals("", result.second)
    }

    @Test
    fun `getErrorCodes returns an empty code and the sub-code when error_code is missing`() {
        val json = """{"error_sub_code":"expired"}"""
        val result = NetworkUtils.getErrorCodes(json)
        assertEquals("", result.first)
        assertEquals("expired", result.second)
    }

    @Test
    fun `getErrorCodes returns both codes empty for an empty JSON object`() {
        val result = NetworkUtils.getErrorCodes("{}")
        assertEquals("", result.first)
        assertEquals("", result.second)
    }

    @Test
    fun `getErrorCodes returns both codes empty for an empty input string`() {
        val result = NetworkUtils.getErrorCodes("")
        assertEquals("", result.first)
        assertEquals("", result.second)
    }

    @Test
    fun `getErrorCodes returns both codes empty for invalid JSON`() {
        val result = NetworkUtils.getErrorCodes("not valid json")
        assertEquals("", result.first)
        assertEquals("", result.second)
    }

    // ── logFailedResponse ────────────────────────────────────────────────────

    @Test
    fun `logFailedResponse does not throw for a 400 response`() {
        NetworkUtils.logFailedResponse("Bad Request", 400, "https://example.com/api", "")
    }

    @Test
    fun `logFailedResponse does not throw for a 401 response`() {
        NetworkUtils.logFailedResponse("Unauthorized", 401, "https://example.com/api", "token expired")
    }

    @Test
    fun `logFailedResponse does not throw for a 404 response`() {
        NetworkUtils.logFailedResponse("Not Found", 404, "https://example.com/api", "")
    }

    @Test
    fun `logFailedResponse does not throw for a 500 response`() {
        NetworkUtils.logFailedResponse("Server Error", 500, "https://example.com/api", "")
    }

    @Test
    fun `logFailedResponse does not throw for a 403 response`() {
        NetworkUtils.logFailedResponse("Forbidden", 403, "https://example.com/api", "forbidden body")
    }

    // ── logNetworkError ──────────────────────────────────────────────────────

    @Test
    fun `logNetworkError does not throw for an IOException`() {
        NetworkUtils.logNetworkError(IOException("connection timeout"), "https://example.com/api")
    }

    @Test
    fun `logNetworkError does not throw for an HttpException`() {
        val httpException = mockk<HttpException>()
        every { httpException.message } returns "HTTP 401 Unauthorized"
        NetworkUtils.logNetworkError(httpException, "https://example.com/api")
    }

    @Test
    fun `logNetworkError does not throw for a generic Exception`() {
        NetworkUtils.logNetworkError(RuntimeException("unknown error"), "https://example.com/api")
    }
}
