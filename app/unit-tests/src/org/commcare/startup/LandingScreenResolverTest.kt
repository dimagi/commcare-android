package org.commcare.startup

import org.junit.Assert.assertEquals
import org.junit.Test

class LandingScreenResolverTest {
    private val activeOpportunity = OpportunityLandingInfo("opp-active", hasEnded = false, wasSeenEnded = false)
    private val otherActiveOpportunity = OpportunityLandingInfo("opp-other-active", hasEnded = false, wasSeenEnded = false)
    private val endedUnseenOpportunity = OpportunityLandingInfo("opp-ended-unseen", hasEnded = true, wasSeenEnded = false)
    private val endedSeenOpportunity = OpportunityLandingInfo("opp-ended-seen", hasEnded = true, wasSeenEnded = true)

    @Test
    fun `an active session on an opportunity's app resumes that opportunity's home`() {
        val state = buildSessionOnOpportunityState(activeOpportunity.uuid, listOf(activeOpportunity, otherActiveOpportunity))

        assertEquals(LandingScreen.OpportunityHome(activeOpportunity.uuid), LandingScreenResolver.resolve(state))
    }

    @Test
    fun `an active session on an app without an opportunity resumes CommCare App Home`() {
        val state = buildState(hasActiveSession = true, hasSeatedApp = true, opportunityLandingInfos = listOf(activeOpportunity))

        assertEquals(LandingScreen.CommCareAppHome, LandingScreenResolver.resolve(state))
    }

    @Test
    fun `an active session skips the last accessed opportunity`() {
        val state =
            buildState(
                hasActiveSession = true,
                hasSeatedApp = true,
                seatedAppOpportunityUuid = activeOpportunity.uuid,
                lastAccessedOpportunityUuid = otherActiveOpportunity.uuid,
                opportunityLandingInfos = listOf(activeOpportunity, otherActiveOpportunity),
            )

        assertEquals(LandingScreen.OpportunityHome(activeOpportunity.uuid), LandingScreenResolver.resolve(state))
    }

    @Test
    fun `an active session on an opportunity that no longer exists resumes CommCare App Home`() {
        val state = buildSessionOnOpportunityState("opp-gone", listOf(activeOpportunity))

        assertEquals(LandingScreen.CommCareAppHome, LandingScreenResolver.resolve(state))
    }

    @Test
    fun `an active session on an opportunity that ended unseen resumes that opportunity's home`() {
        val state = buildSessionOnOpportunityState(endedUnseenOpportunity.uuid, listOf(endedUnseenOpportunity))

        assertEquals(LandingScreen.OpportunityHome(endedUnseenOpportunity.uuid), LandingScreenResolver.resolve(state))
    }

    @Test
    fun `an active session on an opportunity already seen ended lands on the Opportunity List`() {
        val state = buildSessionOnOpportunityState(endedSeenOpportunity.uuid, listOf(endedSeenOpportunity))

        assertEquals(LandingScreen.OpportunityList, LandingScreenResolver.resolve(state))
    }

    @Test
    fun `a seated app without an opportunity signs in on CommCare Login`() {
        val state =
            buildState(
                hasSeatedApp = true,
                lastAccessedOpportunityUuid = activeOpportunity.uuid,
                opportunityLandingInfos = listOf(activeOpportunity),
            )

        assertEquals(LandingScreen.CommCareLogin, LandingScreenResolver.resolve(state))
    }

    @Test
    fun `a seated opportunity app signs back in on that opportunity's home`() {
        val state =
            buildState(
                hasSeatedApp = true,
                seatedAppOpportunityUuid = activeOpportunity.uuid,
                lastAccessedOpportunityUuid = otherActiveOpportunity.uuid,
                opportunityLandingInfos = listOf(activeOpportunity, otherActiveOpportunity),
            )

        assertEquals(LandingScreen.OpportunityHome(activeOpportunity.uuid), LandingScreenResolver.resolve(state))
    }

    @Test
    fun `a seated app whose opportunity no longer exists signs in on CommCare Login`() {
        val state =
            buildState(hasSeatedApp = true, seatedAppOpportunityUuid = "opp-gone", opportunityLandingInfos = listOf(activeOpportunity))

        assertEquals(LandingScreen.CommCareLogin, LandingScreenResolver.resolve(state))
    }

    @Test
    fun `a seated app whose opportunity was already seen ended lands on the Opportunity List`() {
        val state =
            buildState(
                hasSeatedApp = true,
                seatedAppOpportunityUuid = endedSeenOpportunity.uuid,
                opportunityLandingInfos = listOf(endedSeenOpportunity),
            )

        assertEquals(LandingScreen.OpportunityList, LandingScreenResolver.resolve(state))
    }

    @Test
    fun `a user with nothing set up lands on CommCare Setup`() {
        assertEquals(LandingScreen.CommCareSetup, LandingScreenResolver.resolve(buildState()))
    }

    @Test
    fun `a user with opportunities lands on the last accessed one`() {
        val state =
            buildState(
                lastAccessedOpportunityUuid = otherActiveOpportunity.uuid,
                opportunityLandingInfos = listOf(activeOpportunity, otherActiveOpportunity),
            )

        assertEquals(LandingScreen.OpportunityHome(otherActiveOpportunity.uuid), LandingScreenResolver.resolve(state))
    }

    @Test
    fun `a user who never opened their only opportunity lands on its home`() {
        val state = buildState(opportunityLandingInfos = listOf(activeOpportunity))

        assertEquals(LandingScreen.OpportunityHome(activeOpportunity.uuid), LandingScreenResolver.resolve(state))
    }

    @Test
    fun `a user who never opened any of several opportunities lands on the Opportunity List`() {
        val state = buildState(opportunityLandingInfos = listOf(activeOpportunity, otherActiveOpportunity))

        assertEquals(LandingScreen.OpportunityList, LandingScreenResolver.resolve(state))
    }

    @Test
    fun `a last accessed opportunity that no longer exists counts as never opened`() {
        val state = buildState(lastAccessedOpportunityUuid = "opp-gone", opportunityLandingInfos = listOf(activeOpportunity))

        assertEquals(LandingScreen.OpportunityHome(activeOpportunity.uuid), LandingScreenResolver.resolve(state))
    }

    @Test
    fun `a last accessed opportunity that ended unseen reopens once`() {
        val state =
            buildState(
                lastAccessedOpportunityUuid = endedUnseenOpportunity.uuid,
                opportunityLandingInfos = listOf(endedUnseenOpportunity, activeOpportunity),
            )

        assertEquals(LandingScreen.OpportunityHome(endedUnseenOpportunity.uuid), LandingScreenResolver.resolve(state))
    }

    @Test
    fun `a last accessed opportunity already seen ended lands on the Opportunity List`() {
        val state =
            buildState(
                lastAccessedOpportunityUuid = endedSeenOpportunity.uuid,
                opportunityLandingInfos = listOf(endedSeenOpportunity, activeOpportunity),
            )

        assertEquals(LandingScreen.OpportunityList, LandingScreenResolver.resolve(state))
    }

    @Test
    fun `an only opportunity already seen ended lands on the Opportunity List`() {
        val state = buildState(opportunityLandingInfos = listOf(endedSeenOpportunity))

        assertEquals(LandingScreen.OpportunityList, LandingScreenResolver.resolve(state))
    }

    /** An active session on the seated app of [opportunityUuid]. */
    private fun buildSessionOnOpportunityState(
        opportunityUuid: String,
        opportunityLandingInfos: List<OpportunityLandingInfo>,
    ) = buildState(
        hasActiveSession = true,
        hasSeatedApp = true,
        seatedAppOpportunityUuid = opportunityUuid,
        opportunityLandingInfos = opportunityLandingInfos,
    )

    private fun buildState(
        hasActiveSession: Boolean = false,
        hasSeatedApp: Boolean = false,
        seatedAppOpportunityUuid: String? = null,
        lastAccessedOpportunityUuid: String? = null,
        opportunityLandingInfos: List<OpportunityLandingInfo> = emptyList(),
    ) = LandingState(
        hasActiveSession = hasActiveSession,
        hasSeatedApp = hasSeatedApp,
        seatedAppOpportunityUuid = seatedAppOpportunityUuid,
        lastAccessedOpportunityUuid = lastAccessedOpportunityUuid,
        opportunityLandingInfos = opportunityLandingInfos,
    )
}
