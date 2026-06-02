package org.commcare.connect

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.commcare.CommCareTestApplication
import org.commcare.activities.CommCareSetupActivity
import org.commcare.activities.DispatchActivity
import org.commcare.activities.LoginActivity
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.dalvik.BuildConfig
import org.commcare.dalvik.R
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
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class SmsInviteLinkFlowTest {
    private val uuid = "opp-uuid-1234"
    private val validUri: Uri =
        Uri.parse("https://${BuildConfig.CCC_HOST}/users/invite_redirect/$uuid")
    private lateinit var savedStatus: PersonalIdManager.PersonalIdStatus

    @Before
    fun setUp() {
        val manager = PersonalIdManager.getInstance()
        savedStatus = manager.status
        manager.status = PersonalIdManager.PersonalIdStatus.LoggedIn

        mockkStatic(FirebaseAnalyticsUtil::class)
        every {
            FirebaseAnalyticsUtil.reportExternalAppLaunchEvent(any(), any(), any())
        } returns Unit

        every {
            FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener()
        } returns object : NavController.OnDestinationChangedListener {
            override fun onDestinationChanged(
                controller: NavController,
                destination: NavDestination,
                arguments: Bundle?,
            ) = Unit
        }

        mockkStatic(ConnectJobUtils::class)

        mockkStatic(MessageManager::class)
        every { MessageManager.retrieveMessages(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        PersonalIdManager.getInstance().status = savedStatus
        unmockkStatic(FirebaseAnalyticsUtil::class)
        unmockkStatic(ConnectJobUtils::class)
        unmockkStatic(MessageManager::class)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `SMS link to job in delivery status lands on delivery progress screen`() {
        assertEquals(
            ConnectConstants.CCC_DEST_DELIVERY_PROGRESS,
            launchSmsInvite(validUri, jobWithStatus(ConnectJobRecord.STATUS_DELIVERING)),
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `SMS link to job in learning status lands on learn progress screen`() {
        assertEquals(
            ConnectConstants.CCC_DEST_LEARN_PROGRESS,
            launchSmsInvite(validUri, jobWithStatus(ConnectJobRecord.STATUS_LEARNING)),
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `SMS link to available job lands on opportunity summary screen`() {
        assertEquals(
            ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE,
            launchSmsInvite(validUri, jobWithStatus(ConnectJobRecord.STATUS_AVAILABLE)),
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `SMS link to new available job lands on opportunity summary screen`() {
        assertEquals(
            ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE,
            launchSmsInvite(validUri, jobWithStatus(ConnectJobRecord.STATUS_AVAILABLE_NEW)),
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `SMS link with unknown job status falls back to generic opportunity destination`() {
        assertEquals(
            ConnectConstants.CCC_GENERIC_OPPORTUNITY,
            launchSmsInvite(validUri, jobWithStatus(ConnectJobRecord.STATUS_ALL_JOBS)),
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `SMS link with no matching job in DB falls back to generic opportunity destination`() {
        assertEquals(
            ConnectConstants.CCC_GENERIC_OPPORTUNITY,
            launchSmsInvite(validUri, jobInDb = null),
        )
    }

    @Test
    fun `SMS link redirection is re-resolved against synced job after opportunities load`() {
        // First pass mirrors ConnectActivity.handleSecureRedirect when the opp hasn't synced yet:
        // job is null, so the helper returns CCC_GENERIC_OPPORTUNITY unchanged and the fragment
        // is launched with that action.
        val preSync =
            ConnectJobHelper.resolveGenericOpportunityDestination(
                ConnectConstants.CCC_GENERIC_OPPORTUNITY,
                null,
                null,
            )
        assertEquals(ConnectConstants.CCC_GENERIC_OPPORTUNITY, preSync)

        // After ConnectUnlockFragment syncs and loads the invited opp, re-resolving with the now-
        // available job must produce the status-appropriate destination instead of falling through
        // to the generic jobs list.
        val postSync =
            ConnectJobHelper.resolveGenericOpportunityDestination(
                preSync,
                jobWithStatus(ConnectJobRecord.STATUS_DELIVERING),
                null,
            )
        assertEquals(ConnectConstants.CCC_DEST_DELIVERY_PROGRESS, postSync)
    }

    @Test
    fun `intent without VIEW action falls through to the landing page`() {
        val intent = Intent(Intent.ACTION_MAIN).setData(validUri)
        assertRoutedToLandingPage(intent)
    }

    @Test
    fun `intent without a data uri falls through to the landing page`() {
        assertRoutedToLandingPage(Intent(Intent.ACTION_VIEW))
    }

    @Test
    fun `http scheme falls through to the landing page`() {
        assertRoutedToLandingPage(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("http://${BuildConfig.CCC_HOST}/users/invite_redirect/$uuid"),
            ),
        )
    }

    @Test
    fun `mismatched host falls through to the landing page`() {
        assertRoutedToLandingPage(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://evil.example.com/users/invite_redirect/$uuid"),
            ),
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `path that is not invite_redirect falls through to the landing page`() {
        assertRoutedToLandingPage(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://${BuildConfig.CCC_HOST}/users/profile/$uuid"),
            ),
        )
    }

    @Test
    fun `path with extra segments falls through to the landing page`() {
        assertRoutedToLandingPage(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://${BuildConfig.CCC_HOST}/users/invite_redirect/$uuid/extra"),
            ),
        )
    }

    @Test
    fun `SMS link click while not logged in clears URI, reports analytics, and falls through to the landing page`() {
        PersonalIdManager.getInstance().status = PersonalIdManager.PersonalIdStatus.NotIntroduced
        val intent = Intent(Intent.ACTION_VIEW, validUri)

        assertRoutedToLandingPage(intent)

        // The URI was cleared so a future dispatch pass doesn't reprocess it
        assertNull(intent.data)
        verify {
            FirebaseAnalyticsUtil.reportExternalAppLaunchEvent(
                AnalyticsParamValue.OPP_INVITE_LINK,
                false,
                AnalyticsParamValue.OPP_INVITE_LINK_PERSONAL_ID_NOT_CONFIGURED,
            )
        }
    }

    /**
     * Drives the SMS-invite click flow end-to-end for [uri] and returns the REDIRECT_ACTION
     * argument that ConnectActivity passes to its start destination — i.e. the destination
     * the user would land on after the nav graph resolves.
     */
    private fun launchSmsInvite(
        uri: Uri,
        jobInDb: ConnectJobRecord?,
    ): String? {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        val dispatch =
            Robolectric
                .buildActivity(DispatchActivity::class.java, intent)
                .create()
                .resume()
                .get()

        val connectIntent = shadowOf(dispatch).nextStartedActivity
        assertNotNull("Dispatch did not start any activity for $uri", connectIntent)
        assertEquals(
            "Dispatch did not route SMS link to ConnectActivity",
            ConnectActivity::class.java.name,
            connectIntent.component?.className,
        )

        assertTrue(connectIntent.getBooleanExtra(ConnectConstants.FROM_SMS_INVITE_LINK, false))
        assertTrue(connectIntent.getBooleanExtra(ConnectConstants.SHOW_LAUNCH_BUTTON, false))
        assertEquals(uuid, connectIntent.getStringExtra(ConnectConstants.OPPORTUNITY_UUID))

        every { ConnectJobUtils.getCompositeJob(any(), eq(uuid)) } returns jobInDb

        val connect =
            Robolectric
                .buildActivity(ConnectActivity::class.java, connectIntent)
                .create()
                .resume()
                .get()

        val navHost =
            connect.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_connect)
                as NavHostFragment
        return navHost.navController.currentBackStackEntry
            ?.arguments
            ?.getString(ConnectConstants.REDIRECT_ACTION)
    }

    private fun assertRoutedToLandingPage(intent: Intent) {
        val dispatch =
            Robolectric
                .buildActivity(DispatchActivity::class.java, intent)
                .create()
                .resume()
                .get()
        val next = shadowOf(dispatch).nextStartedActivity
        assertNotNull("Dispatch did not start any activity", next)
        val nextClass = next.component?.className
        val landedOnLandingPage =
            nextClass == LoginActivity::class.java.name ||
                nextClass == CommCareSetupActivity::class.java.name
        assertTrue(
            "Expected Dispatch to land on LoginActivity or CommCareSetupActivity, but started $nextClass",
            landedOnLandingPage,
        )
    }

    private fun jobWithStatus(status: Int): ConnectJobRecord {
        val job = mockk<ConnectJobRecord>()
        every { job.status } returns status
        return job
    }
}
