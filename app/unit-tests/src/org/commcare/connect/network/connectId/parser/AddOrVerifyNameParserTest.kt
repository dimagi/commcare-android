package org.commcare.connect.network.connectId.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for AddOrVerifyNameParser
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class AddOrVerifyNameParserTest {
    private lateinit var parser: AddOrVerifyNameParser
    private lateinit var sessionData: PersonalIdSessionData

    @Before
    fun setUp() {
        parser = AddOrVerifyNameParser()
        sessionData = PersonalIdSessionData()
    }

    @Test
    fun testParseCompleteValidResponse() {
        // Arrange
        val json =
            JSONObject().apply {
                put("account_exists", true)
                put(
                    "photo",
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg" +
                        "==",
                )
            }

        // Act
        parser.parse(json, sessionData)

        // Assert
        assertEquals(true, sessionData.accountExists)
        assertEquals(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
            sessionData.photoBase64,
        )
    }

    @Test
    fun testParseWithDefaultValues() {
        // Arrange
        val json = JSONObject()
        // Not setting any fields to test default values

        // Act
        parser.parse(json, sessionData)

        // Assert
        assertEquals(false, sessionData.accountExists)
        assertNull(sessionData.photoBase64)
    }

    @Test
    fun testParseWithPartialData() {
        // Arrange
        val json =
            JSONObject().apply {
                put("account_exists", true)
                // Missing photo field
            }

        // Act
        parser.parse(json, sessionData)

        // Assert
        assertEquals(true, sessionData.accountExists)
        assertNull(sessionData.photoBase64)
    }

    @Test
    fun testParseWithEmptyStrings() {
        // Arrange
        val json =
            JSONObject().apply {
                put("photo", "")
            }

        // Act
        parser.parse(json, sessionData)

        // Assert
        assertEquals("", sessionData.photoBase64)
        assertEquals(false, sessionData.accountExists)
    }

    @Test
    fun testParseWithNullValues() {
        // Arrange
        val json =
            JSONObject().apply {
                put("account_exists", JSONObject.NULL)
                put("photo", JSONObject.NULL)
            }

        // Act
        parser.parse(json, sessionData)

        // Assert
        assertEquals(false, sessionData.accountExists) // optBoolean returns false for null
        assertNull(sessionData.photoBase64)
    }

    @Test
    fun testParseWithLargePhotoData() {
        // Arrange - Test with a larger photo string
        val largePhotoData = StringBuilder()
        repeat(100000) {
            largePhotoData.append("a")
        }

        val json =
            JSONObject().apply {
                put("account_exists", true)
                put("photo", largePhotoData.toString())
            }

        // Act
        parser.parse(json, sessionData)

        // Assert
        assertEquals(true, sessionData.accountExists)
        assertEquals(largePhotoData.toString(), sessionData.photoBase64)
        assertEquals(100000, sessionData.photoBase64?.length)
    }

    @Test(expected = NullPointerException::class)
    fun testParseWithNullJSON() {
        // Act - Should throw NullPointerException when trying to parse null JSON
        parser.parse(null, sessionData)
    }

    @Test(expected = NullPointerException::class)
    fun testParseWithNullSessionData() {
        // Arrange
        val json =
            JSONObject().apply {
                put("account_exists", true)
            }

        // Act - Should throw NullPointerException when sessionData is null
        parser.parse(json, null)
    }
}
