package org.commcare.connect.opportunity

/** What [OpportunityHomeStateController] needs from the page it drives. */
interface OpportunityHomeHost {
    fun jobStatus(): Int

    /** Whether the surface shown must stay, because an app install is running or its failure is still shown. */
    fun shouldBlockPhaseTransition(): Boolean
}

/** Resolves the surface Opportunity Home shows; the page swaps it in when the state changes. */
class OpportunityHomeStateController(
    private val host: OpportunityHomeHost,
) {
    var currentState: OpportunityHomeState? = null
        private set

    /** Returns whether the resolved state changed; deferred while the host blocks phase transitions. */
    fun reResolveState(): Boolean {
        if (host.shouldBlockPhaseTransition()) {
            return false
        }
        val resolved = OpportunityHomeState.resolve(host.jobStatus())
        if (resolved == currentState) {
            return false
        }
        currentState = resolved
        return true
    }
}
