package org.commcare.activities

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Intent
import org.commcare.CommCareApplication
import org.commcare.android.util.ActivityAssertions.assertStarted
import org.commcare.utils.SessionRegistrationHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

/**
 * Pins the redirect to login when home loses its session after `onCreate`, now routed through
 * [org.commcare.home.SessionExpirationDelegate] instead of the session-aware activity chain.
 */
class HomeSessionExpirationTest : BaseHomeScreenActivityTest() {
    @After
    fun clearPendingExpiration() {
        SessionRegistrationHelper.unregisterSessionExpiration()
    }

    @Test
    fun `background expiration redirects to login on resume`() {
        val controller = buildStandardHomeController().start()
        SessionRegistrationHelper.registerSessionExpiration()

        controller.resume()

        assertRedirectedToLogin(controller.get())
        assertFalse(SessionRegistrationHelper.consumePendingExpiration())
    }

    @Test
    fun `expiration broadcast redirects to login`() {
        val homeScreen = buildVisibleHome()
        drainStartedActivities(homeScreen)

        broadcastExpiration()

        assertRedirectedToLogin(homeScreen)
        assertFalse(SessionRegistrationHelper.consumePendingExpiration())
    }

    @Test
    fun `pending expiration skips the activity result`() {
        val homeScreen = buildVisibleHome()
        drainStartedActivities(homeScreen)
        SessionRegistrationHelper.registerSessionExpiration()

        homeScreen.startActivityForResult(Intent(), HomeScreenBaseActivity.CREATE_PIN)
        val pinRequest = shadowOf(homeScreen).nextStartedActivityForResult.intent

        shadowOf(homeScreen).receiveResult(pinRequest, RESULT_OK, Intent())

        assertRedirectedToLogin(homeScreen)
        assertNull(ShadowToast.getTextOfLatestToast())
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

    /** Login is reached through [DispatchActivity], so the redirect shows as a cleared-top start of it. */
    private fun assertRedirectedToLogin(activity: Activity) {
        val intent = assertStarted(activity, DispatchActivity::class.java)
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    private fun drainStartedActivities(homeScreen: StandardHomeActivity) {
        while (shadowOf(homeScreen).nextStartedActivity != null) {
            continue
        }
    }
}
