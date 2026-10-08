package org.commcare.connect.opportunity

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class OpportunityHomeStateControllerTest {
    private class FakeHost : OpportunityHomeHost {
        var phase: OpportunityPhase = OpportunityPhase.LEARNING
        var installing: Boolean = false
        val shown = mutableListOf<OpportunityHomeState>()

        override fun phase(): OpportunityPhase = phase

        override fun isBusyInstalling(): Boolean = installing

        override fun showState(state: OpportunityHomeState) {
            shown += state
        }
    }

    private class FakeOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)

        override val lifecycle: Lifecycle get() = registry

        fun resume() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    private fun controllerFor(host: FakeHost): Pair<OpportunityHomeStateController, FakeOwner> {
        val controller = OpportunityHomeStateController(host)
        val owner = FakeOwner()
        owner.registry.addObserver(controller)
        return controller to owner
    }

    @Test
    fun `resolves and shows the surface on first resume`() {
        val host = FakeHost()
        val (controller, owner) = controllerFor(host)

        owner.resume()

        assertEquals(OpportunityHomeState.LEARNING, controller.currentState)
        assertEquals(listOf(OpportunityHomeState.LEARNING), host.shown)
    }

    @Test
    fun `re-resolving to the same state does not swap the surface again`() {
        val host = FakeHost()
        val (controller, owner) = controllerFor(host)
        owner.resume()

        controller.reResolveState()
        controller.reResolveState()

        assertEquals(1, host.shown.size)
    }

    @Test
    fun `a phase change swaps the surface`() {
        val host = FakeHost()
        val (controller, owner) = controllerFor(host)
        owner.resume()

        host.phase = OpportunityPhase.DELIVERY
        controller.reResolveState()

        assertEquals(listOf(OpportunityHomeState.LEARNING, OpportunityHomeState.DELIVERY), host.shown)
    }

    @Test
    fun `a running install defers the re-resolve rather than swapping the surface`() {
        val host = FakeHost()
        val (controller, owner) = controllerFor(host)
        owner.resume()

        host.installing = true
        host.phase = OpportunityPhase.DELIVERY
        controller.reResolveState()
        assertEquals(listOf(OpportunityHomeState.LEARNING), host.shown)

        host.installing = false
        controller.reResolveState()
        assertEquals(OpportunityHomeState.DELIVERY, host.shown.last())
    }

    @Test
    fun `a phase change made while away is shown on the next resume`() {
        val host = FakeHost().apply { phase = OpportunityPhase.AVAILABLE }
        val (_, owner) = controllerFor(host)
        owner.resume()

        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        host.phase = OpportunityPhase.LEARNING
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        assertEquals(listOf(OpportunityHomeState.JOB_INTRO, OpportunityHomeState.LEARNING), host.shown)
    }
}
