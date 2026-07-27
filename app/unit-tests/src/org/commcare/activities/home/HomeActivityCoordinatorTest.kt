package org.commcare.activities.home

import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.junit.Assert.assertEquals
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
}
