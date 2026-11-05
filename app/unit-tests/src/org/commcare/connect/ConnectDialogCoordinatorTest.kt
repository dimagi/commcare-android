package org.commcare.connect

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.commcare.CommCareTestApplication
import org.commcare.activities.CommCareActivity
import org.commcare.personalId.PersonalIdBackupCodeReminderHelper
import org.commcare.personalId.demographic.DemographicBannerBinder
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectDialogCoordinatorTest {
    private val activity = mockk<CommCareActivity<*>>()

    @Before
    fun setUp() {
        mockkStatic(PersonalIdBackupCodeReminderHelper::class)
        mockkObject(DemographicBannerBinder)
        every { DemographicBannerBinder.showIfDue(any(), any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `the demographic prompt is skipped when the backup code reminder is shown`() {
        every { PersonalIdBackupCodeReminderHelper.checkAndShowReminder(activity) } returns true

        ConnectDialogCoordinator.showDueDialog(activity)

        verify(exactly = 0) { DemographicBannerBinder.showIfDue(any(), any()) }
    }

    @Test
    fun `the demographic prompt is checked when no backup code reminder is shown`() {
        every { PersonalIdBackupCodeReminderHelper.checkAndShowReminder(activity) } returns false

        ConnectDialogCoordinator.showDueDialog(activity)

        verify(exactly = 1) { DemographicBannerBinder.showIfDue(activity, any()) }
    }
}
