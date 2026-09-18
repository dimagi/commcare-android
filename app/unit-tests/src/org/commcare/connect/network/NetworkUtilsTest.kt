package org.commcare.connect.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import org.commcare.CommCareTestApplication
import org.commcare.connect.network.base.NetworkUtils
import org.commcare.connect.network.personalId.PersonalIdApiErrorBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // ── parseErrorBody ───────────────────────────────────────────────────────

    @Test
    fun `parseErrorBody reads every field the client cares about`() {
        val json =
            """{"error_code":"RATE_LIMITED","error_sub_code":"expired","retry_after_seconds":7200}"""
        val result = NetworkUtils.parseErrorBody(json)
        assertEquals("RATE_LIMITED", result.errorCode)
        assertEquals("expired", result.errorSubCode)
        assertEquals(7200, result.retryAfterSeconds)
    }

    @Test
    fun `parseErrorBody returns the code and an empty sub-code when the sub-code is missing`() {
        val result = NetworkUtils.parseErrorBody("""{"error_code":"auth_error"}""")
        assertEquals("auth_error", result.errorCode)
        assertEquals("", result.errorSubCode)
    }

    @Test
    fun `parseErrorBody returns an empty code and the sub-code when error_code is missing`() {
        val result = NetworkUtils.parseErrorBody("""{"error_sub_code":"expired"}""")
        assertEquals("", result.errorCode)
        assertEquals("expired", result.errorSubCode)
    }

    @Test
    fun `parseErrorBody returns a null wait when the server did not supply one`() {
        assertNull(NetworkUtils.parseErrorBody("""{"error_code":"RATE_LIMITED"}""").retryAfterSeconds)
    }

    @Test
    fun `parseErrorBody treats a non-positive wait as no wait at all`() {
        assertNull(NetworkUtils.parseErrorBody("""{"retry_after_seconds":0}""").retryAfterSeconds)
        assertNull(NetworkUtils.parseErrorBody("""{"retry_after_seconds":-30}""").retryAfterSeconds)
    }

    @Test
    fun `parseErrorBody returns empty fields for an empty JSON object`() {
        assertEquals(PersonalIdApiErrorBody("", "", null), NetworkUtils.parseErrorBody("{}"))
    }

    @Test
    fun `parseErrorBody returns empty fields for an empty input string`() {
        assertEquals(PersonalIdApiErrorBody("", "", null), NetworkUtils.parseErrorBody(""))
    }

    @Test
    fun `parseErrorBody returns empty fields for invalid JSON`() {
        assertEquals(PersonalIdApiErrorBody("", "", null), NetworkUtils.parseErrorBody("not valid json"))
        assertEquals(PersonalIdApiErrorBody("", "", null), NetworkUtils.parseErrorBody("<html>429</html>"))
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
        val httpException = mockk<HttpException>(relaxed = true)
        every { httpException.message } returns "HTTP 401 Unauthorized"
        NetworkUtils.logNetworkError(httpException, "https://example.com/api")
    }

    @Test
    fun `logNetworkError does not throw for a generic Exception`() {
        NetworkUtils.logNetworkError(RuntimeException("unknown error"), "https://example.com/api")
    }
}
