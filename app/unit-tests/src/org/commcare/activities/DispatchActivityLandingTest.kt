package org.commcare.activities

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import io.mockk.every
import io.mockk.mockkStatic
import org.commcare.CommCareApplication
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.util.ActivityAssertions
import org.commcare.android.util.ConnectTestUtils
import org.commcare.android.util.TestAppInstaller
import org.commcare.connect.ConnectConstants
import org.commcare.connect.PersonalIdSessionManager
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdUserPreferences
import org.commcare.recovery.measures.ExecuteRecoveryMeasuresActivity
import org.commcare.services.CommCareSessionService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers

/** Where [DispatchActivity] opens the app, and that it decides once per launch rather than on every resume. */
class DispatchActivityLandingTest : BaseHomeScreenActivityTest() {
    @Before
    fun stubForgottenAccountReport() {
        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.reportPersonalIdAccountForgotten(any()) } returns Unit
    }

    /**
     * Forgets the PersonalID user while the report is still stubbed. Reporting it from the base teardown
     * starts Firebase after the app is gone, which leaves Play Services failing for every later test.
     */
    @After
    fun forgetPersonalIdUser() {
        PersonalIdSessionManager.getInstance().forgetUser("dispatch activity landing test teardown")
    }

    @After
    fun clearFormEntryInProgress() {
        setFormEntryInProgress(false)
    }

    @Test
    fun `an active session opens CommCare App Home as the only screen in the task`() {
        val dispatchActivity = buildDispatchActivity().get()

        val home = ActivityAssertions.assertStarted(dispatchActivity, StandardHomeActivity::class.java)
        assertOpensAsTaskRoot(dispatchActivity, home)
    }

    @Test
    fun `resuming without a result does not route again`() {
        CommCareApplication.instance().closeUserSession()
        val controller = buildDispatchActivity()
        ActivityAssertions.startedForResultIntents(controller.get())
        ActivityAssertions.startedIntents(controller.get())

        controller.pause().resume()
        ShadowLooper.idleMainLooper()

        ActivityAssertions.assertStartedNothing(controller.get())
    }

    @Test
    fun `returning from a screen started without a result routes again`() {
        val controller = buildDispatchActivityAfterPendingBackupCode()

        controller.pause().resume()
        ShadowLooper.idleMainLooper()

        ActivityAssertions.assertStarted(controller.get(), StandardHomeActivity::class.java)
    }

    @Test
    fun `a new intent routes again`() {
        val controller = buildDispatchActivityAfterPendingBackupCode()

        controller.pause().newIntent(Intent()).resume()
        ShadowLooper.idleMainLooper()

        ActivityAssertions.assertStarted(controller.get(), StandardHomeActivity::class.java)
    }

    @Test
    fun `a new intent is routed by its own extras`() {
        val controller = buildDispatchActivityAfterPendingBackupCode()

        CommCareApplication.instance().closeUserSession()
        controller
            .pause()
            .newIntent(Intent().putExtra(LoginActivity.USER_TRIGGERED_LOGOUT, true))
            .resume()
        ShadowLooper.idleMainLooper()

        ActivityAssertions.assertStartedForResult(controller.get(), LoginActivity::class.java)
    }

    @Test
    fun `an active session on an opportunity's app opens that opportunity behind the unlock`() {
        ConnectTestUtils.seatJob(ConnectTestUtils.connectJob())

        val dispatchActivity = buildDispatchActivity().get()

        assertStartedOpportunityHome(dispatchActivity)
    }

    @Test
    fun `a seated opportunity app with no session opens that opportunity behind the unlock`() {
        ConnectTestUtils.seatJob(ConnectTestUtils.connectJob())
        CommCareApplication.instance().closeUserSession()

        val dispatchActivity = buildDispatchActivity().get()

        assertStartedOpportunityHome(dispatchActivity)
    }

    @Test
    fun `a seated app with no session opens CommCare Login on top of the router`() {
        CommCareApplication.instance().closeUserSession()

        val dispatchActivity = buildDispatchActivity().get()

        ActivityAssertions.assertStartedForResult(dispatchActivity, LoginActivity::class.java)
        assertFalse(dispatchActivity.isFinishing)
    }

    @Test
    fun `a declined unlock opens CommCare Login instead of the opportunity`() {
        ConnectTestUtils.seatJob(ConnectTestUtils.connectJob())
        CommCareApplication.instance().closeUserSession()

        val dispatchActivity = buildDispatchActivity(buildUnlockDeclinedIntent()).get()

        ActivityAssertions.assertStartedForResult(dispatchActivity, LoginActivity::class.java)
        assertFalse(dispatchActivity.isFinishing)
    }

    @Test
    fun `a declined unlock with an active session logs out and opens CommCare Login`() {
        ConnectTestUtils.seatJob(ConnectTestUtils.connectJob())

        val dispatchActivity = buildDispatchActivity(buildUnlockDeclinedIntent()).get()

        assertFalse(CommCareApplication.hasLoggedInUser())
        ActivityAssertions.assertStartedForResult(dispatchActivity, LoginActivity::class.java)
    }

    @Test
    fun `a declined unlock during a sync closes the app and keeps the session`() {
        ConnectTestUtils.seatJob(ConnectTestUtils.connectJob())

        CommCareSessionService.sessionAliveLock.lock()
        val dispatchActivity =
            try {
                buildDispatchActivity(buildUnlockDeclinedIntent()).get()
            } finally {
                CommCareSessionService.sessionAliveLock.unlock()
            }

        assertTrue(dispatchActivity.isFinishing)
        assertTrue(CommCareApplication.hasLoggedInUser())
    }

    @Test
    fun `logging out of CommCare App Home opens CommCare Login`() {
        ConnectTestUtils.seatJob(ConnectTestUtils.connectJob())
        CommCareApplication.instance().closeUserSession()

        val dispatchActivity =
            buildDispatchActivity(Intent().putExtra(LoginActivity.USER_TRIGGERED_LOGOUT, true)).get()

        ActivityAssertions.assertStartedForResult(dispatchActivity, LoginActivity::class.java)
    }

    @Test
    fun `recovery measures started from CommCare App Home run before routing again`() {
        val recoveryIntent = Intent().putExtra(DispatchActivity.EXECUTE_RECOVERY_MEASURES, true)

        val dispatchActivity = buildDispatchActivity(recoveryIntent).get()

        ActivityAssertions.assertStartedForResult(dispatchActivity, ExecuteRecoveryMeasuresActivity::class.java)
    }

    @Test
    fun `signing in on CommCare Login opens CommCare App Home as the only screen in the task`() {
        CommCareApplication.instance().closeUserSession()
        val controller = buildDispatchActivity()
        val login = ActivityAssertions.assertStartedForResult(controller.get(), LoginActivity::class.java)
        ActivityAssertions.startedIntents(controller.get())

        TestAppInstaller.login(TEST_USER, TEST_PASSWORD)
        deliverResult(controller, login, Activity.RESULT_OK, Intent())

        val home = ActivityAssertions.assertStarted(controller.get(), StandardHomeActivity::class.java)
        assertOpensAsTaskRoot(controller.get(), home)
    }

    @Test
    fun `recovery measures started from CommCare Login return to CommCare Login`() {
        CommCareApplication.instance().closeUserSession()
        val controller = buildDispatchActivity()
        val login = ActivityAssertions.assertStartedForResult(controller.get(), LoginActivity::class.java)
        ActivityAssertions.startedIntents(controller.get())

        val recoveryResult = Intent().putExtra(DispatchActivity.EXECUTE_RECOVERY_MEASURES, true)
        deliverResult(controller, login, Activity.RESULT_OK, recoveryResult)
        val recovery =
            ActivityAssertions.assertStartedForResult(controller.get(), ExecuteRecoveryMeasuresActivity::class.java)
        deliverResult(controller, recovery, Activity.RESULT_OK)

        ActivityAssertions.assertStartedForResult(controller.get(), LoginActivity::class.java)
    }

    @Test
    fun `a launch above other screens restarts routing as the only screen in the task`() {
        val logoutIntent =
            Intent()
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(LoginActivity.USER_TRIGGERED_LOGOUT, true)
        val controller = Robolectric.buildActivity(DispatchActivity::class.java, logoutIntent)
        Shadows.shadowOf(controller.get()).setIsTaskRoot(false)

        val dispatchActivity = controller.create().get()

        val restart = ActivityAssertions.assertStarted(dispatchActivity, DispatchActivity::class.java)
        assertTrue(restart.getBooleanExtra(LoginActivity.USER_TRIGGERED_LOGOUT, false))
        assertEquals(0, restart.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
        assertOpensAsTaskRoot(dispatchActivity, restart)
    }

    @Test
    fun `a launch above a form in progress asks the form to save or discard before restarting`() {
        setFormEntryInProgress(true)

        val dispatchActivity = buildStackedDispatchActivity().create().get()

        val formEntryIntent = ActivityAssertions.assertStarted(dispatchActivity, FormEntryActivity::class.java)
        val pendingRestartIntent = getPendingNavIntent(formEntryIntent)
        assertEquals(DispatchActivity::class.java.name, pendingRestartIntent.component?.className)
        assertEquals(
            AnalyticsParamValue.EXTERNAL_LAUNCH,
            formEntryIntent.getStringExtra(FormEntryActivity.EXTRA_PENDING_NAV_SOURCE),
        )
        assertOpensAsTaskRoot(dispatchActivity, pendingRestartIntent)
    }

    @Test
    fun `a launch above a form whose session ended restarts routing without asking the form`() {
        setFormEntryInProgress(true)
        CommCareApplication.instance().closeUserSession()

        val dispatchActivity = buildStackedDispatchActivity().create().get()

        val restartIntent = ActivityAssertions.assertStarted(dispatchActivity, DispatchActivity::class.java)
        assertOpensAsTaskRoot(dispatchActivity, restartIntent)
    }

    @Test
    fun `a launch above another app waiting for its result routes in place`() {
        CommCareApplication.instance().closeUserSession()
        val controller = Robolectric.buildActivity(DispatchActivity::class.java, Intent())
        Shadows.shadowOf(controller.get()).setIsTaskRoot(false)
        Shadows.shadowOf(controller.get()).setCallingActivity(ComponentName("org.example.caller", "CallerActivity"))

        val dispatchActivity = controller.create().resume().get()
        ShadowLooper.idleMainLooper()

        ActivityAssertions.assertStartedForResult(dispatchActivity, LoginActivity::class.java)
        assertFalse(dispatchActivity.isFinishing)
    }

    @Test
    fun `signing in on CommCare Login to view an opportunity opens its home as the only screen in the task`() {
        ConnectTestUtils.seatJob(ConnectTestUtils.connectJob())
        CommCareApplication.instance().closeUserSession()
        val controller = buildDispatchActivity(buildUnlockDeclinedIntent())
        val login = ActivityAssertions.assertStartedForResult(controller.get(), LoginActivity::class.java)
        ActivityAssertions.startedIntents(controller.get())

        TestAppInstaller.login(TEST_USER, TEST_PASSWORD)
        val redirectResult = Intent().putExtra(DispatchActivity.REDIRECT_TO_CONNECT_OPPORTUNITY_INFO, true)
        deliverResult(controller, login, Activity.RESULT_OK, redirectResult)

        val connect = ActivityAssertions.assertStarted(controller.get(), ConnectActivity::class.java)
        assertTrue(connect.getBooleanExtra(ConnectConstants.GO_TO_JOB_STATUS, false))
        assertEquals(ConnectTestUtils.JOB_UUID, connect.getStringExtra(ConnectConstants.OPPORTUNITY_UUID))
        assertOpensAsTaskRoot(controller.get(), connect)
    }

    private fun deliverResult(
        controller: ActivityController<DispatchActivity>,
        startedIntent: Intent,
        resultCode: Int,
        data: Intent? = null,
    ) {
        controller.pause()
        Shadows.shadowOf(controller.get()).receiveResult(startedIntent, resultCode, data)
        controller.resume()
        ShadowLooper.idleMainLooper()
    }

    private fun buildDispatchActivity(intent: Intent = Intent()): ActivityController<DispatchActivity> =
        Robolectric
            .buildActivity(DispatchActivity::class.java, intent)
            .create()
            .resume()
            .also { ShadowLooper.idleMainLooper() }

    /** Routes first to the pending backup code screen, which leaves the router open beneath it. */
    private fun buildDispatchActivityAfterPendingBackupCode(): ActivityController<DispatchActivity> {
        ConnectTestUtils.signInToPersonalId()
        PersonalIdUserPreferences.setPendingBackupCode(true)
        val controller = buildDispatchActivity()
        ActivityAssertions.startedIntents(controller.get())
        return controller
    }

    private fun buildStackedDispatchActivity(): ActivityController<DispatchActivity> {
        val controller = Robolectric.buildActivity(DispatchActivity::class.java, Intent())
        Shadows.shadowOf(controller.get()).setIsTaskRoot(false)
        return controller
    }

    private fun setFormEntryInProgress(inProgress: Boolean) {
        ReflectionHelpers.setStaticField(FormEntryActivity::class.java, "isFormEntryActive", inProgress)
    }

    @Suppress("DEPRECATION")
    private fun getPendingNavIntent(formEntry: Intent): Intent =
        formEntry.getParcelableExtra<Intent>(FormEntryActivity.EXTRA_PENDING_NAV_INTENT)!!

    private fun buildUnlockDeclinedIntent(): Intent = Intent().putExtra(DispatchActivity.EXTRA_PERSONAL_ID_UNLOCK_DECLINED, true)

    private fun assertStartedOpportunityHome(dispatchActivity: DispatchActivity) {
        val connect = ActivityAssertions.assertStarted(dispatchActivity, ConnectActivity::class.java)
        assertTrue(connect.getBooleanExtra(ConnectConstants.FROM_STARTUP_ROUTING, false))
        assertEquals(ConnectTestUtils.JOB_UUID, connect.getStringExtra(ConnectConstants.OPPORTUNITY_UUID))
        assertOpensAsTaskRoot(dispatchActivity, connect)
    }

    private fun assertOpensAsTaskRoot(
        dispatchActivity: DispatchActivity,
        landing: Intent,
    ) {
        val taskRootFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        assertEquals(taskRootFlags, landing.flags and taskRootFlags)
        assertTrue(dispatchActivity.isFinishing)
    }
}
