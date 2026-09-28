package org.commcare.connect.opportunity

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment

/** The surface Opportunity Home resolved, taken from the page that is the current destination. */
inline fun <reified T : Fragment> NavHostFragment.opportunityHomeSurface(): T {
    val home =
        childFragmentManager.fragments
            .filterIsInstance<OpportunityHomeFragment>()
            .firstOrNull()
            ?: throw AssertionError("Opportunity Home is not the current destination")
    return home.childFragmentManager.fragments
        .filterIsInstance<T>()
        .firstOrNull()
        ?: throw AssertionError("Opportunity Home did not resolve to ${T::class.simpleName}")
}
