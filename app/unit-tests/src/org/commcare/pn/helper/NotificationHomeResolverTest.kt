package org.commcare.pn.helper

import org.commcare.startup.LandingScreen
import org.commcare.startup.OpportunityLandingInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationHomeResolverTest {
    private val activeOpportunity = OpportunityLandingInfo("opp-active", hasEnded = false, wasSeenEnded = false)
    private val targetOpportunity = OpportunityLandingInfo("opp-target", hasEnded = false, wasSeenEnded = false)
    private val endedUnseenOpportunity = OpportunityLandingInfo("opp-ended-unseen", hasEnded = true, wasSeenEnded = false)
    private val endedSeenOpportunity = OpportunityLandingInfo("opp-ended-seen", hasEnded = true, wasSeenEnded = true)

    @Test
    fun `the last accessed opportunity is the home`() {
        val homeScreen = resolveHome(activeOpportunity.uuid, listOf(activeOpportunity, targetOpportunity), targetOpportunity.uuid)

        assertEquals(LandingScreen.OpportunityHome(activeOpportunity.uuid), homeScreen)
    }

    @Test
    fun `the last accessed opportunity is the home when there is no target opportunity`() {
        val homeScreen = resolveHome(activeOpportunity.uuid, listOf(activeOpportunity), null)

        assertEquals(LandingScreen.OpportunityHome(activeOpportunity.uuid), homeScreen)
    }

    @Test
    fun `no last accessed opportunity falls back to the Opportunity List`() {
        val homeScreen = resolveHome(null, listOf(activeOpportunity, targetOpportunity), targetOpportunity.uuid)

        assertEquals(LandingScreen.OpportunityList, homeScreen)
    }

    @Test
    fun `a last accessed opportunity that no longer exists falls back to the Opportunity List`() {
        val homeScreen = resolveHome("opp-gone", listOf(activeOpportunity), null)

        assertEquals(LandingScreen.OpportunityList, homeScreen)
    }

    @Test
    fun `a last accessed opportunity that is the target falls back to the Opportunity List`() {
        val homeScreen = resolveHome(targetOpportunity.uuid, listOf(activeOpportunity, targetOpportunity), targetOpportunity.uuid)

        assertEquals(LandingScreen.OpportunityList, homeScreen)
    }

    @Test
    fun `a last accessed opportunity that ended unseen is the home`() {
        val homeScreen = resolveHome(endedUnseenOpportunity.uuid, listOf(endedUnseenOpportunity), null)

        assertEquals(LandingScreen.OpportunityHome(endedUnseenOpportunity.uuid), homeScreen)
    }

    @Test
    fun `a last accessed opportunity already seen ended falls back to the Opportunity List`() {
        val homeScreen = resolveHome(endedSeenOpportunity.uuid, listOf(endedSeenOpportunity), null)

        assertEquals(LandingScreen.OpportunityList, homeScreen)
    }

    private fun resolveHome(
        lastAccessedOpportunityUuid: String?,
        opportunities: List<OpportunityLandingInfo>,
        targetOpportunityUuid: String?,
    ): LandingScreen = NotificationHomeResolver.resolve(lastAccessedOpportunityUuid, opportunities, targetOpportunityUuid)
}
