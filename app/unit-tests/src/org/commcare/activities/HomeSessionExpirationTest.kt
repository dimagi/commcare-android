package org.commcare.activities

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

        val intent = assertStarted(controller.get(), DispatchActivity::class.java)
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
        assertFalse(SessionRegistrationHelper.consumePendingExpiration())
    }

    @Test
    fun `expiration broadcast redirects to login`() {
        val home = buildVisibleHome()
        drainStartedActivities(home)

        broadcastExpiration()

        assertStarted(home, DispatchActivity::class.java)
        assertFalse(SessionRegistrationHelper.consumePendingExpiration())
    }

    @Test
    fun `pending expiration skips the activity result`() {
        val home = buildVisibleHome()
        drainStartedActivities(home)
        SessionRegistrationHelper.registerSessionExpiration()

        shadowOf(home).callOnActivityResult(HomeScreenBaseActivity.CREATE_PIN, RESULT_OK, Intent())

        assertStarted(home, DispatchActivity::class.java)
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

    private fun drainStartedActivities(home: StandardHomeActivity) {
        while (shadowOf(home).nextStartedActivity != null) {
            continue
        }
    }
}
