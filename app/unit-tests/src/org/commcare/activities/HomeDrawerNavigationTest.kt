package org.commcare.activities

import android.app.Activity.RESULT_OK
import org.commcare.android.util.TestAppInstaller.seatedAppId
import org.commcare.navdrawer.BaseDrawerController.NavItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization pins for the nav drawer's traditional destinations (app switching).
 *
 * The Connect/PersonalId destinations live in [HomeConnectDrawerNavigationTest].
 */
class HomeDrawerNavigationTest : BaseHomeScreenActivityTest() {
    // ---- COMMCARE_APPS: app switching ----

    @Test
    fun `commcare apps with different app id closes session and returns app switch result`() {
        val home = buildHome()
        home.handleDrawerItemClick(NavItemType.COMMCARE_APPS, "a-different-app-id")

        val shadow = shadowOf(home)
        assertTrue("Home should finish to hand off to LoginActivity", home.isFinishing)
        assertEquals(RESULT_OK, shadow.resultCode)
        val result = shadow.resultIntent
        assertEquals("a-different-app-id", result.getStringExtra(LoginActivity.EXTRA_APP_ID))
        assertFalse(result.getBooleanExtra(LoginActivity.EXTRA_FORCE_SINGLE_APP_MODE, true))
    }

    @Test
    fun `commcare apps with current app id is a no-op`() {
        val home = buildHome()
        home.handleDrawerItemClick(NavItemType.COMMCARE_APPS, seatedAppId())

        assertFalse("Selecting the already-seated app should not finish home", home.isFinishing)
    }

    @Test
    fun `commcare apps with null record id is a no-op`() {
        val home = buildHome()
        home.handleDrawerItemClick(NavItemType.COMMCARE_APPS, null)

        assertFalse(home.isFinishing)
    }
}
