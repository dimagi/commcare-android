package org.commcare.connect.network.connectId.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.ConnectConstants
import org.commcare.core.network.AuthInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.util.Date
import kotlin.math.abs

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectTokenResponseParserTest {
    private data class TokenTestData(
        val token: String,
        val expiresInSeconds: Int? = null,
        val expectedTimeDeltaSeconds: Long? = null,
    )

    private fun createTokenJson(testData: TokenTestData): String =
        if (testData.expiresInSeconds != null) {
            """
            {
                "${ConnectConstants.CONNECT_KEY_TOKEN}": "${testData.token}",
                "${ConnectConstants.CONNECT_KEY_EXPIRES}": ${testData.expiresInSeconds}
            }
            """.trimIndent()
        } else {
            """
            {
                "${ConnectConstants.CONNECT_KEY_TOKEN}": "${testData.token}"
            }
            """.trimIndent()
        }

    private fun createTokenJsonWithCustomExpires(
        token: String,
        expiresValue: Any,
    ): String =
        """
        {
            "${ConnectConstants.CONNECT_KEY_TOKEN}": "$token",
            "${ConnectConstants.CONNECT_KEY_EXPIRES}": $expiresValue
        }
        """.trimIndent()

    private fun parseTokenAndAssertBasics(
        jsonResponse: String,
        expectedToken: String,
        userRecord: ConnectUserRecord = ConnectUserRecord(),
    ): Pair<AuthInfo.TokenAuth, ConnectUserRecord> {
        val parser = ConnectTokenResponseParser<AuthInfo.TokenAuth>()
        val inputStream = ByteArrayInputStream(jsonResponse.toByteArray())
        val result = parser.parse(200, inputStream, userRecord)

        assertEquals(expectedToken, result.bearerToken)
        assertEquals(expectedToken, userRecord.connectToken)

        return Pair(result, userRecord)
    }

    private fun assertExpirationTime(
        userRecord: ConnectUserRecord,
        currentTime: Date,
        expectedDeltaSeconds: Long,
        tolerance: Long = 5000L,
    ) {
        val expectedExpirationTime = currentTime.time + (expectedDeltaSeconds * 1000L)
        val actualExpirationTime = userRecord.connectTokenExpiration.time
        val timeDifference = abs(actualExpirationTime - expectedExpirationTime)
        assertTrue("Expiration time should be within ${tolerance}ms of expected", timeDifference < tolerance)
    }

    private fun testTokenWithExpiration(testData: TokenTestData) {
        // Arrange
        val currentTime = Date()
        val jsonResponse = createTokenJson(testData)

        // Act & Basic Assert
        val (_, userRecord) = parseTokenAndAssertBasics(jsonResponse, testData.token)

        // Assert Expiration
        if (testData.expectedTimeDeltaSeconds != null) {
            assertExpirationTime(userRecord, currentTime, testData.expectedTimeDeltaSeconds)
        }
    }

    // Helper method for exception testing
    private fun parseTokenExpectingException(jsonResponse: String) {
        val parser = ConnectTokenResponseParser<AuthInfo.TokenAuth>()
        val userRecord = ConnectUserRecord()
        val inputStream = ByteArrayInputStream(jsonResponse.toByteArray())
        parser.parse(200, inputStream, userRecord)
    }

    @Test
    fun testParseValidResponseWithExpiresIn() {
        testTokenWithExpiration(
            TokenTestData(
                token = "test_token_123",
                expiresInSeconds = 3600,
                expectedTimeDeltaSeconds = 3600L,
            ),
        )
    }

    @Test
    fun testParseValidResponseWithoutExpiresIn() {
        testTokenWithExpiration(
            TokenTestData(
                token = "another_test_token",
                expiresInSeconds = null,
                expectedTimeDeltaSeconds = 0L,
            ),
        )
    }

    @Test
    fun testParseValidResponseWithZeroExpiresIn() {
        testTokenWithExpiration(
            TokenTestData(
                token = "zero_expiry_token",
                expiresInSeconds = 0,
                expectedTimeDeltaSeconds = 0L,
            ),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun testParseEmptyToken() {
        testTokenWithExpiration(
            TokenTestData(
                token = "",
                expiresInSeconds = 0,
                expectedTimeDeltaSeconds = 0L,
            ),
        )
    }

    @Test(expected = RuntimeException::class)
    fun testParseInvalidJsonThrowsRuntimeException() {
        // Arrange & Act & Assert
        parseTokenExpectingException("{ invalid json }")
    }

    @Test(expected = RuntimeException::class)
    fun testParseMissingTokenFieldThrowsRuntimeException() {
        // Arrange
        val jsonResponse =
            """
            {
                "${ConnectConstants.CONNECT_KEY_EXPIRES}": 3600
            }
            """.trimIndent()

        // Act & Assert
        parseTokenExpectingException(jsonResponse)
    }

    @Test(expected = IllegalStateException::class)
    fun testParseNullTokenField() {
        // Arrange
        val jsonResponse = createTokenJsonWithCustomExpires("null", 3600)

        // Act & Assert
        parseTokenExpectingException(jsonResponse)
    }

    @Test
    fun testParseWithInvalidExpiresInType() {
        // Arrange
        val jsonResponse = createTokenJsonWithCustomExpires("test_token", "\"not_a_number\"")

        // Act & Assert
        // optInt returns 0 for invalid strings, so this should work without throwing exception
        val (_, userRecord) = parseTokenAndAssertBasics(jsonResponse, "test_token")
    }

    @Test(expected = IllegalStateException::class)
    fun testParseWithNegativeExpiresIn() {
        // Arrange
        val jsonResponse = createTokenJsonWithCustomExpires("test_toen", -3600)

        // Act & Assert
        parseTokenExpectingException(jsonResponse)
    }

    @Test(expected = RuntimeException::class)
    fun testParseEmptyResponseThrowsRuntimeException() {
        // Arrange & Act & Assert
        parseTokenExpectingException("")
    }
}
