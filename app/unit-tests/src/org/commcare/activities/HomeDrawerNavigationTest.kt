package org.commcare.activities

import org.commcare.navdrawer.BaseDrawerController.NavItemType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

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
}
