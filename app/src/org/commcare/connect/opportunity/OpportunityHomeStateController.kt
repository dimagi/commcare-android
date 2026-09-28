package org.commcare.connect.opportunity

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** What [OpportunityHomeStateController] needs from the page it drives. */
interface OpportunityHomeHost {
    fun phase(): OpportunityPhase

    /** Whether an app install is running on the surface currently shown. */
    fun isBusyInstalling(): Boolean

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

    /** Swaps the surface when the resolved state changed; deferred while an install is running. */
    fun reResolveState() {
        if (host.isBusyInstalling()) {
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
