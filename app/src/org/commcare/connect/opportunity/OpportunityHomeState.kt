package org.commcare.connect.opportunity

import org.commcare.android.database.connect.models.ConnectJobRecord
import org.javarosa.core.services.Logger

/** The surface Opportunity Home shows. */
enum class OpportunityHomeState {
    JOB_INTRO,
    LEARNING,
    DELIVERY,
    ;

    companion object {
        /** A finished opportunity keeps its status's surface; an unrecognised status falls back to [JOB_INTRO]. */
        @JvmStatic
        fun resolve(jobStatus: Int): OpportunityHomeState =
            when (jobStatus) {
                ConnectJobRecord.STATUS_AVAILABLE_NEW, ConnectJobRecord.STATUS_AVAILABLE -> JOB_INTRO
                ConnectJobRecord.STATUS_LEARNING -> LEARNING
                ConnectJobRecord.STATUS_DELIVERING -> DELIVERY
                else -> resolveUnrecognised(jobStatus)
            }

        private fun resolveUnrecognised(jobStatus: Int): OpportunityHomeState {
            Logger.exception(
                "Opportunity Home resolved an unrecognised job status",
                IllegalStateException("Job status $jobStatus"),
            )
            return JOB_INTRO
        }
    }
}
