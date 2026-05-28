package org.commcare.connect

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.dalvik.BuildConfig
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests for [ConnectJobHelper.retrieveConnectOppInviteIntentIfPresent] and
 * [ConnectJobHelper.resolveGenericOpportunityDestination], i.e. the two functions that decide
 * which final activity / destination a launching intent + opportunity status should resolve to.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectJobHelperOppInviteTest {
    private val context: Context = CommCareTestApplication.instance()
    private val uuid = "opp-uuid-1234"
    private val validUri: Uri =
        Uri.parse("https://${BuildConfig.CCC_HOST}/users/invite_redirect/$uuid")
    private lateinit var savedStatus: PersonalIdManager.PersonalIdStatus

    @Before
    fun setUp() {
        // Default state across tests is "logged in" so init() is a no-op and isloggedIn() == true.
        // The "not logged in" test overrides this.
        val manager = PersonalIdManager.getInstance()
        savedStatus = manager.status
        manager.status = PersonalIdManager.PersonalIdStatus.LoggedIn

        mockkStatic(FirebaseAnalyticsUtil::class)
        every {
            FirebaseAnalyticsUtil.reportExternalAppLaunchEvent(any(), any(), any())
        } returns Unit
    }

    @After
    fun tearDown() {
        PersonalIdManager.getInstance().status = savedStatus
        unmockkStatic(FirebaseAnalyticsUtil::class)
    }

    // --- retrieveConnectOppInviteIntentIfPresent -----------------------------------------------

    @Test
    fun `returns null when action is not VIEW`() {
        val intent = Intent(Intent.ACTION_MAIN).setData(validUri)
        assertNull(ConnectJobHelper.retrieveConnectOppInviteIntentIfPresent(context, intent))
    }

    @Test
    fun `returns null when data uri is missing`() {
        val intent = Intent(Intent.ACTION_VIEW)
        assertNull(ConnectJobHelper.retrieveConnectOppInviteIntentIfPresent(context, intent))
    }

    @Test
    fun `returns null when scheme is not https`() {
        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("http://${BuildConfig.CCC_HOST}/users/invite_redirect/$uuid"),
            )
        assertNull(ConnectJobHelper.retrieveConnectOppInviteIntentIfPresent(context, intent))
    }

    @Test
    fun `returns null when host does not match CCC_HOST`() {
        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://evil.example.com/users/invite_redirect/$uuid"),
            )
        assertNull(ConnectJobHelper.retrieveConnectOppInviteIntentIfPresent(context, intent))
    }

    @Test
    fun `returns null when path prefix does not match invite_redirect`() {
        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://${BuildConfig.CCC_HOST}/users/profile/$uuid"),
            )
        assertNull(ConnectJobHelper.retrieveConnectOppInviteIntentIfPresent(context, intent))
    }

    @Test
    fun `returns null when path has wrong number of segments`() {
        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://${BuildConfig.CCC_HOST}/users/invite_redirect/$uuid/extra"),
            )
        assertNull(ConnectJobHelper.retrieveConnectOppInviteIntentIfPresent(context, intent))
    }

    @Test
    fun `returns null and reports analytics when user is not logged in`() {
        PersonalIdManager.getInstance().status = PersonalIdManager.PersonalIdStatus.NotIntroduced
        val intent = Intent(Intent.ACTION_VIEW, validUri)

        assertNull(ConnectJobHelper.retrieveConnectOppInviteIntentIfPresent(context, intent))
        // The URI should still be cleared so future dispatch() doesn't reprocess it
        assertNull(intent.data)
        verify {
            FirebaseAnalyticsUtil.reportExternalAppLaunchEvent(
                AnalyticsParamValue.OPP_INVITE_LINK,
                false,
                AnalyticsParamValue.OPP_INVITE_LINK_PERSONAL_ID_NOT_CONFIGURED,
            )
        }
    }

    @Test
    fun `returns ConnectActivity intent with expected extras when valid and logged in`() {
        val intent = Intent(Intent.ACTION_VIEW, validUri)

        val result = ConnectJobHelper.retrieveConnectOppInviteIntentIfPresent(context, intent)

        assertNotNull(result)
        assertEquals(ConnectActivity::class.java.name, result!!.component?.className)
        assertEquals(
            ConnectConstants.CCC_GENERIC_OPPORTUNITY,
            result.getStringExtra(ConnectConstants.REDIRECT_ACTION),
        )
        assertEquals(uuid, result.getStringExtra(ConnectConstants.OPPORTUNITY_UUID))
        assertTrue(result.getBooleanExtra(ConnectConstants.FROM_SMS_INVITE_LINK, false))
        assertTrue(result.getBooleanExtra(ConnectConstants.SHOW_LAUNCH_BUTTON, false))
        // Source intent URI is cleared once consumed
        assertNull(intent.data)
    }

    // --- resolveGenericOpportunityDestination --------------------------------------------------

    @Test
    fun `resolve returns input action when current action is not generic-opportunity`() {
        val job = jobWithStatus(ConnectJobRecord.STATUS_DELIVERING)
        assertEquals(
            "some_other_action",
            ConnectJobHelper.resolveGenericOpportunityDestination("some_other_action", job, null),
        )
    }

    @Test
    fun `resolve returns input action when job is null`() {
        assertEquals(
            ConnectConstants.CCC_GENERIC_OPPORTUNITY,
            ConnectJobHelper.resolveGenericOpportunityDestination(
                ConnectConstants.CCC_GENERIC_OPPORTUNITY,
                null,
                null,
            ),
        )
    }

    @Test
    fun `resolve routes delivering job with payment uuid to payments`() {
        val job = jobWithStatus(ConnectJobRecord.STATUS_DELIVERING)
        assertEquals(
            ConnectConstants.CCC_DEST_PAYMENTS,
            ConnectJobHelper.resolveGenericOpportunityDestination(
                ConnectConstants.CCC_GENERIC_OPPORTUNITY,
                job,
                "payment-1",
            ),
        )
    }

    @Test
    fun `resolve routes delivering job without payment uuid to delivery progress`() {
        val job = jobWithStatus(ConnectJobRecord.STATUS_DELIVERING)
        assertEquals(
            ConnectConstants.CCC_DEST_DELIVERY_PROGRESS,
            ConnectJobHelper.resolveGenericOpportunityDestination(
                ConnectConstants.CCC_GENERIC_OPPORTUNITY,
                job,
                null,
            ),
        )
    }

    @Test
    fun `resolve treats empty payment uuid the same as missing one`() {
        val job = jobWithStatus(ConnectJobRecord.STATUS_DELIVERING)
        assertEquals(
            ConnectConstants.CCC_DEST_DELIVERY_PROGRESS,
            ConnectJobHelper.resolveGenericOpportunityDestination(
                ConnectConstants.CCC_GENERIC_OPPORTUNITY,
                job,
                "",
            ),
        )
    }

    @Test
    fun `resolve routes learning job to learn progress`() {
        val job = jobWithStatus(ConnectJobRecord.STATUS_LEARNING)
        assertEquals(
            ConnectConstants.CCC_DEST_LEARN_PROGRESS,
            ConnectJobHelper.resolveGenericOpportunityDestination(
                ConnectConstants.CCC_GENERIC_OPPORTUNITY,
                job,
                null,
            ),
        )
    }

    @Test
    fun `resolve routes available job to opportunity summary page`() {
        val job = jobWithStatus(ConnectJobRecord.STATUS_AVAILABLE)
        assertEquals(
            ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE,
            ConnectJobHelper.resolveGenericOpportunityDestination(
                ConnectConstants.CCC_GENERIC_OPPORTUNITY,
                job,
                null,
            ),
        )
    }

    @Test
    fun `resolve routes available-new job to opportunity summary page`() {
        val job = jobWithStatus(ConnectJobRecord.STATUS_AVAILABLE_NEW)
        assertEquals(
            ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE,
            ConnectJobHelper.resolveGenericOpportunityDestination(
                ConnectConstants.CCC_GENERIC_OPPORTUNITY,
                job,
                null,
            ),
        )
    }

    @Test
    fun `resolve returns input action for unrecognized status`() {
        val job = jobWithStatus(ConnectJobRecord.STATUS_ALL_JOBS)
        assertEquals(
            ConnectConstants.CCC_GENERIC_OPPORTUNITY,
            ConnectJobHelper.resolveGenericOpportunityDestination(
                ConnectConstants.CCC_GENERIC_OPPORTUNITY,
                job,
                null,
            ),
        )
    }

    private fun jobWithStatus(status: Int): ConnectJobRecord {
        val job = mockk<ConnectJobRecord>()
        every { job.status } returns status
        return job
    }
}
