package org.commcare.startup

import org.commcare.CommCareApplication
import org.commcare.connect.PersonalIdSessionManager
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.personalId.PersonalIdUserPreferences

/** Provides the current [LandingState] from the session, the seated app, PersonalID and Connect storage. */
object LandingStateProvider {
    @JvmStatic
    fun getLandingState(personalIdUnlockDeclined: Boolean): LandingState {
        val seatedApp = CommCareApplication.instance().currentApp
        val personalIdSignedIn = PersonalIdSessionManager.getInstance().isloggedIn()
        val seatedAppOpportunityUuid =
            if (personalIdSignedIn && seatedApp != null) {
                ConnectJobUtils.getAppRecord(seatedApp.uniqueId)?.jobUUID
            } else {
                null
            }
        val opportunityLandingInfos =
            if (!personalIdUnlockDeclined && personalIdSignedIn && ConnectUserDatabaseUtil.hasConnectAccess()) {
                getOpportunityLandingInfos()
            } else {
                emptyList()
            }

        return LandingState(
            hasActiveSession = CommCareApplication.hasLoggedInUser(),
            hasSeatedApp = seatedApp != null,
            seatedAppOpportunityUuid = seatedAppOpportunityUuid,
            lastAccessedOpportunityUuid = PersonalIdUserPreferences.getLastAccessedOpportunityUuid(),
            opportunityLandingInfos = opportunityLandingInfos,
        )
    }

    private fun getOpportunityLandingInfos(): List<OpportunityLandingInfo> =
        ConnectJobUtils.getJobs().map { job ->
            OpportunityLandingInfo(
                uuid = job.jobUUID,
                hasEnded = job.isFinished,
                wasSeenEnded = PersonalIdUserPreferences.wasEndedOpportunitySeen(job.jobUUID),
            )
        }
}
