package org.commcare.startup

/** The screen the mobile app opens on. */
sealed class LandingScreen {
    object CommCareAppHome : LandingScreen()

    object CommCareLogin : LandingScreen()

    object CommCareSetup : LandingScreen()

    object OpportunityList : LandingScreen()

    data class OpportunityHome(
        val opportunityUuid: String,
    ) : LandingScreen()
}

/** What [LandingScreenResolver] needs to know about one of the user's opportunities. */
data class OpportunityLandingInfo(
    val uuid: String,
    val hasEnded: Boolean,
    val wasSeenEnded: Boolean,
)

/**
 * Everything the landing decision depends on. [opportunityLandingInfos] is empty unless PersonalID is signed in
 * with Connect access and the user didn't cancel or fail the PersonalID unlock.
 */
data class LandingState(
    val hasActiveSession: Boolean,
    val hasSeatedApp: Boolean,
    val seatedAppOpportunityUuid: String?,
    val lastAccessedOpportunityUuid: String?,
    val opportunityLandingInfos: List<OpportunityLandingInfo>,
)

/** Decides which [LandingScreen] the app opens on. */
object LandingScreenResolver {
    @JvmStatic
    fun resolve(state: LandingState): LandingScreen {
        val opportunities = state.opportunityLandingInfos
        val opportunityUuid = if (state.hasSeatedApp) state.seatedAppOpportunityUuid else state.lastAccessedOpportunityUuid
        val opportunity =
            opportunities.find { it.uuid == opportunityUuid }
                ?: opportunities.singleOrNull().takeUnless { state.hasSeatedApp }

        return when {
            opportunity != null && opportunity.hasEnded && opportunity.wasSeenEnded -> LandingScreen.OpportunityList
            opportunity != null -> LandingScreen.OpportunityHome(opportunity.uuid)
            state.hasActiveSession -> LandingScreen.CommCareAppHome
            state.hasSeatedApp -> LandingScreen.CommCareLogin
            opportunities.isEmpty() -> LandingScreen.CommCareSetup
            else -> LandingScreen.OpportunityList
        }
    }
}
