package org.commcare.activities.connect

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import org.commcare.AppUtils
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.util.TestAppInstaller
import org.commcare.connect.ConnectAppUtils
import org.commcare.connect.ConnectConstants
import org.commcare.connect.MessageManager
import org.commcare.connect.PersonalIdSessionManager
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.rules.MainCoroutineRule
import org.commcare.tasks.DataPullTask
import org.commcare.utils.SessionRegistrationHelper
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * [ConnectActivity] as a home host: it keeps a coordinator whether or not a user session is live,
 * and offers the app actions only while one is.
 */
@ExperimentalCoroutinesApi
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class ConnectActivityHomeHostTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val uuid = "opp-uuid-1234"
    private lateinit var savedStatus: PersonalIdSessionManager.PersonalIdStatus

    @Before
    fun setUp() {
        val manager = PersonalIdSessionManager.getInstance()
        savedStatus = manager.status
        manager.status = PersonalIdSessionManager.PersonalIdStatus.LoggedIn

        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.reportConnectTabChange(any()) } returns Unit
        every { FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener() } returns
            object : NavController.OnDestinationChangedListener {
                override fun onDestinationChanged(
                    controller: NavController,
                    destination: NavDestination,
                    arguments: Bundle?,
                ) = Unit
            }

        val job = mockk<ConnectJobRecord>(relaxed = true)
        every { job.status } returns ConnectJobRecord.STATUS_DELIVERING
        mockkStatic(ConnectJobUtils::class)
        every { ConnectJobUtils.getCompositeJob(eq(uuid)) } returns job
        every { ConnectJobUtils.getPaymentsSortedByDate(any()) } returns emptyList()

        mockkStatic(MessageManager::class)
        every { MessageManager.retrieveMessages(any(), any()) } returns Unit

        val repository = mockk<ConnectRepository>(relaxed = true)
        every { repository.getDeliveryProgress(any(), any(), any()) } returns flowOf(DataState.Loading)
        mockkObject(ConnectRepository.Companion)
        every { ConnectRepository.getInstance() } returns repository

        mockkStatic(AppUtils::class)
        every { AppUtils.isAppInstalled(any()) } returns false
        mockkObject(ConnectAppUtils)
        every { ConnectAppUtils.downloadApp(any(), any()) } returns true
    }

    @After
    fun tearDown() {
        PersonalIdSessionManager.getInstance().status = savedStatus
        SessionRegistrationHelper.unregisterSessionExpiration()
        unmockkAll()
    }

    @Test
    fun `app actions are unavailable without a user session`() {
        val activity = launchConnect()

        assertFalse(activity.areAppActionsAvailable())
    }

    @Test
    fun `app actions are available while a user is logged in`() {
        logIn()

        val activity = launchConnect()

        assertTrue(activity.areAppActionsAvailable())
    }

    @Test
    fun `losing the session leaves the page open`() {
        logIn()
        val activity = launchConnect()
        while (shadowOf(activity).nextStartedActivity != null) {
            continue
        }

        broadcastExpiration()

        assertFalse(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `sync tasks report to the coordinator and show their own progress`() {
        val activity = launchConnect()
        activity.setWaitDialogEnabled(false)

        assertSame(activity.coordinator.sync, activity.receiver)
        assertNotNull(activity.generateProgressDialog(DataPullTask.DATA_PULL_TASK_ID))
        assertNull(activity.generateProgressDialog(NON_SYNC_TASK_ID))
    }

    private fun launchConnect(): ConnectActivity {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), ConnectActivity::class.java).apply {
                putExtra(ConnectConstants.GO_TO_JOB_STATUS, true)
                putExtra(ConnectConstants.OPPORTUNITY_UUID, uuid)
            }
        val activity = Robolectric.buildActivity(ConnectActivity::class.java, intent).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    private fun logIn() {
        TestAppInstaller.installAppAndLogin(APP_PATH, TEST_USER, TEST_PASSWORD)
    }

    /** What `CommCareApplication.expireUserSession` signals, without closing the fixture's session. */
    private fun broadcastExpiration() {
        val context = CommCareApplication.instance()
        SessionRegistrationHelper.registerSessionExpiration()
        context.sendBroadcast(
            Intent(SessionRegistrationHelper.USER_SESSION_EXPIRED).setPackage(context.packageName),
        )
        ShadowLooper.idleMainLooper()
    }

    private companion object {
        const val APP_PATH = "jr://resource/commcare-apps/index_and_cache_test/profile.ccpr"
        const val TEST_USER = "test"
        const val TEST_PASSWORD = "123"
        const val NON_SYNC_TASK_ID = -1
    }
}
