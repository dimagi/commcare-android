package org.commcare.connect.network.connectId.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class CompleteProfileResponseParserTest {
    private lateinit var parser: CompleteProfileResponseParser
    private lateinit var sessionData: PersonalIdSessionData

    @Before
    fun setUp() {
        parser = CompleteProfileResponseParser()
        sessionData = PersonalIdSessionData()
    }

    @Test
    fun testParseCompleteValidResponse() {
        // Arrange
        val json =
            JSONObject().apply {
                put("username", "completed-user-123")
                put("db_key", "complete-db-key-456")
                put("password", "complete-password")
                put("invited_user", true)
            }

        // Act
        parser.parse(json, sessionData)

        // Assert
        assertEquals("completed-user-123", sessionData.personalId)
        assertEquals("complete-db-key-456", sessionData.dbKey)
        assertEquals("complete-password", sessionData.oauthPassword)
        assertTrue(sessionData.invitedUser)
    }

    @Test(expected = NullPointerException::class)
    fun testParseWithMissingUsername() {
        // Arrange - Missing required username field
        val json =
            JSONObject().apply {
                put("db_key", "test-db-key")
                put("password", "test-password")
            }

        // Act - Should throw NullPointerException due to Objects.requireNonNull
        parser.parse(json, sessionData)
    }

    @Test(expected = NullPointerException::class)
    fun testParseWithMissingDbKey() {
        // Arrange - Missing required db_key field
        val json =
            JSONObject().apply {
                put("username", "test-user")
                put("password", "test-password")
            }

        // Act - Should throw NullPointerException due to Objects.requireNonNull
        parser.parse(json, sessionData)
    }

    @Test(expected = NullPointerException::class)
    fun testParseWithMissingPassword() {
        // Arrange - Missing required password field
        val json =
            JSONObject().apply {
                put("username", "test-user")
                put("db_key", "test-db-key")
            }

        // Act - Should throw NullPointerException due to Objects.requireNonNull
        parser.parse(json, sessionData)
    }

    @Test(expected = NullPointerException::class)
    fun testParseWithNullUsername() {
        // Arrange
        val json =
            JSONObject().apply {
                put("username", JSONObject.NULL)
                put("db_key", "test-db-key")
                put("password", "test-password")
            }

        // Act - Should throw NullPointerException due to Objects.requireNonNull
        parser.parse(json, sessionData)
    }

    @Test(expected = NullPointerException::class)
    fun testParseWithNullDbKey() {
        // Arrange
        val json =
            JSONObject().apply {
                put("username", "test-user")
                put("db_key", JSONObject.NULL)
                put("password", "test-password")
            }

        // Act - Should throw NullPointerException due to Objects.requireNonNull
        parser.parse(json, sessionData)
    }

    @Test(expected = NullPointerException::class)
    fun testParseWithNullPassword() {
        // Arrange
        val json =
            JSONObject().apply {
                put("username", "test-user")
                put("db_key", "test-db-key")
                put("password", JSONObject.NULL)
            }

        // Act - Should throw NullPointerException due to Objects.requireNonNull
        parser.parse(json, sessionData)
    }

    @Test(expected = IllegalStateException::class)
    fun testParseWithEmptyUsername() {
        // Arrange
        val json =
            JSONObject().apply {
                put("username", "")
                put("db_key", "db_key")
                put("password", "password")
            }

        // Act
        parser.parse(json, sessionData)
    }

    @Test(expected = IllegalStateException::class)
    fun testParseWithEmptyDbKey() {
        // Arrange
        val json =
            JSONObject().apply {
                put("username", "username")
                put("db_key", "")
                put("password", "password")
            }

        // Act
        parser.parse(json, sessionData)
    }

    @Test(expected = IllegalStateException::class)
    fun testParseWithEmptyPassword() {
        // Arrange
        val json =
            JSONObject().apply {
                put("username", "username")
                put("db_key", "dbkey")
                put("password", "")
            }

        // Act
        parser.parse(json, sessionData)
    }
}
