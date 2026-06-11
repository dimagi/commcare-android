package org.commcare.connect

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.commcare.CommCareTestApplication
import org.commcare.activities.CommCareActivity
import org.commcare.android.database.connect.models.ConnectReleaseToggleRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.personalId.PersonalIdUserPreferences
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date
import java.util.concurrent.TimeUnit

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class EmailOfferHelperTest {
    private val context = CommCareTestApplication.instance()
    private val emailOtpSlug = "email_otp_verification"

    @Before
    fun setUp() {
        mockkStatic(ConnectAppDatabaseUtil::class)
        mockkStatic(ConnectUserDatabaseUtil::class)
        mockkStatic(PersonalIdManager::class)
        // Default: a logged-in PersonalID user. Individual tests can override.
        every { PersonalIdManager.getInstance().isloggedIn() } returns true
        PersonalIdUserPreferences.clear()
    }

    @After
    fun tearDown() {
        unmockkStatic(ConnectAppDatabaseUtil::class)
        unmockkStatic(ConnectUserDatabaseUtil::class)
        unmockkStatic(PersonalIdManager::class)
        PersonalIdUserPreferences.clear()
    }

    @Test
    fun `checkEmailCollection does nothing when the user is not logged in to PersonalID`() {
        every { PersonalIdManager.getInstance().isloggedIn() } returns false
        setEmailToggle(active = true)
        setUserEmail(null)
        val activity = mockActivity()

        EmailOfferHelper.checkEmailCollection(activity)

        assertEquals(0, PersonalIdUserPreferences.getEmailOfferCount())
        assertNull(PersonalIdUserPreferences.getLastEmailOfferDate())
        verify(exactly = 0) { activity.showAlertDialog(any()) }
    }

    @Test
    fun `checkEmailCollection does nothing when the email release toggle is off`() {
        setEmailToggle(active = false)
        setUserEmail(null)
        val activity = mockActivity()

        EmailOfferHelper.checkEmailCollection(activity)

        assertEquals(0, PersonalIdUserPreferences.getEmailOfferCount())
        assertNull(PersonalIdUserPreferences.getLastEmailOfferDate())
        verify(exactly = 0) { activity.showAlertDialog(any()) }
    }

    @Test
    fun `checkEmailCollection does nothing when the user already has an email`() {
        setEmailToggle(active = true)
        setUserEmail("user@example.com")
        val activity = mockActivity()

        EmailOfferHelper.checkEmailCollection(activity)

        assertEquals(0, PersonalIdUserPreferences.getEmailOfferCount())
        assertNull(PersonalIdUserPreferences.getLastEmailOfferDate())
        verify(exactly = 0) { activity.showAlertDialog(any()) }
    }

    @Test
    fun `checkEmailCollection does nothing when the offer has already been shown twice`() {
        setEmailToggle(active = true)
        setUserEmail(null)
        PersonalIdUserPreferences.setEmailOfferCount(2)
        val previousDate = daysAgo(10)
        PersonalIdUserPreferences.setLastEmailOfferDate(previousDate)
        val activity = mockActivity()

        EmailOfferHelper.checkEmailCollection(activity)

        assertEquals(2, PersonalIdUserPreferences.getEmailOfferCount())
        assertEquals(previousDate.time, PersonalIdUserPreferences.getLastEmailOfferDate()!!.time)
        verify(exactly = 0) { activity.showAlertDialog(any()) }
    }

    @Test
    fun `checkEmailCollection records the offer and shows the dialog on first eligibility`() {
        setEmailToggle(active = true)
        setUserEmail(null)
        val activity = mockActivity()

        EmailOfferHelper.checkEmailCollection(activity)

        assertEquals(1, PersonalIdUserPreferences.getEmailOfferCount())
        assertNotNull(PersonalIdUserPreferences.getLastEmailOfferDate())
        verify(exactly = 1) { activity.showAlertDialog(any()) }
    }

    @Test
    fun `checkEmailCollection records the offer and shows the dialog once the cooldown has elapsed`() {
        setEmailToggle(active = true)
        setUserEmail(null)
        PersonalIdUserPreferences.setEmailOfferCount(1)
        val oldDate = daysAgo(31)
        PersonalIdUserPreferences.setLastEmailOfferDate(oldDate)
        val activity = mockActivity()

        EmailOfferHelper.checkEmailCollection(activity)

        assertEquals(2, PersonalIdUserPreferences.getEmailOfferCount())
        val updatedDate = PersonalIdUserPreferences.getLastEmailOfferDate()
        assertNotNull(updatedDate)
        assert(updatedDate!!.time > oldDate.time) {
            "Expected last-offer date to be refreshed past the old 31-days-ago value"
        }
        verify(exactly = 1) { activity.showAlertDialog(any()) }
    }

    @Test
    fun `checkEmailCollection does nothing while still within the cooldown since the last offer`() {
        setEmailToggle(active = true)
        setUserEmail(null)
        PersonalIdUserPreferences.setEmailOfferCount(1)
        val recentDate = daysAgo(5)
        PersonalIdUserPreferences.setLastEmailOfferDate(recentDate)
        val activity = mockActivity()

        EmailOfferHelper.checkEmailCollection(activity)

        assertEquals(1, PersonalIdUserPreferences.getEmailOfferCount())
        assertEquals(recentDate.time, PersonalIdUserPreferences.getLastEmailOfferDate()!!.time)
        verify(exactly = 0) { activity.showAlertDialog(any()) }
    }

    // ---------- helpers ------------------------------------------------------------------

    private fun setEmailToggle(active: Boolean) {
        every { ConnectAppDatabaseUtil.getReleaseToggles(any()) } returns
            listOf(buildToggle(emailOtpSlug, active))
    }

    private fun setUserEmail(email: String?) {
        val user = ConnectUserRecord().apply { setEmail(email) }
        every { ConnectUserDatabaseUtil.getUser(any()) } returns user
    }

    private fun mockActivity(): CommCareActivity<*> {
        val activity = mockk<CommCareActivity<*>>(relaxed = true)
        every { activity.applicationContext } returns context
        return activity
    }

    private fun buildToggle(
        slug: String,
        active: Boolean,
    ): ConnectReleaseToggleRecord =
        ConnectReleaseToggleRecord.releaseToggleFromJson(
            slug,
            JSONObject().apply { put("active", active) },
        )

    private fun daysAgo(days: Int): Date = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong()))
}
