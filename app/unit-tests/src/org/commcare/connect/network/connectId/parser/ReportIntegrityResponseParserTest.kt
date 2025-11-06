package org.commcare.connect.network.connectId.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ReportIntegrityResponseParserTest {
    private lateinit var parser: ReportIntegrityResponseParser<Boolean>

    @Before
    fun setUp() {
        parser = ReportIntegrityResponseParser<Boolean>()
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
    }
}
