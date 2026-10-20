package org.commcare.activities

import android.content.Intent
import android.widget.Button
import org.commcare.CommCareApplication
import org.commcare.android.util.ActivityAssertions.assertStarted
import org.commcare.dalvik.R
import org.commcare.navdrawer.BaseDrawerController.NavItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.shadows.ShadowLooper

/**
 * Characterization pins for the nav drawer's traditional destination (app switching).
 *
 *
 * The Connect/PersonalId destinations live in [HomeConnectDrawerNavigationTest].
 */
class HomeDrawerNavigationTest : BaseHomeScreenActivityTest() {
    // ---- COMMCARE_APPS: app switching ----

    @Test
    fun `commcare apps raises the switch-app confirmation`() {
        val home = buildVisibleHome()
        home.handleDrawerItemClick(NavItemType.COMMCARE_APPS)
        home.supportFragmentManager.executePendingTransactions()

        assertNotNull("selecting apps should prompt before leaving home", home.currentAlertDialog)
    }

    @Test
    fun `commcare apps does not leave home until the prompt is confirmed`() {
        val home = buildVisibleHome()
        home.handleDrawerItemClick(NavItemType.COMMCARE_APPS)
        home.supportFragmentManager.executePendingTransactions()

        assertFalse("home should stay up while the prompt is unanswered", home.isFinishing)
    }

    @Test
    fun `confirming commcare apps logs out and opens the login page as the task root`() {
        val home = buildVisibleHome()
        home.handleDrawerItemClick(NavItemType.COMMCARE_APPS)
        home.supportFragmentManager.executePendingTransactions()

        home.currentAlertDialog!!
            .dialog!!
            .findViewById<Button>(R.id.positive_button)
            .performClick()
        ShadowLooper.idleMainLooper()

        val intent = assertStarted(home, DispatchActivity::class.java)
        assertTrue(intent.getBooleanExtra(LoginActivity.USER_TRIGGERED_LOGOUT, false))
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            intent.flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        assertFalse(CommCareApplication.isSessionActive())
        assertTrue(home.isFinishing)
    }
}
