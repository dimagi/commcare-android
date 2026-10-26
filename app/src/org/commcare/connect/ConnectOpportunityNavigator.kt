package org.commcare.connect

import androidx.fragment.app.Fragment
import androidx.navigation.NavDirections

/** How a status surface hosted by [ConnectOpportunityHomeFragment] reaches the page. */
interface ConnectOpportunityNavigator {
    /** The opportunity moved to a different phase, so the surface shown may no longer fit. */
    fun onPhaseChanged()

    /** Follows one of the page's own nav actions; ignored once the page has been left. */
    fun navigateFromOpportunityHost(directions: NavDirections)

    companion object {
        /** The page hosting [fragment], or null when it is not on one. */
        @JvmStatic
        fun hostOf(fragment: Fragment): ConnectOpportunityNavigator? =
            generateSequence(fragment.parentFragment) { it.parentFragment }
                .filterIsInstance<ConnectOpportunityNavigator>()
                .firstOrNull()
    }
}
