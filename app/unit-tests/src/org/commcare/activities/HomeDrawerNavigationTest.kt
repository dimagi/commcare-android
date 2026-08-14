package org.commcare.activities

import org.commcare.navdrawer.BaseDrawerController.NavItemType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Characterization pins for the nav drawer's traditional destination (app switching).
 *
 * QA-8628 removed the in-drawer app list, so `COMMCARE_APPS` no longer carries the id of a chosen
 * app and no longer switches directly. It now raises a confirmation dialog and only returns to
 * login once the user confirms, which is what these pin.
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
