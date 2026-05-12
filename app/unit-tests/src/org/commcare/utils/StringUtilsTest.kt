package org.commcare.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class StringUtilsTest {
    @Test
    fun isValidEmail_returnsTrueForWellFormedAddress() {
        assertTrue(StringUtils.isValidEmail("user@example.com"))
        assertTrue(StringUtils.isValidEmail("first.last+tag@sub.example.co"))
    }

    @Test
    fun isValidEmail_trimsSurroundingWhitespace() {
        assertTrue(StringUtils.isValidEmail("  user@example.com  "))
    }

    @Test
    fun isValidEmail_returnsFalseForNull() {
        assertFalse(StringUtils.isValidEmail(null))
    }

    @Test
    fun isValidEmail_returnsFalseForEmptyOrBlank() {
        assertFalse(StringUtils.isValidEmail(""))
        assertFalse(StringUtils.isValidEmail("   "))
    }

    @Test
    fun isValidEmail_returnsFalseForMalformedAddress() {
        assertFalse(StringUtils.isValidEmail("not-an-email"))
        assertFalse(StringUtils.isValidEmail("missing@domain"))
        assertFalse(StringUtils.isValidEmail("@no-local.com"))
        assertFalse(StringUtils.isValidEmail("spaces in@example.com"))
    }
}
