package org.commcare.activities

import io.mockk.verify
import org.commcare.connect.ConnectNavHelper
import org.commcare.navdrawer.BaseDrawerController.NavItemType
import org.junit.Test

/**
 * Characterization pins for the Connect/PersonalId nav-drawer destinations (opportunities,
 * messaging, work history), including the managed-login vs legacy-login unlock split.
 *
 * Not pinned: the `closeDrawer()` that each managed-login arm calls after navigating. These tests
 * never log a PersonalId user in, so `BaseDrawerActivity.drawerController` is null and `closeDrawer()`
 * is a no-op that logs — asserting it here would assert nothing. It needs a fixture that actually
 * seats the drawer (see `PersonalIdDrawerVisibilityTest` for what that costs).
 *
 * Also not pinned: what the legacy path's unlock listener does on success. The base stubs
 * `unlockAndGoTo*` to a no-op, so the callback body never runs.
 *
 * The drawer's traditional destinations (app switching) live in [HomeDrawerNavigationTest].
 */
class HomeConnectDrawerNavigationTest : HomeConnectTestBase() {
    // ---- OPPORTUNITIES ----

    @Test
    fun `opportunities managed login routes to connect jobs list`() {
        val home = buildHome(personalIdManagedLogin = true)
        home.handleDrawerItemClick(NavItemType.OPPORTUNITIES, null)

        verify(exactly = 1) { ConnectNavHelper.goToConnectJobsList(home, false) }
        verify(exactly = 0) { ConnectNavHelper.unlockAndGoToConnectJobsList(any(), any(), any()) }
    }

    @Test
    fun `opportunities legacy login routes through unlock`() {
        val home = buildHome(personalIdManagedLogin = false)
        home.handleDrawerItemClick(NavItemType.OPPORTUNITIES, null)

        verify(exactly = 1) { ConnectNavHelper.unlockAndGoToConnectJobsList(home, any(), any()) }
        verify(exactly = 0) { ConnectNavHelper.goToConnectJobsList(any(), any()) }
    }

    // ---- MESSAGING ----

    @Test
    fun `messaging managed login routes to messaging directly`() {
        val home = buildHome(personalIdManagedLogin = true)
        home.handleDrawerItemClick(NavItemType.MESSAGING, null)

        verify(exactly = 1) { ConnectNavHelper.goToMessaging(home, null) }
        verify(exactly = 0) { ConnectNavHelper.unlockAndGoToMessaging(any(), any(), any(), any()) }
    }

    @Test
    fun `messaging legacy login routes through unlock`() {
        val home = buildHome(personalIdManagedLogin = false)
        home.handleDrawerItemClick(NavItemType.MESSAGING, null)

        verify(exactly = 1) { ConnectNavHelper.unlockAndGoToMessaging(home, any(), any(), any()) }
        verify(exactly = 0) { ConnectNavHelper.goToMessaging(any(), any()) }
    }

    // ---- WORK_HISTORY ----

    @Test
    fun `work history managed login routes to work history directly`() {
        val home = buildHome(personalIdManagedLogin = true)
        home.handleDrawerItemClick(NavItemType.WORK_HISTORY, null)

        verify(exactly = 1) { ConnectNavHelper.goToWorkHistory(home) }
        verify(exactly = 0) { ConnectNavHelper.unlockAndGoToWorkHistory(any(), any(), any()) }
    }

    @Test
    fun `work history legacy login routes through unlock`() {
        val home = buildHome(personalIdManagedLogin = false)
        home.handleDrawerItemClick(NavItemType.WORK_HISTORY, null)

        verify(exactly = 1) { ConnectNavHelper.unlockAndGoToWorkHistory(home, any(), any()) }
        verify(exactly = 0) { ConnectNavHelper.goToWorkHistory(any()) }
    }
}
