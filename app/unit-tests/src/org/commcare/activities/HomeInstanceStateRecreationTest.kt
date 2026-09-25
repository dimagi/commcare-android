package org.commcare.activities

import org.commcare.android.util.ActivityAssertions.assertOnly
import org.commcare.android.util.ActivityAssertions.startedForResultIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.shadows.ShadowLooper

/**
 * The three launch/nav keys now live on `HomeActivityCoordinator` and persist through the host's
 * `SavedStateRegistry` instead of `HomeScreenBaseActivity.onSaveInstanceState`. These tests prove
 * they still survive `recreate()`.
 */
class HomeInstanceStateRecreationTest : BaseHomeScreenActivityTest() {
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
     *
     * Two details make this row able to fail. `PRIMED` mode carries the checks through to the PIN
     * step, which launches `CreatePinActivity` — a re-run is visible rather than silent. And
     * `START_FROM_LOGIN` is put back before `recreate()`, because `processFromLoginLaunch` strips it
     * from the in-process intent: a process-death restore re-delivers the system's copy with the
     * extra still on it, which is precisely the case the persisted flag exists to cover. Without
     * restoring it the stripped extra alone would suppress the checks, and the row would pass with
     * the flag never being read.
     */
    @Test
    fun `login launch checks do not re-run after recreation`() {
        val controller =
            buildStandardHomeController {
                putExtra(DispatchActivity.START_FROM_LOGIN, true)
                putExtra(LoginActivity.LOGIN_MODE, LoginMode.PRIMED)
            }
        assertTrue(controller.get().coordinator.loginExtraWasConsumed)
        assertEquals(
            "the first login launch should run the checks through to the PIN step",
            CreatePinActivity::class.java.name,
            assertOnly(startedForResultIntents(controller.get())).component!!.className,
        )

        controller.get().intent.putExtra(DispatchActivity.START_FROM_LOGIN, true)
        controller.recreate()
        ShadowLooper.idleMainLooper()

        val recreated = controller.get()
        assertTrue(recreated.coordinator.loginExtraWasConsumed)
        assertEquals(
            "the restored flag should keep the launch checks from running a second time",
            emptyList<String>(),
            startedForResultIntents(recreated).map { it.component?.className },
        )
        assertFalse(recreated.isFinishing)
    }
}
