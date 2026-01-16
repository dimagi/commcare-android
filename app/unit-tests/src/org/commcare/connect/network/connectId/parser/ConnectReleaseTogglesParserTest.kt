package org.commcare.connect.network.connectId.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import org.commcare.CommCareTestApplication
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.connect.network.connect.parser.ConnectReleaseTogglesParser
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectReleaseTogglesParserTest {
    private lateinit var parser: ConnectReleaseTogglesParser

    @Before
    fun setUp() {
        parser = ConnectReleaseTogglesParser()
    }

    @Test
    fun testParseValidResponse() {
        // Arrange
        val mockDateString = "2026-01-26T00:00:00Z"
        val mockJson =
            """
            {
                "toggles": {
                    "feature_a": {
                        "active": true,
                        "created_at": "$mockDateString",
                        "modified_at": "$mockDateString"
                    },
                    "feature_b": {
                        "active": false,
                        "created_at": "$mockDateString",
                        "modified_at": "$mockDateString"
                    }
                }
            }
            """.trimIndent()

        val expectedDateString = "Mon Jan 26 00:00:00 GMT 2026"

        val inputStream = ByteArrayInputStream(mockJson.toByteArray())

        // Mock the DB storage call so it doesn't try to access a real DB.
        every { ConnectAppDatabaseUtil.storeReleaseToggles(any(), any()) } returns Unit

        // Act
        val result = parser.parse(200, inputStream, null)

        // Assert
        assertEquals(2, result.size)

        assertEquals("feature_a", result[0].slug)
        assertTrue(result[0].active)
        assertEquals(expectedDateString, result[0].createdAt.toString())
        assertEquals(expectedDateString, result[0].modifiedAt.toString())

        assertEquals("feature_b", result[1].slug)
        assertFalse(result[1].active)
        assertEquals(expectedDateString, result[1].createdAt.toString())
        assertEquals(expectedDateString, result[1].modifiedAt.toString())
    }

    @Test(expected = JSONException::class)
    fun testParseWithInvalidTogglesJson() {
        // Arrange
        val mockJson =
            """
            {
                "toggles": "not_an_object"
            }
            """.trimIndent()

        val inputStream = ByteArrayInputStream(mockJson.toByteArray())

        // Act
        parser.parse(200, inputStream, null)
    }

    @Test(expected = JSONException::class)
    fun testParseWithInvalidJson() {
        // Arrange
        val mockJson =
            """
            {
                "invalid_json"
            }
            """.trimIndent()

        val inputStream = ByteArrayInputStream(mockJson.toByteArray())

        // Act
        parser.parse(200, inputStream, null)
    }

    @Test
    fun testParseWithSingleInvalidToggleJson() {
        // Arrange
        val mockDateString = "2026-01-26T00:00:00Z"
        val mockJson =
            """
            {
                "toggles": {
                    "feature_a": {
                        "active": true,
                        "created_at": "$mockDateString",
                        "modified_at": "$mockDateString"
                    },
                    "feature_b": "not_an_object"
                }
            }
            """.trimIndent()

        val expectedDateString = "Mon Jan 26 00:00:00 GMT 2026"

        val inputStream = ByteArrayInputStream(mockJson.toByteArray())

        // Mock the DB storage call so it doesn't try to access a real DB.
        every { ConnectAppDatabaseUtil.storeReleaseToggles(any(), any()) } returns Unit

        // Act
        val result = parser.parse(200, inputStream, null)

        // Assert
        assertEquals(1, result.size)

        assertEquals("feature_a", result[0].slug)
        assertTrue(result[0].active)
        assertEquals(expectedDateString, result[0].createdAt.toString())
        assertEquals(expectedDateString, result[0].modifiedAt.toString())
    }

    @Test
    fun testParseWithInvalidIndividualTogglesJson() {
        // Arrange
        val mockJson =
            """
            {
                "toggles": {
                    "feature_a": "not_an_object",
                    "feature_b": "not_an_object"
                }
            }
            """.trimIndent()

        val inputStream = ByteArrayInputStream(mockJson.toByteArray())

        // Mock the DB storage call so it doesn't try to access a real DB.
        every { ConnectAppDatabaseUtil.storeReleaseToggles(any(), any()) } returns Unit

        // Act
        val result = parser.parse(200, inputStream, null)

        // Assert
        assertTrue(result.isEmpty())
    }
}
