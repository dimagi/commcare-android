package org.commcare.activities.connect

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import org.commcare.AppUtils
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.activities.MenuActivity
import org.commcare.activities.ReportProblemActivity
import org.commcare.activities.SessionAwarePreferenceActivity
import org.commcare.android.database.connect.models.ConnectAppRecord
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.mocks.FormAndDataSyncerFake
import org.commcare.android.util.TestAppInstaller
import org.commcare.connect.ConnectAppUtils
import org.commcare.connect.ConnectConstants
import org.commcare.connect.MessageManager
import org.commcare.connect.PersonalIdSessionManager
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.home.HomeRequestCodes
import org.commcare.home.HomeResultCodes
import org.commcare.preferences.AdvancedActionsPreferences
import org.commcare.preferences.HiddenPreferences
import org.commcare.rules.MainCoroutineRule
import org.commcare.tasks.DataPullTask
import org.commcare.utils.ConnectivityStatus
import org.commcare.utils.SessionRegistrationHelper
import org.javarosa.core.services.locale.Localization
import org.junit.After
import org.junit.Assert.assertEquals
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
import org.robolectric.shadows.ShadowToast

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
    private lateinit var job: ConnectJobRecord
    private lateinit var seatedApp: ConnectAppRecord
    private lateinit var repository: ConnectRepository

    @Before
    fun setUp() {
        val manager = PersonalIdSessionManager.getInstance()
        savedStatus = manager.status
        manager.status = PersonalIdSessionManager.PersonalIdStatus.LoggedIn

        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.reportConnectTabChange(any()) } returns Unit
        every { FirebaseAnalyticsUtil.reportOptionsMenuItemClick(any(), any()) } returns Unit
        every { FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener() } returns
            object : NavController.OnDestinationChangedListener {
                override fun onDestinationChanged(
                    controller: NavController,
                    destination: NavDestination,
                    arguments: Bundle?,
                ) = Unit
            }

        job = mockk(relaxed = true)
        every { job.status } returns ConnectJobRecord.STATUS_DELIVERING
        every { job.jobUUID } returns uuid
        seatedApp = mockk(relaxed = true)
        every { seatedApp.jobUUID } returns uuid
        mockkStatic(ConnectJobUtils::class)
        every { ConnectJobUtils.getCompositeJob(eq(uuid)) } returns job
        every { ConnectJobUtils.getSeatedAppRecord() } returns seatedApp
        every { ConnectJobUtils.getPaymentsSortedByDate(any()) } returns emptyList()

        mockkStatic(MessageManager::class)
        every { MessageManager.retrieveMessages(any(), any()) } returns Unit

        repository = mockk(relaxed = true)
        every { repository.getDeliveryProgress(any(), any(), any()) } returns flowOf(DataState.Loading)
        mockkObject(ConnectRepository.Companion)
        every { ConnectRepository.getInstance() } returns repository

        mockkStatic(AppUtils::class)
        every { AppUtils.isAppInstalled(any()) } returns false
        mockkObject(ConnectAppUtils)
        every { ConnectAppUtils.downloadApp(any(), any()) } returns true

        mockkStatic(ConnectivityStatus::class)
        every { ConnectivityStatus.isNetworkAvailable(any()) } returns true
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
    fun `no app actions for another opp`() {
        logIn()
        every { seatedApp.jobUUID } returns "another-opp-uuid"

        val activity = launchConnect()

        assertFalse(activity.areAppActionsAvailable())
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

    @Test
    fun `the app actions menu is hidden without a user session`() {
        val activity = launchConnect()

        APP_ACTION_ITEMS.forEach { assertFalse(isMenuItemVisible(activity, it)) }
    }

    @Test
    fun `the app actions menu shows localized titles while a user is logged in`() {
        logIn()

        val activity = launchConnect()

        APP_ACTION_ITEMS.minus(R.id.action_update_commcare).forEach { assertTrue(isMenuItemVisible(activity, it)) }
        assertEquals("Report an Issue", menuItem(activity, R.id.action_report_problem).title.toString())
        assertEquals("Settings", menuItem(activity, R.id.action_preferences).title.toString())
    }

    @Test
    fun `the app actions menu leaves out reporting when the app hides it`() {
        logIn()
        CommCareApplication
            .instance()
            .currentApp.appPreferences
            .edit()
            .putString("cc-hide-issue-report", "yes")
            .commit()

        val activity = launchConnect()

        assertFalse(isMenuItemVisible(activity, R.id.action_report_problem))
        assertTrue(isMenuItemVisible(activity, R.id.action_preferences))
    }

    @Test
    fun `the app actions menu is only on Opportunity Home`() {
        logIn()
        val activity = launchConnect()

        val navHost = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_connect) as NavHostFragment
        navHost.navController.navigate(R.id.connect_jobs_list_fragment)
        shadowOf(Looper.getMainLooper()).idle()

        APP_ACTION_ITEMS.forEach { assertFalse(isMenuItemVisible(activity, it)) }
    }

    @Test
    fun `reporting an issue opens the report screen and logs the menu click`() {
        logIn()
        val activity = launchLoggedInConnect()

        activity.onOptionsItemSelected(menuItem(activity, R.id.action_report_problem))

        assertEquals(
            ReportProblemActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component?.className,
        )
        verify { FirebaseAnalyticsUtil.reportOptionsMenuItemClick(ConnectActivity::class.java, AnalyticsParamValue.REPORT_PROBLEM) }
    }

    @Test
    fun `a data reset from settings closes the page`() {
        logIn()
        val activity = launchLoggedInConnect()

        activity.onOptionsItemSelected(menuItem(activity, R.id.action_preferences))
        val settings = shadowOf(activity).nextStartedActivityForResult

        assertEquals(SessionAwarePreferenceActivity::class.java.name, settings.intent.component?.className)
        assertEquals(HomeRequestCodes.PREFERENCES_ACTIVITY, settings.requestCode)
        verify { FirebaseAnalyticsUtil.reportOptionsMenuItemClick(ConnectActivity::class.java, AnalyticsParamValue.ITEM_SETTINGS) }
        shadowOf(activity).receiveResult(settings.intent, HomeResultCodes.RESULT_DATA_RESET, null)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `forms processed in advanced actions are reported in a toast`() {
        logIn()
        val activity = launchLoggedInConnect()

        activity.onOptionsItemSelected(menuItem(activity, R.id.action_advanced))
        val advanced = shadowOf(activity).nextStartedActivityForResult
        val result =
            Intent()
                .putExtra(AdvancedActionsPreferences.FORM_PROCESS_COUNT_KEY, 3)
                .putExtra(AdvancedActionsPreferences.FORM_PROCESS_MESSAGE_KEY, "home.menu.advanced")

        assertEquals(HomeRequestCodes.ADVANCED_ACTIONS_ACTIVITY, advanced.requestCode)
        shadowOf(activity).receiveResult(advanced.intent, HomeResultCodes.RESULT_FORMS_PROCESSED, result)
        assertEquals("Advanced", ShadowToast.getTextOfLatestToast())
        assertFalse(activity.isFinishing)
    }

    @Test
    fun `sync also syncs the app`() {
        logIn()
        val activity = launchLoggedInConnect()
        val syncer = fakeSyncer(activity)

        activity.onOptionsItemSelected(menuItem(activity, R.id.action_sync))

        assertEquals(1, syncer.formSendCount)
        assertTrue(syncer.lastSendSyncsAfterwards)
        assertTrue(syncer.lastSendUserTriggered)
        verify { repository.getDeliveryProgress(job, true, any()) }
    }

    @Test
    fun `sync with no session`() {
        val activity = launchConnect()
        val syncer = fakeSyncer(activity)

        activity.onOptionsItemSelected(menuItem(activity, R.id.action_sync))

        assertEquals(0, syncer.formSendCount)
        verify { repository.getDeliveryProgress(job, true, any()) }
    }

    @Test
    fun `offline sync skips the app`() {
        logIn()
        val activity = launchLoggedInConnect()
        val syncer = fakeSyncer(activity)
        every { ConnectivityStatus.isNetworkAvailable(any()) } returns false
        every { ConnectivityStatus.isAirplaneModeOn(any()) } returns false

        activity.onOptionsItemSelected(menuItem(activity, R.id.action_sync))

        assertEquals(0, syncer.formSendCount)
        assertEquals(
            Localization.get("notification.sync.connections.action"),
            ShadowToast.getTextOfLatestToast(),
        )
        verify { repository.getDeliveryProgress(job, true, any()) }
    }

    @Test
    fun `sync off Opp Home`() {
        logIn()
        val activity = launchLoggedInConnect()
        val syncer = fakeSyncer(activity)
        val navHost = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_connect) as NavHostFragment
        navHost.navController.navigate(R.id.connect_jobs_list_fragment)
        shadowOf(Looper.getMainLooper()).idle()

        activity.onOptionsItemSelected(menuItem(activity, R.id.action_sync))

        assertEquals(0, syncer.formSendCount)
    }

    @Test
    fun `a launched app opens its menu here`() {
        logIn()
        val activity = launchLoggedInConnect()

        assertTrue(activity.onAppLaunched(true))

        val menu = shadowOf(activity).nextStartedActivityForResult
        assertEquals(MenuActivity::class.java.name, menu.intent.component?.className)
        assertEquals(HomeRequestCodes.GET_COMMAND, menu.requestCode)
    }

    @Test
    fun `backing out of the app menu returns to Opp Home`() {
        logIn()
        val activity = launchLoggedInConnect()
        activity.onAppLaunched(true)
        val menu = shadowOf(activity).nextStartedActivityForResult
        shadowOf(activity).nextStartedActivity

        shadowOf(activity).receiveResult(menu.intent, Activity.RESULT_CANCELED, null)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(R.id.connect_opportunity_home_fragment, navController(activity).currentDestination?.id)
        assertNull(CommCareApplication.instance().currentSession.command)
    }

    @Test
    fun `a new sign-in runs the launch checks before the menu`() {
        logIn()
        val activity = launchLoggedInConnect()
        val syncer = fakeSyncer(activity)
        HiddenPreferences.setPostUpdateSyncNeeded(true)

        assertFalse(activity.onAppLaunched(false))

        assertEquals(1, syncer.formSendCount)
        assertNull(shadowOf(activity).nextStartedActivityForResult)
    }

    private fun navController(activity: ConnectActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_connect) as NavHostFragment).navController

    private fun fakeSyncer(activity: ConnectActivity) = FormAndDataSyncerFake().also { activity.coordinator.sync.setFormAndDataSyncer(it) }

    private fun launchLoggedInConnect(): ConnectActivity {
        val activity = launchConnect()
        while (shadowOf(activity).nextStartedActivity != null) {
            continue
        }
        return activity
    }

    private fun menuItem(
        activity: ConnectActivity,
        id: Int,
    ) = shadowOf(activity).optionsMenu.findItem(id)

    private fun isMenuItemVisible(
        activity: ConnectActivity,
        id: Int,
    ): Boolean {
        activity.invalidateOptionsMenu()
        shadowOf(Looper.getMainLooper()).idle()
        return menuItem(activity, id).isVisible
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
        val APP_ACTION_ITEMS =
            listOf(
                R.id.action_report_problem,
                R.id.action_update,
                R.id.action_update_commcare,
                R.id.action_change_language,
                R.id.action_preferences,
                R.id.action_advanced,
            )
    }
}
