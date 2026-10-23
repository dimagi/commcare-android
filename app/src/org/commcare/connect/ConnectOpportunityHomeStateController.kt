package org.commcare.connect

/** What [ConnectOpportunityHomeStateController] needs from the page it drives. */
interface ConnectOpportunityHomeHost {
    fun jobStatus(): Int

    /** Whether the surface shown must stay, because an app install is running or its failure is still shown. */
    fun shouldBlockPhaseTransition(): Boolean
}

/** Resolves the surface Opportunity Home shows; the page swaps it in when the state changes. */
class ConnectOpportunityHomeStateController(
    private val host: ConnectOpportunityHomeHost,
) {
    var currentState: ConnectOpportunityHomeState? = null
        private set

    /** Returns whether the resolved state changed; deferred while the host blocks phase transitions. */
    fun reResolveState(): Boolean {
        if (host.shouldBlockPhaseTransition()) {
            return false
        }
        val resolved = ConnectOpportunityHomeState.resolve(host.jobStatus())
        if (resolved == currentState) {
            return false
        }
        currentState = resolved
        return true
    }
}
