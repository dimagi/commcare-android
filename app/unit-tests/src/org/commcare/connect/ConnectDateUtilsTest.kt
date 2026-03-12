package org.commcare.connect

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectDateUtilsTest {
    // ── convertIsoDate ──────────────────────────────────────────────────────

    @Test
    fun convertIsoDate_validStandardFormat_returnsParsedDate() {
        val result = ConnectDateUtils.convertIsoDate("2024-06-15T10:30:00Z")
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun convertIsoDate_withFractionalSeconds_stripsSubsecondsAndParses() {
        // .123456 fractional seconds should be stripped before parsing
        val withFraction = ConnectDateUtils.convertIsoDate("2024-06-15T10:30:00.123456Z")
        val withoutFraction = ConnectDateUtils.convertIsoDate("2024-06-15T10:30:00Z")
        assertEquals(withoutFraction, withFraction)
    }

    @Test
    fun convertIsoDate_withPlusZeroOffset_normalisesToZAndParses() {
        // +00:00 should be treated the same as Z
        val withPlus = ConnectDateUtils.convertIsoDate("2024-06-15T10:30:00+00:00")
        val withZ = ConnectDateUtils.convertIsoDate("2024-06-15T10:30:00Z")
        assertEquals(withZ, withPlus)
    }

    @Test(expected = IllegalArgumentException::class)
    fun convertIsoDate_emptyInput_throwsIllegalArgumentException() {
        ConnectDateUtils.convertIsoDate("")
    }

    @Test(expected = ParseException::class)
    fun convertIsoDate_invalidFormat_throwsParseException() {
        ConnectDateUtils.convertIsoDate("not-a-date")
    }

    @Test
    fun convertIsoDate_withShortOutputStyle_returnsShorterString() {
        val medium = ConnectDateUtils.convertIsoDate("2024-06-15T10:30:00Z", DateFormat.MEDIUM)
        val short = ConnectDateUtils.convertIsoDate("2024-06-15T10:30:00Z", DateFormat.SHORT)
        // SHORT format should be equal to or shorter than MEDIUM
        assertTrue(short.length <= medium.length)
    }

    @Test
    fun convertIsoDate_reproducibleForSameInput() {
        val first = ConnectDateUtils.convertIsoDate("2024-01-01T00:00:00Z")
        val second = ConnectDateUtils.convertIsoDate("2024-01-01T00:00:00Z")
        assertEquals(first, second)
    }

    // ── parseIsoDateForSorting ──────────────────────────────────────────────

    @Test
    fun parseIsoDateForSorting_validDate_returnsDate() {
        val result = ConnectDateUtils.parseIsoDateForSorting("2024-06-15T10:30:00Z")
        assertNotNull(result)
    }

    @Test
    fun parseIsoDateForSorting_emptyString_returnsNull() {
        val result = ConnectDateUtils.parseIsoDateForSorting("")
        assertNull(result)
    }

    @Test
    fun parseIsoDateForSorting_invalidFormat_returnsNull() {
        val result = ConnectDateUtils.parseIsoDateForSorting("not-a-date")
        assertNull(result)
    }

    @Test
    fun parseIsoDateForSorting_parsesCorrectUtcEpoch() {
        // 2024-01-01T00:00:00Z == 1704067200000 ms epoch
        val result = ConnectDateUtils.parseIsoDateForSorting("2024-01-01T00:00:00Z")
        assertNotNull(result)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val expected = sdf.parse("2024-01-01T00:00:00Z")
        assertEquals(expected, result)
    }

    @Test
    fun parseIsoDateForSorting_ordersEarlierBeforeLater() {
        val earlier = ConnectDateUtils.parseIsoDateForSorting("2024-01-01T00:00:00Z")
        val later = ConnectDateUtils.parseIsoDateForSorting("2024-12-31T23:59:59Z")
        assertNotNull(earlier)
        assertNotNull(later)
        assertTrue(earlier!!.before(later))
    }
}
