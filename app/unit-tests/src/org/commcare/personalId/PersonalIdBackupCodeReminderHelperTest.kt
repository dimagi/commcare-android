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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdBackupCodeReminderHelperTest {
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
        assertFalse(PersonalIdBackupCodeReminderHelper.isDue())
    }

    @Test
    fun `isDue returns false immediately after initialize`() {
        PersonalIdBackupCodeReminderHelper.initialize()
        assertFalse(PersonalIdBackupCodeReminderHelper.isDue())
    }

    @Test
    fun `isDue returns true when next due time is in the past`() {
        PersonalIdUserPreferences.setNextBackupCodeReminderDue(System.currentTimeMillis() - 1000L)
        assertTrue(PersonalIdBackupCodeReminderHelper.isDue())
    }

    @Test
    fun `initialize schedules first reminder 1 hour out`() {
        PersonalIdBackupCodeReminderHelper.initialize()
        val nextDue = PersonalIdUserPreferences.getNextBackupCodeReminderDue()
        assertTimeRange(nextDue, 1.hours.inWholeMilliseconds)
    }

    @Test
    fun `scheduleNext after first reminder uses 3-day interval`() {
        PersonalIdBackupCodeReminderHelper.initialize()
        PersonalIdBackupCodeReminderHelper.scheduleNext()
        val nextDue = PersonalIdUserPreferences.getNextBackupCodeReminderDue()
        assertTimeRange(nextDue, 3.days.inWholeMilliseconds)
    }

    @Test
    fun `scheduleNext after second reminder uses 7-day interval`() {
        PersonalIdBackupCodeReminderHelper.initialize()
        PersonalIdBackupCodeReminderHelper.scheduleNext()
        PersonalIdBackupCodeReminderHelper.scheduleNext()
        val nextDue = PersonalIdUserPreferences.getNextBackupCodeReminderDue()
        assertTimeRange(nextDue, 7.days.inWholeMilliseconds)
    }

    private fun assertTimeRange(
        nextDue: Long,
        duration: Long,
    ) {
        assertTrue(nextDue >= System.currentTimeMillis() + duration - 5000L)
        assertTrue(nextDue <= System.currentTimeMillis() + duration + 5000L)
    }
}
