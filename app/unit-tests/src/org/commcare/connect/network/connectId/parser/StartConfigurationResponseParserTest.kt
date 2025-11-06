package org.commcare.connect.network.connectId.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class StartConfigurationResponseParserTest {
    private lateinit var parser: StartConfigurationResponseParser
    private lateinit var sessionData: PersonalIdSessionData

    @Before
    fun setUp() {
        parser = StartConfigurationResponseParser()
        sessionData = PersonalIdSessionData()
    }

    @Test
    fun testParseCompleteValidResponse() {
        // Arrange
        val json =
            JSONObject().apply {
                put("required_lock", "pin")
                put("demo_user", true)
                put("token", "test-token-123")
                put("sms_method", "gateway")
                put("failure_code", "test-failure")
                put("failure_subcode", "test-subcode")
                put("otp_fallback", true)
            }

        // Act
        parser.parse(json, sessionData)

        // Assert
        assertEquals("pin", sessionData.requiredLock)
        assertEquals(true, sessionData.demoUser)
        assertEquals("test-token-123", sessionData.token)
        assertEquals("gateway", sessionData.smsMethod)
        assertEquals("test-failure", sessionData.sessionFailureCode)
        assertEquals("test-subcode", sessionData.sessionFailureSubcode)
        assertTrue(sessionData.otpFallback)
    }

    @Test
    fun testParseWithDefaultValues() {
        // Arrange
        val json = JSONObject()
        // Not setting any fields to test default values

        // Act
        parser.parse(json, sessionData)

        // Assert
        assertNull(sessionData.requiredLock)
        assertFalse(sessionData.demoUser ?: true) // Check for nullable Boolean
        assertNull(sessionData.token)
        assertNull(sessionData.smsMethod)
        assertNull(sessionData.sessionFailureCode)
        assertNull(sessionData.sessionFailureSubcode)
        assertFalse(sessionData.otpFallback)
    }

    @Test
    fun testParseWithPartialData() {
        // Arrange
        val json =
            JSONObject().apply {
                put("required_lock", "biometric")
                put("token", "partial-token")
                // Missing other fields
            }

        // Act
        parser.parse(json, sessionData)

        // Assert
        assertEquals("biometric", sessionData.requiredLock)
        assertEquals("partial-token", sessionData.token)
        assertFalse(sessionData.demoUser ?: true)  // Check for nullable Boolean
        assertNull(sessionData.smsMethod)
        assertNull(sessionData.sessionFailureCode)
        assertNull(sessionData.sessionFailureSubcode)
        assertFalse(sessionData.otpFallback)
    }

    @Test
    fun testParseWithEmptyStrings() {
        // Arrange
        val json =
            JSONObject().apply {
                put("required_lock", "")
                put("token", "")
                put("sms_method", "")
                put("failure_code", "")
                put("failure_subcode", "")
            }

        // Act
        parser.parse(json, sessionData)

        // Assert
        assertEquals("", sessionData.requiredLock)
        assertEquals("", sessionData.token)
        assertEquals("", sessionData.smsMethod)
        assertEquals("", sessionData.sessionFailureCode)
        assertEquals("", sessionData.sessionFailureSubcode)
    }

    @Test
    fun testParseValidRequiredLockTypeAsPin() {
        val jsonPin =
            JSONObject().apply {
                put("required_lock", PersonalIdSessionData.PIN)
            }
        parser.parse(jsonPin, sessionData)
        assertEquals(PersonalIdSessionData.PIN, sessionData.requiredLock)
    }

    @Test
    fun testParseValidRequiredLockTypeAsBiometric() {
        val jsonBiometric =
            JSONObject().apply {
                put("required_lock", PersonalIdSessionData.BIOMETRIC_TYPE)
            }
        parser.parse(jsonBiometric, sessionData)
        assertEquals(PersonalIdSessionData.BIOMETRIC_TYPE, sessionData.requiredLock)
    }

    @Test
    fun testParseTruthyBooleanValues() {
        // Test true values
        val json =
            JSONObject().apply {
                put("demo_user", true)
                put("otp_fallback", true)
            }
        parser.parse(json, sessionData)
        assertEquals(true, sessionData.demoUser)
        assertTrue(sessionData.otpFallback)
    }

    @Test
    fun testParseFalsyBooleanValues() {
        val json =
            JSONObject().apply {
                put("demo_user", false)
                put("otp_fallback", false)
            }
        parser.parse(json, sessionData)
        assertEquals(false, sessionData.demoUser)
        assertFalse(sessionData.otpFallback)
    }

    @Test
    fun testParseWithExtraFields() {
        // Arrange
        val json =
            JSONObject().apply {
                put("required_lock", "pin")
                put("token", "test-token")
                put("extra_field", "should-be-ignored")
                put("another_field", 123)
            }

        // Act
        parser.parse(json, sessionData)

        // Assert - Should only set known fields, ignore extra ones
        assertEquals("pin", sessionData.requiredLock)
        assertEquals("test-token", sessionData.token)
        assertFalse(sessionData.demoUser ?: true) // handle nullable Boolean
        assertNull(sessionData.smsMethod)
    }

    @Test
    fun testParsePreservesExistingData() {
        // Arrange - Set some initial data
        sessionData.apply {
            personalId = "existing-id"
            userName = "existing-user"
            phoneNumber = "123-456-7890"
        }

        val json =
            JSONObject().apply {
                put("token", "new-token")
                put("demo_user", true)
            }

        // Act
        parser.parse(json, sessionData)

        // Assert - New data should be set, existing data preserved
        assertEquals("new-token", sessionData.token)
        assertEquals(true, sessionData.demoUser)
        assertEquals("existing-id", sessionData.personalId)
        assertEquals("existing-user", sessionData.userName)
        assertEquals("123-456-7890", sessionData.phoneNumber)
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
                put("token", "test-token")
            }

        // Act - Should throw NullPointerException when sessionData is null
        parser.parse(json, null)
    }

    @Test
    fun testParseOverwritesPreviousValues() {
        // Arrange
        val json1 =
            JSONObject().apply {
                put("required_lock", "pin")
                put("token", "first-token")
            }

        val json2 =
            JSONObject().apply {
                put("demo_user", true)
                put("sms_method", "gateway")
                put("token", "second-token")
            }

        // Act
        parser.parse(json1, sessionData)
        parser.parse(json2, sessionData)

        // Assert - Second parse should update all fields based on what's in the JSON
        assertNull(sessionData.requiredLock) // Not in second JSON, so set to null
        assertEquals("second-token", sessionData.token) // Updated in second parse
        assertEquals(true, sessionData.demoUser) // From second parse
        assertEquals("gateway", sessionData.smsMethod) // From second parse
    }
}
