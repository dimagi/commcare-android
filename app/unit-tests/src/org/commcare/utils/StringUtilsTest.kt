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
    fun `isValidEmail returns true for well-formed address`() {
        assertTrue(StringUtils.isValidEmail("user@example.com"))
        assertTrue(StringUtils.isValidEmail("first.last+tag@sub.example.co"))
    }

    @Test
    fun `isValidEmail returns true when surrounding whitespace is present`() {
        assertTrue(StringUtils.isValidEmail("  user@example.com  "))
    }

    @Test
    fun `isValidEmail returns false for null input`() {
        assertFalse(StringUtils.isValidEmail(null))
    }

    @Test
    fun `isValidEmail returns false for empty or blank input`() {
        assertFalse(StringUtils.isValidEmail(""))
        assertFalse(StringUtils.isValidEmail("   "))
    }

    @Test
    fun `isValidEmail returns false for malformed address`() {
        assertFalse(StringUtils.isValidEmail("not-an-email"))
        assertFalse(StringUtils.isValidEmail("missing@domain"))
        assertFalse(StringUtils.isValidEmail("@no-local.com"))
        assertFalse(StringUtils.isValidEmail("spaces in@example.com"))
    }

    @Test
    fun `isValidEmail returns false for single character top level domain`() {
        assertFalse(StringUtils.isValidEmail("user@gmail.c"))
        assertFalse(StringUtils.isValidEmail("user@sub.example.x"))
    }

    @Test
    fun `isValidEmail returns false when top level domain has a leading or trailing hyphen`() {
        assertFalse(StringUtils.isValidEmail("user@example.co-"))
        assertFalse(StringUtils.isValidEmail("user@example.-co"))
    }

    @Test
    fun `isValidEmail returns true for two character top level domain`() {
        assertTrue(StringUtils.isValidEmail("user@gmail.co"))
        assertTrue(StringUtils.isValidEmail("user@g.co"))
    }

    @Test
    fun `isValidEmail returns true for the longest top level domain in the IANA root`() {
        assertTrue(StringUtils.isValidEmail("user@example.xn--vermgensberatung-pwb"))
    }
}
