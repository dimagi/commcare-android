package org.commcare.activities

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.shadows.ShadowLooper

/**
 * Acceptance coverage for CCCT-2679: the three launch/nav keys now live on
 * `HomeActivityCoordinator` and persist through the host's `SavedStateRegistry` instead of
 * `HomeScreenBaseActivity.onSaveInstanceState`. These tests prove they still survive `recreate()`.
 */
class HomeInstanceStateRecreationTest : HomeScreenActivityTest() {
    @Test
    fun `external launch sets was-external on the coordinator`() {
        val home =
            buildHome {
                putExtra(DispatchActivity.WAS_EXTERNAL, true)
            }

        assertTrue(home.coordinator.wasExternal)
    }

    @Test
    fun `was-external survives recreation`() {
        val controller =
            buildStandardHomeController {
                putExtra(DispatchActivity.WAS_EXTERNAL, true)
            }
        assertTrue(controller.get().coordinator.wasExternal)

        controller.recreate()
        ShadowLooper.idleMainLooper()

        assertTrue(controller.get().coordinator.wasExternal)
    }

    @Test
    fun `login-extra-consumed survives recreation`() {
        val controller = buildStandardHomeController()
        controller.get().coordinator.loginExtraWasConsumed = true

        controller.recreate()
        ShadowLooper.idleMainLooper()

        assertTrue(controller.get().coordinator.loginExtraWasConsumed)
    }

    @Test
    fun `pending endpoint navigation survives recreation`() {
        val controller = buildStandardHomeController()
        controller.get().coordinator.pendingEndpointNavigationAfterSync = true

        controller.recreate()
        ShadowLooper.idleMainLooper()

        assertTrue(controller.get().coordinator.pendingEndpointNavigationAfterSync)
    }

    @Test
    fun `all three keys survive recreation together`() {
        val controller = buildStandardHomeController()
        controller.get().coordinator.apply {
            wasExternal = true
            loginExtraWasConsumed = true
            pendingEndpointNavigationAfterSync = true
        }

        controller.recreate()
        ShadowLooper.idleMainLooper()

        controller.get().coordinator.let {
            assertTrue(it.wasExternal)
            assertTrue(it.loginExtraWasConsumed)
            assertTrue(it.pendingEndpointNavigationAfterSync)
        }
    }

    @Test
    fun `a plain launch leaves all three keys false across recreation`() {
        val controller = buildStandardHomeController()

        controller.recreate()
        ShadowLooper.idleMainLooper()

        controller.get().coordinator.let {
            assertFalse(it.wasExternal)
            assertFalse(it.loginExtraWasConsumed)
            assertFalse(it.pendingEndpointNavigationAfterSync)
        }
    }

    /**
     * The behavioral half of `loginExtraWasConsumed`: arriving from login runs the launch checks
     * once, and a recreation must not run them a second time.
     */
    @Test
    fun `login launch checks do not re-run after recreation`() {
        val controller =
            buildStandardHomeController {
                putExtra(DispatchActivity.START_FROM_LOGIN, true)
                putExtra(LoginActivity.LOGIN_MODE, LoginMode.PASSWORD)
            }
        assertTrue(controller.get().coordinator.loginExtraWasConsumed)

        controller.recreate()
        ShadowLooper.idleMainLooper()

        val recreated = controller.get()
        assertTrue(recreated.coordinator.loginExtraWasConsumed)
        assertFalse(recreated.isFinishing)
    }
}
