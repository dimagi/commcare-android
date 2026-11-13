package org.commcare.pn.helper

import org.commcare.startup.LandingScreen
import org.commcare.startup.OpportunityLandingInfo

/** Decides which Connect home sits beneath a screen opened from outside the app. */
object NotificationHomeResolver {
    /**
     * Returns the last accessed opportunity's Opportunity Home, or the Opportunity List when that opportunity
     * is [targetOpportunityUuid], no longer exists, or has ended and was already seen ended.
     */
    @JvmStatic
    fun resolve(
        lastAccessedOpportunityUuid: String?,
        opportunities: List<OpportunityLandingInfo>,
        targetOpportunityUuid: String?,
    ): LandingScreen {
        val lastAccessed =
            opportunities.find { it.uuid == lastAccessedOpportunityUuid && it.uuid != targetOpportunityUuid }

        return if (lastAccessed == null || (lastAccessed.hasEnded && lastAccessed.wasSeenEnded)) {
            LandingScreen.OpportunityList
        } else {
            LandingScreen.OpportunityHome(lastAccessed.uuid)
        }
    }
}
