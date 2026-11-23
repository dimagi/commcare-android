package org.commcare.personalId.demographic

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class DateOfBirthTest {
    private lateinit var originalTimeZone: TimeZone

    // MaterialDatePicker reports the chosen day as midnight UTC.
    private val sixNovember1979Utc = 310_694_400_000L

    @Before
    fun saveTimeZone() {
        originalTimeZone = TimeZone.getDefault()
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `the picked day is stored as an ISO date`() {
        assertEquals("1979-11-06", DateOfBirth.fromPickerSelection(sixNovember1979Utc))
    }

    @Test
    fun `the stored date gives back the picker's midnight UTC`() {
        assertEquals(sixNovember1979Utc, DateOfBirth.toUtcMillis("1979-11-06"))
    }

    @Test
    fun `the date does not shift a day in timezones behind or ahead of UTC`() {
        listOf("America/New_York", "Asia/Kolkata", "Pacific/Kiritimati").forEach { zone ->
            TimeZone.setDefault(TimeZone.getTimeZone(zone))

            assertEquals(zone, "1979-11-06", DateOfBirth.fromPickerSelection(sixNovember1979Utc))
            assertEquals(zone, "6 Nov 1979", DateOfBirth.forDisplay("1979-11-06", Locale.UK))
        }
    }

    @Test
    fun `an unset or unreadable date gives nothing`() {
        assertNull(DateOfBirth.toUtcMillis(null))
        assertNull(DateOfBirth.toUtcMillis("6 November"))
        assertNull(DateOfBirth.forDisplay(null))
    }
}
