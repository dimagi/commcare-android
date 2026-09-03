package org.commcare.personalId

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdReminderHelperTest {
    @Before
    fun setUp() {
        PersonalIdUserPreferences.clear()
    }

    @After
    fun tearDown() {
        PersonalIdUserPreferences.clear()
    }

    @Test
    fun `isDue returns false when uninitialized`() {
        assertFalse(PersonalIdReminderHelper.isDue())
    }

    @Test
    fun `isDue returns false immediately after initialize`() {
        PersonalIdReminderHelper.initialize()
        assertFalse(PersonalIdReminderHelper.isDue())
    }

    @Test
    fun `isDue returns true when next due time is in the past`() {
        PersonalIdUserPreferences.setNextReminderDue(System.currentTimeMillis() - 1000L)
        assertTrue(PersonalIdReminderHelper.isDue())
    }

    @Test
    fun `scheduleNext after first reminder uses 3-day interval`() {
        PersonalIdReminderHelper.initialize()
        PersonalIdReminderHelper.scheduleNext()
        val nextDue = PersonalIdUserPreferences.getNextReminderDue()
        val threeDaysMs = 3 * 24 * 60 * 60 * 1000L
        assertTrue(nextDue >= System.currentTimeMillis() + threeDaysMs - 5000L)
        assertTrue(nextDue <= System.currentTimeMillis() + threeDaysMs + 5000L)
    }

    @Test
    fun `scheduleNext after second reminder uses 7-day interval`() {
        PersonalIdReminderHelper.initialize()
        PersonalIdReminderHelper.scheduleNext() // first → stage becomes 1
        PersonalIdReminderHelper.scheduleNext() // second → 7d
        val nextDue = PersonalIdUserPreferences.getNextReminderDue()
        val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
        assertTrue(nextDue >= System.currentTimeMillis() + sevenDaysMs - 5000L)
        assertTrue(nextDue <= System.currentTimeMillis() + sevenDaysMs + 5000L)
    }

    @Test
    fun `clear removes state so isDue returns false`() {
        PersonalIdUserPreferences.setNextReminderDue(System.currentTimeMillis() - 1000L)
        PersonalIdReminderHelper.clear()
        assertFalse(PersonalIdReminderHelper.isDue())
    }
}
