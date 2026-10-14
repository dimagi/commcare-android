package org.commcare.connect.opportunity

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** What [OpportunityHomeStateController] needs from the page it drives. */
interface OpportunityHomeHost {
    fun phase(): OpportunityPhase

    /** Whether the surface shown must stay, because an app install is running or its failure is still shown. */
    fun holdsSurface(): Boolean

    /** Render [state], replacing the surface currently shown. */
    fun showState(state: OpportunityHomeState)
}

/** Resolves the surface Opportunity Home shows and swaps it in, re-resolving on every resume. */
class OpportunityHomeStateController(
    private val host: OpportunityHomeHost,
) : DefaultLifecycleObserver {
    var currentState: OpportunityHomeState? = null
        private set

    override fun onResume(owner: LifecycleOwner) {
        reResolveState()
    }

    /** Swaps the surface when the resolved state changed; deferred while the host holds the surface. */
    fun reResolveState() {
        if (host.holdsSurface()) {
            return
        }
        val resolved = OpportunityHomeState.resolve(host.phase())
        if (resolved == currentState) {
            return
        }
        currentState = resolved
        host.showState(resolved)
    }
}
