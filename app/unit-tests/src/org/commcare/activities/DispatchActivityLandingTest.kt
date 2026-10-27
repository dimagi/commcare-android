package org.commcare.activities

import android.app.Activity
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
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdUserPreferences
import org.commcare.recovery.measures.ExecuteRecoveryMeasuresActivity
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

    @Test
    fun `an active session opens CommCare App Home`() {
        val dispatchActivity = buildDispatchActivity().get()

        ActivityAssertions.assertStartedForResult(dispatchActivity, StandardHomeActivity::class.java)
    }

    @Test
    fun `resuming without a result does not route again`() {
        val controller = buildDispatchActivity()
        ActivityAssertions.startedIntents(controller.get())

        controller.pause().resume()
        ShadowLooper.idleMainLooper()

        ActivityAssertions.assertStartedNothing(controller.get())
    }

    @Test
    fun `returning from a screen started without a result routes again`() {
        ConnectTestUtils.signInToPersonalId()
        PersonalIdUserPreferences.setPendingBackupCode(true)
        val controller = buildDispatchActivity()
        ActivityAssertions.startedIntents(controller.get())

        controller.pause().resume()
        ShadowLooper.idleMainLooper()

        ActivityAssertions.assertStartedForResult(controller.get(), StandardHomeActivity::class.java)
    }

    @Test
    fun `a new intent routes again`() {
        val controller = buildDispatchActivity()
        ActivityAssertions.startedIntents(controller.get())

        controller.pause().newIntent(Intent()).resume()
        ShadowLooper.idleMainLooper()

        ActivityAssertions.assertStarted(controller.get(), StandardHomeActivity::class.java)
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
    fun `a seated app with no session opens CommCare Login`() {
        CommCareApplication.instance().closeUserSession()

        val dispatchActivity = buildDispatchActivity().get()

        ActivityAssertions.assertStartedForResult(dispatchActivity, LoginActivity::class.java)
    }

    @Test
    fun `a declined unlock opens CommCare Login instead of the opportunity`() {
        ConnectTestUtils.seatJob(ConnectTestUtils.connectJob())
        CommCareApplication.instance().closeUserSession()
        val controller = buildDispatchActivity()
        val connect = ActivityAssertions.assertStartedForResult(controller.get(), ConnectActivity::class.java)
        ActivityAssertions.startedIntents(controller.get())

        deliverResult(controller, connect, DispatchActivity.RESULT_PERSONAL_ID_UNLOCK_DECLINED)

        ActivityAssertions.assertStartedForResult(controller.get(), LoginActivity::class.java)
        assertFalse(controller.get().isFinishing)
    }

    @Test
    fun `a declined unlock with an active session logs out and opens CommCare Login`() {
        ConnectTestUtils.seatJob(ConnectTestUtils.connectJob())
        val controller = buildDispatchActivity()
        val connect = ActivityAssertions.assertStartedForResult(controller.get(), ConnectActivity::class.java)
        ActivityAssertions.startedIntents(controller.get())

        deliverResult(controller, connect, DispatchActivity.RESULT_PERSONAL_ID_UNLOCK_DECLINED)

        assertFalse(CommCareApplication.hasLoggedInUser())
        ActivityAssertions.assertStartedForResult(controller.get(), LoginActivity::class.java)
    }

    @Test
    fun `a new intent is routed by its own extras`() {
        ConnectTestUtils.seatJob(ConnectTestUtils.connectJob())
        val controller = buildDispatchActivity()
        ActivityAssertions.startedIntents(controller.get())
        ActivityAssertions.startedForResultIntents(controller.get())

        CommCareApplication.instance().closeUserSession()
        controller
            .pause()
            .newIntent(Intent().putExtra(LoginActivity.USER_TRIGGERED_LOGOUT, true))
            .resume()
        ShadowLooper.idleMainLooper()

        ActivityAssertions.assertStartedForResult(controller.get(), LoginActivity::class.java)
    }

    @Test
    fun `logging out of CommCare App Home opens CommCare Login`() {
        val controller = buildDispatchActivity()
        val home = ActivityAssertions.assertStartedForResult(controller.get(), StandardHomeActivity::class.java)
        ActivityAssertions.startedIntents(controller.get())

        ConnectTestUtils.seatJob(ConnectTestUtils.connectJob())
        CommCareApplication.instance().closeUserSession()
        deliverResult(controller, home, Activity.RESULT_OK)

        ActivityAssertions.assertStartedForResult(controller.get(), LoginActivity::class.java)
    }

    @Test
    fun `signing in on CommCare Login opens CommCare App Home`() {
        CommCareApplication.instance().closeUserSession()
        val controller = buildDispatchActivity()
        val login = ActivityAssertions.assertStartedForResult(controller.get(), LoginActivity::class.java)
        ActivityAssertions.startedIntents(controller.get())

        TestAppInstaller.login(TEST_USER, TEST_PASSWORD)
        deliverResult(controller, login, Activity.RESULT_OK, Intent())

        ActivityAssertions.assertStartedForResult(controller.get(), StandardHomeActivity::class.java)
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

    private fun assertStartedOpportunityHome(dispatchActivity: DispatchActivity) {
        val connect = ActivityAssertions.assertStartedForResult(dispatchActivity, ConnectActivity::class.java)
        assertTrue(connect.getBooleanExtra(ConnectConstants.FROM_STARTUP_ROUTING, false))
        assertEquals(ConnectTestUtils.JOB_UUID, connect.getStringExtra(ConnectConstants.OPPORTUNITY_UUID))
        assertFalse(dispatchActivity.isFinishing)
    }
}
