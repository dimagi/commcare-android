package org.commcare.connect.network.connectId.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ReportIntegrityResponseParserTest {
    private lateinit var parser: ReportIntegrityResponseParser<Boolean>
    private lateinit var firebaseAnalyticsMock: MockedStatic<FirebaseAnalyticsUtil>

    @Before
    fun setUp() {
        parser = ReportIntegrityResponseParser<Boolean>()
        firebaseAnalyticsMock = mockStatic(FirebaseAnalyticsUtil::class.java)
    }

    @After
    fun tearDown() {
        firebaseAnalyticsMock.close()
    }

    @Test
    fun testParseValidResponseWithResultCode() {
        // Arrange
        val jsonResponse =
            JSONObject().apply {
                put("result_code", "test-result-code")
            }

        val jsonString = jsonResponse.toString()
        val inputStream: InputStream = ByteArrayInputStream(jsonString.toByteArray())
        val requestId = "test-request-123"

        // Act
        val result = parser.parse(200, inputStream, requestId)

        // Assert
        assertTrue(result)
        firebaseAnalyticsMock.verify(
            { FirebaseAnalyticsUtil.reportPersonalIdHeartbeatIntegritySubmission(requestId, "test-result-code") },
            times(1),
        )
    }

    @Test
    fun testParseResponseWithNullResultCode() {
        // Arrange
        val jsonResponse =
            JSONObject().apply {
                put("result_code", JSONObject.NULL)
            }

        val jsonString = jsonResponse.toString()
        val inputStream: InputStream = ByteArrayInputStream(jsonString.toByteArray())
        val requestId = "test-request-789"

        // Act
        val result = parser.parse(200, inputStream, requestId)

        // Assert
        assertTrue(result)
        firebaseAnalyticsMock.verify(
            { FirebaseAnalyticsUtil.reportPersonalIdHeartbeatIntegritySubmission(requestId, "NoCodeFromServer") },
            times(1),
        )
    }

    @Test
    fun testParseResponseWithoutResultCodeField() {
        // Arrange - JSON without result_code field
        val jsonResponse =
            JSONObject().apply {
                put("some_other_field", "some_value")
            }

        val jsonString = jsonResponse.toString()
        val inputStream: InputStream = ByteArrayInputStream(jsonString.toByteArray())
        val requestId = "test-request-000"

        // Act
        val result = parser.parse(200, inputStream, requestId)

        // Assert - Parser should return true and use default value "NoCodeFromServer"
        assertTrue(result)
        firebaseAnalyticsMock.verify(
            { FirebaseAnalyticsUtil.reportPersonalIdHeartbeatIntegritySubmission(requestId, "NoCodeFromServer") },
            times(1),
        )
    }

    @Test(expected = JSONException::class)
    fun testParseWithMalformedJson() {
        // Arrange - Invalid JSON
        val malformedJson = "{ invalid json content "
        val inputStream: InputStream = ByteArrayInputStream(malformedJson.toByteArray())
        val requestId = "test-request-malformed"

        // Act - Should throw JSONException
        parser.parse(200, inputStream, requestId)
    }

    @Test(expected = JSONException::class)
    fun testParseWithNonJsonContent() {
        // Arrange - Non-JSON content
        val nonJsonContent = "This is not JSON at all"
        val inputStream: InputStream = ByteArrayInputStream(nonJsonContent.toByteArray())
        val requestId = "test-request-nonjson"

        // Act - Should throw JSONException
        parser.parse(200, inputStream, requestId)
    }
}
