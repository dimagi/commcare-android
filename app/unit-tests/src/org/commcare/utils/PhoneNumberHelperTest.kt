package org.commcare.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for PhoneNumberHelper focusing on phone number validation, parsing, and formatting.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PhoneNumberHelperTest {
    private lateinit var context: Context
    private lateinit var phoneNumberHelper: PhoneNumberHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        phoneNumberHelper = PhoneNumberHelper.getInstance(context)
    }

    // ========== Phone Number Validation Tests ==========

    @Test
    fun testPhoneNumberValidation_invalidShortNumber() {
        // Arrange
        val countryCode = "+1"
        val phoneNumber = "123"
        val fullNumber = PhoneNumberHelper.buildPhoneNumber(countryCode, phoneNumber)

        // Act
        val isValid = phoneNumberHelper.isValidPhoneNumber(fullNumber)

        // Assert
        assertFalse("Too short phone number should be rejected", isValid)
    }

    @Test
    fun testPhoneNumberValidation_emptyNumber() {
        // Arrange
        val countryCode = "+1"
        val phoneNumber = ""
        val fullNumber = PhoneNumberHelper.buildPhoneNumber(countryCode, phoneNumber)

        // Act
        val isValid = phoneNumberHelper.isValidPhoneNumber(fullNumber)

        // Assert
        assertFalse("Empty phone number should be rejected", isValid)
    }

    @Test
    fun testPhoneNumberValidation_invalidFormat() {
        // Arrange
        val invalidNumber = "not-a-phone-number"

        // Act
        val isValid = phoneNumberHelper.isValidPhoneNumber(invalidNumber)

        // Assert
        assertFalse("Invalid format should be rejected", isValid)
    }

    // ========== Phone Number Building Tests ==========

    @Test
    fun testBuildPhoneNumber_concatenatesCorrectly() {
        // Arrange
        val countryCode = "+91"
        val phoneNumber = "9876543210"

        // Act
        val fullNumber = PhoneNumberHelper.buildPhoneNumber(countryCode, phoneNumber)

        // Assert
        assertEquals("+919876543210", fullNumber)
    }

    @Test
    fun testBuildPhoneNumber_removesSpecialCharacters() {
        // Arrange
        val countryCode = "+1"
        val phoneNumber = "(415) 123-4567"

        // Act
        val fullNumber = PhoneNumberHelper.buildPhoneNumber(countryCode, phoneNumber)

        // Assert
        assertEquals("+14151234567", fullNumber)
    }

    // ========== Phone Number Parsing Tests ==========

    @Test
    fun testInvalidNumber() {
        // Arrange
        val invalidPhoneNumber = "not-a-phone"

        // Act
        val countryCode = phoneNumberHelper.getCountryCode(invalidPhoneNumber)
        val nationalNumber = phoneNumberHelper.getNationalNumber(invalidPhoneNumber)

        // Assert
        assertEquals("Should return -1 for invalid country code", -1, countryCode)
        assertEquals("Should return -1 for invalid national number", -1L, nationalNumber)
    }


    @Test
    fun testShortInvalidNumber() {
        // Arrange
        val invalidPhoneNumber = "+1123"

        // Act
        val countryCode = phoneNumberHelper.getCountryCode(invalidPhoneNumber)
        val nationalNumber = phoneNumberHelper.getNationalNumber(invalidPhoneNumber)

        // Assert
        assertEquals("Should return -1 for invalid short number", -1, countryCode)
        assertEquals("Should return -1 for invalid national number", -1L, nationalNumber)
    }

    // ========== Country Code Formatting Tests ==========

    @Test
    fun testFormatCountryCode_addsPlus() {
        // Arrange
        val countryCode = 91

        // Act
        val formatted = phoneNumberHelper.formatCountryCode(countryCode)

        // Assert
        assertEquals("+91", formatted)
    }

    @Test
    fun testFormatCountryCode_zeroCode() {
        // Arrange
        val countryCode = 0

        // Act
        val formatted = phoneNumberHelper.formatCountryCode(countryCode)

        // Assert
        assertEquals("Should return empty string for zero", "", formatted)
    }

    @Test
    fun testFormatCountryCode_negativeCode() {
        // Arrange
        val countryCode = -1

        // Act
        val formatted = phoneNumberHelper.formatCountryCode(countryCode)

        // Assert
        assertEquals("Should return empty string for negative", "", formatted)
    }
}
