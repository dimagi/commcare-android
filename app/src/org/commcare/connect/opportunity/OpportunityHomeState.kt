package org.commcare.connect.opportunity

import org.commcare.android.database.connect.models.ConnectJobRecord

/** The stage of an opportunity, which decides the surface Opportunity Home shows. */
enum class OpportunityPhase {
    AVAILABLE,
    LEARNING,
    DELIVERY,
    ;

    companion object {
        /** A finished opportunity is always [DELIVERY]; an unrecognised status falls back to [AVAILABLE]. */
        @JvmStatic
        fun of(job: ConnectJobRecord): OpportunityPhase {
            if (job.isFinished) {
                return DELIVERY
            }
            return when (job.status) {
                ConnectJobRecord.STATUS_LEARNING -> LEARNING
                ConnectJobRecord.STATUS_DELIVERING -> DELIVERY
                else -> AVAILABLE
            }
        }
    }
}

/** The surface Opportunity Home shows. */
enum class OpportunityHomeState {
    JOB_INTRO,
    LEARNING,
    DELIVERY,
    ;

    companion object {
        @JvmStatic
        fun resolve(phase: OpportunityPhase): OpportunityHomeState =
            when (phase) {
                OpportunityPhase.AVAILABLE -> JOB_INTRO
                OpportunityPhase.LEARNING -> LEARNING
                OpportunityPhase.DELIVERY -> DELIVERY
            }
    }
}
