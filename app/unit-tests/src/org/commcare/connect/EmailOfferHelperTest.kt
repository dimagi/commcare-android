package org.commcare.connect

import android.content.Context
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
import org.commcare.personalId.PersonalIdPreferences
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [EmailOfferHelper]'s email-offer eligibility ([EmailOfferHelper.shouldOfferEmail])
 * and the offer entry point ([EmailOfferHelper.checkEmailCollection]).
 *
 * The release-toggle and user lookups are static DB reads, mocked with MockK (mirroring
 * ReleaseToggleHelperTest). The offer count / last-offer date are read through real
 * SharedPreferences backed by Robolectric, so the tests exercise the real persistence path.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class EmailOfferHelperTest {
    private val context: Context = CommCareTestApplication.instance()
    private val emailOtpSlug = "email_otp_verification"

    @Before
    fun setUp() {
        mockkStatic(ConnectAppDatabaseUtil::class)
        mockkStatic(ConnectUserDatabaseUtil::class)
        PersonalIdPreferences.clear(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(ConnectAppDatabaseUtil::class)
        unmockkStatic(ConnectUserDatabaseUtil::class)
        PersonalIdPreferences.clear(context)
    }

    // ---------- shouldOfferEmail ---------------------------------------------------------

    @Test
    fun `shouldOfferEmail returns false when the email release toggle is off`() {
        setEmailToggle(active = false)
        setUserEmail(null)

        assertFalse(EmailOfferHelper.shouldOfferEmail(context))
    }

    @Test
    fun `shouldOfferEmail returns false when the user already has an email`() {
        setEmailToggle(active = true)
        setUserEmail("user@example.com")

        assertFalse(EmailOfferHelper.shouldOfferEmail(context))
    }

    @Test
    fun `shouldOfferEmail returns false when the offer has already been shown twice`() {
        setEmailToggle(active = true)
        setUserEmail(null)
        PersonalIdPreferences.setEmailOfferCount(context, 2)

        assertFalse(EmailOfferHelper.shouldOfferEmail(context))
    }

    @Test
    fun `shouldOfferEmail returns true on first eligibility when no offer has been made yet`() {
        setEmailToggle(active = true)
        setUserEmail(null)

        assertTrue(EmailOfferHelper.shouldOfferEmail(context))
    }

    @Test
    fun `shouldOfferEmail returns true once the cooldown since the last offer has elapsed`() {
        setEmailToggle(active = true)
        setUserEmail(null)
        PersonalIdPreferences.setEmailOfferCount(context, 1)
        PersonalIdPreferences.setLastEmailOfferDate(context, daysAgo(31))

        assertTrue(EmailOfferHelper.shouldOfferEmail(context))
    }

    @Test
    fun `shouldOfferEmail returns false while still within the cooldown since the last offer`() {
        setEmailToggle(active = true)
        setUserEmail(null)
        PersonalIdPreferences.setEmailOfferCount(context, 1)
        PersonalIdPreferences.setLastEmailOfferDate(context, daysAgo(5))

        assertFalse(EmailOfferHelper.shouldOfferEmail(context))
    }

    // ---------- checkEmailCollection -----------------------------------------------------

    @Test
    fun `checkEmailCollection records the offer and shows the dialog when eligible`() {
        setEmailToggle(active = true)
        setUserEmail(null)
        val activity = mockActivity()

        EmailOfferHelper.checkEmailCollection(activity)

        assertEquals(1, PersonalIdPreferences.getEmailOfferCount(context))
        assertNotNull(PersonalIdPreferences.getLastEmailOfferDate(context))
        verify(exactly = 1) { activity.showAlertDialog(any()) }
    }

    @Test
    fun `checkEmailCollection does nothing when not eligible`() {
        setEmailToggle(active = false)
        setUserEmail(null)
        val activity = mockActivity()

        EmailOfferHelper.checkEmailCollection(activity)

        assertNull(PersonalIdPreferences.getEmailOfferCount(context))
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
