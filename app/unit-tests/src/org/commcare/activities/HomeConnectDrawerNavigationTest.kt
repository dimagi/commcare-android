package org.commcare.activities

import android.content.Intent
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.verify
import org.commcare.activities.connect.ConnectActivity
import org.commcare.activities.connect.ConnectMessagingActivity
import org.commcare.activities.connect.PersonalIdWorkHistoryActivity
import org.commcare.connect.PersonalIdManager
import org.commcare.navdrawer.BaseDrawerController.NavItemType
import org.commcare.personalId.PersonalIdUnlocker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Characterization pins for the Connect/PersonalId nav-drawer destinations (opportunities,
 * messaging, work history), including the managed-login vs legacy-login unlock split.
 *
 * Navigation itself runs for real and is asserted on the activity each destination launches. The
 * unlock the legacy arm goes through is the one thing stubbed: it drives the device's biometric
 * hardware, which a Robolectric device doesn't have.
 *
 * The drawer's traditional destinations (app switching) live in [HomeDrawerNavigationTest].
 */
class HomeConnectDrawerNavigationTest : HomeConnectTestBase() {
    @Before
    fun grantConnectAccessAndAllowUnlock() {
        // goToConnectJobsList() refuses to navigate without Connect access on the signed-in user.
        signInToPersonalId(hasConnectAccess = true)
        mockkObject(PersonalIdUnlocker)
        every { PersonalIdUnlocker.unlock(any(), any(), any()) } answers {
            thirdArg<PersonalIdManager.ConnectActivityCompleteListener>().connectActivityComplete(true)
        }
    }

    // ---- OPPORTUNITIES ----

    @Test
    fun `opportunities managed login goes straight to the jobs list`() {
        val home = buildHome(personalIdManagedLogin = true)

        home.handleDrawerItemClick(NavItemType.OPPORTUNITIES, null)

        assertStarted(home, ConnectActivity::class.java)
        verify(exactly = 0) { PersonalIdUnlocker.unlock(any(), any(), any()) }
    }

    @Test
    fun `opportunities legacy login unlocks before the jobs list`() {
        val home = buildHome(personalIdManagedLogin = false)

        home.handleDrawerItemClick(NavItemType.OPPORTUNITIES, null)

        verify(exactly = 1) { PersonalIdUnlocker.unlock(home, any(), any()) }
        assertStarted(home, ConnectActivity::class.java)
    }

    // ---- MESSAGING ----

    @Test
    fun `messaging managed login goes straight to messaging`() {
        val home = buildHome(personalIdManagedLogin = true)

        home.handleDrawerItemClick(NavItemType.MESSAGING, null)

        assertStarted(home, ConnectMessagingActivity::class.java)
        verify(exactly = 0) { PersonalIdUnlocker.unlock(any(), any(), any()) }
    }

    @Test
    fun `messaging legacy login unlocks before messaging`() {
        val home = buildHome(personalIdManagedLogin = false)

        home.handleDrawerItemClick(NavItemType.MESSAGING, null)

        verify(exactly = 1) { PersonalIdUnlocker.unlock(home, any(), any()) }
        assertStarted(home, ConnectMessagingActivity::class.java)
    }

    // ---- WORK_HISTORY ----

    @Test
    fun `work history managed login goes straight to work history`() {
        val home = buildHome(personalIdManagedLogin = true)

        home.handleDrawerItemClick(NavItemType.WORK_HISTORY, null)

        assertStarted(home, PersonalIdWorkHistoryActivity::class.java)
        verify(exactly = 0) { PersonalIdUnlocker.unlock(any(), any(), any()) }
    }

    @Test
    fun `work history legacy login unlocks before work history`() {
        val home = buildHome(personalIdManagedLogin = false)

        home.handleDrawerItemClick(NavItemType.WORK_HISTORY, null)

        verify(exactly = 1) { PersonalIdUnlocker.unlock(home, any(), any()) }
        assertStarted(home, PersonalIdWorkHistoryActivity::class.java)
    }

    private fun assertStarted(
        home: StandardHomeActivity,
        target: Class<*>,
    ) {
        val started: Intent? = shadowOf(home).nextStartedActivity
        assertEquals(target.name, started?.component?.className)
    }
}
