package org.commcare.activities.home

import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Host-agnostic coordinator tests. Robolectric is needed only for real `Bundle` and
 * `LifecycleRegistry` behavior — no activity is built.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class HomeActivityCoordinatorTest {
    @Test
    fun `coordinator registers itself on the host lifecycle during construction`() {
        val host = FakeHomeActivityHost()

        HomeActivityCoordinator(host)

        assertEquals(1, host.observerCount)
    }

    @Test
    fun `coordinator receives on-create from the host lifecycle`() {
        val host = FakeHomeActivityHost()
        val coordinator = HomeActivityCoordinator(host)
        host.performRestore()

        host.dispatchOnCreate()

        assertEquals(Lifecycle.State.CREATED, host.lifecycle.currentState)
        assertEquals(1, coordinator.onCreateCallCount)
    }

    @Test
    fun `launch and nav state defaults to false with nothing saved`() {
        val host = FakeHomeActivityHost()
        val coordinator = HomeActivityCoordinator(host)
        host.performRestore()
        host.dispatchOnCreate()

        assertFalse(coordinator.wasExternal)
        assertFalse(coordinator.loginExtraWasConsumed)
        assertFalse(coordinator.pendingEndpointNavigationAfterSync)
    }

    @Test
    fun `all three launch and nav keys survive a save and restore round trip`() {
        val firstHost = FakeHomeActivityHost()
        val first = HomeActivityCoordinator(firstHost)
        firstHost.performRestore()
        firstHost.dispatchOnCreate()
        first.wasExternal = true
        first.loginExtraWasConsumed = true
        first.pendingEndpointNavigationAfterSync = true

        val saved = firstHost.performSave()

        val secondHost = FakeHomeActivityHost()
        val second = HomeActivityCoordinator(secondHost)
        secondHost.performRestore(saved)
        secondHost.dispatchOnCreate()

        assertTrue(second.wasExternal)
        assertTrue(second.loginExtraWasConsumed)
        assertTrue(second.pendingEndpointNavigationAfterSync)
    }

    @Test
    fun `each key round trips independently`() {
        val firstHost = FakeHomeActivityHost()
        val first = HomeActivityCoordinator(firstHost)
        firstHost.performRestore()
        firstHost.dispatchOnCreate()
        first.loginExtraWasConsumed = true

        val saved = firstHost.performSave()

        val secondHost = FakeHomeActivityHost()
        val second = HomeActivityCoordinator(secondHost)
        secondHost.performRestore(saved)
        secondHost.dispatchOnCreate()

        assertFalse(second.wasExternal)
        assertTrue(second.loginExtraWasConsumed)
        assertFalse(second.pendingEndpointNavigationAfterSync)
    }

    /**
     * The session-gated hosts read this state inside `onCreateSessionSafe`, which runs during
     * `Activity.onCreate` — before `ON_CREATE` is dispatched. Restoring only from `onCreate(owner)`
     * would hand them stale defaults on every recreation, so a read before dispatch must already
     * see the restored values.
     */
    @Test
    fun `restored state is readable before on-create is dispatched`() {
        val firstHost = FakeHomeActivityHost()
        val first = HomeActivityCoordinator(firstHost)
        firstHost.performRestore()
        firstHost.dispatchOnCreate()
        first.wasExternal = true
        val saved = firstHost.performSave()

        val secondHost = FakeHomeActivityHost()
        val second = HomeActivityCoordinator(secondHost)
        secondHost.performRestore(saved)
        // No dispatchOnCreate() — this is the moment onCreateSessionSafe runs.

        assertTrue(second.wasExternal)
    }

    /**
     * A write made before `ON_CREATE` (as `processFromExternalLaunch` does) must not be clobbered by
     * the coordinator's own `onCreate` restore pass.
     */
    @Test
    fun `a write before on-create is not overwritten by the on-create restore`() {
        val host = FakeHomeActivityHost()
        val coordinator = HomeActivityCoordinator(host)
        host.performRestore()
        coordinator.wasExternal = true

        host.dispatchOnCreate()

        assertTrue(coordinator.wasExternal)
    }

    @Test
    fun `state set before on-create is still saved`() {
        val host = FakeHomeActivityHost()
        val coordinator = HomeActivityCoordinator(host)
        host.performRestore()
        coordinator.pendingEndpointNavigationAfterSync = true

        val saved = host.performSave()

        val secondHost = FakeHomeActivityHost()
        val second = HomeActivityCoordinator(secondHost)
        secondHost.performRestore(saved)
        secondHost.dispatchOnCreate()

        assertTrue(second.pendingEndpointNavigationAfterSync)
    }
}
