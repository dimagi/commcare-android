package org.commcare.connect

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.repository.DataState
import org.commcare.connect.viewmodel.ConnectAppInstallViewModel
import org.commcare.connect.viewmodel.ConnectOpportunityHomeViewModel
import org.commcare.dalvik.R
import org.commcare.fragments.RefreshableFragment
import org.commcare.fragments.base.BaseConnectFragment
import org.commcare.fragments.connect.ConnectDeliveryHomeFragment
import org.commcare.fragments.connect.ConnectJobIntroFragment
import org.commcare.fragments.connect.ConnectLearningProgressFragment
import org.commcare.fragments.extensions.hasLiveView
import org.commcare.personalId.PersonalIdUserPreferences

/**
 * Per-opportunity page that shows the status surface for the opportunity's current phase.
 *
 * Unlike other Connect pages it does not extend [BaseConnectFragment] or `ConnectJobFragment`: it only
 * hosts the surfaces, which already bring the binding, network bar, sync and install handling, and it
 * resolves against the activity's current active job rather than a snapshot taken in `onCreate`.
 */
class ConnectOpportunityHomeFragment :
    Fragment(R.layout.fragment_connect_opportunity_home),
    ConnectOpportunityHomeHost,
    ConnectOpportunityNavigator,
    RefreshableFragment {
    private val stateController by lazy { ConnectOpportunityHomeStateController(this) }
    private val viewModel: ConnectOpportunityHomeViewModel by viewModels()

    private val job: ConnectJobRecord
        get() =
            requireNotNull((requireActivity() as ConnectActivity).activeJob) {
                "Opportunity Home needs an active job"
            }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        observeAppInstall()
        observeOpportunityRefresh()
    }

    override fun onResume() {
        super.onResume()
        updateState()
        recordOpportunityVisit()
        viewModel.refreshOpportunities()
    }

    /**
     * An intro view doesn't count as accessing the opportunity. An ended opportunity is marked seen once shown,
     * and the mark clears if the opportunity is extended or reactivated.
     */
    private fun recordOpportunityVisit() {
        if (!ConnectOpportunityHomeState.isIntro(job)) {
            PersonalIdUserPreferences.setLastAccessedOpportunityUuid(job.jobUUID)
        }

        if (job.isFinished) {
            PersonalIdUserPreferences.markEndedOpportunitySeen(job.jobUUID)
        } else {
            PersonalIdUserPreferences.clearEndedOpportunitySeen(job.jobUUID)
        }
    }

    /** A surface held by an install or launch may no longer fit once that is over, however it ended. */
    private fun observeAppInstall() {
        ConnectAppInstallViewModel.of(requireActivity()).installState.observe(viewLifecycleOwner) {
            if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                onPhaseChanged()
            }
        }
    }

    /**
     * Startup can land here without the opportunity refresh the unlock screen used to do, so the page
     * fetches it itself. The job is read back from storage, which the fetch updates and which keeps the
     * learning and delivery progress the opportunities response lacks.
     */
    private fun observeOpportunityRefresh() {
        viewModel.opportunities.observe(viewLifecycleOwner) { state ->
            if (state is DataState.Success) {
                ConnectJobUtils.getCompositeJob(job.jobUUID)?.let(::publishRefreshedJob)
            }
        }
    }

    /** A status change swaps the surface; otherwise the surface redraws, since ending or extending keeps it. */
    private fun publishRefreshedJob(refreshed: ConnectJobRecord) {
        (requireActivity() as ConnectActivity).activeJob = refreshed
        if (!hasLiveView() || childFragmentManager.isStateSaved) {
            return
        }
        val surfaceSwapped = stateController.reResolveState()
        if (surfaceSwapped) {
            stateController.currentState?.let(::showState)
        } else {
            (currentSurface() as? ConnectOpportunitySurface)?.onActiveJobRefreshed()
        }
        recordOpportunityVisit()
    }

    private fun currentSurface(): Fragment? = childFragmentManager.findFragmentById(R.id.connect_opportunity_home_container)

    override fun refresh(forceRefresh: Boolean) {
        (currentSurface() as? RefreshableFragment)
            ?.refresh(forceRefresh)
    }

    override fun jobStatus(): Int = job.status

    fun isShowingIntro(): Boolean = stateController.currentState == ConnectOpportunityHomeState.JOB_INTRO

    override fun shouldBlockPhaseTransition(): Boolean =
        (currentSurface() as? BaseConnectFragment<*>)
            ?.holdsAppInstall == true

    /** Ignored while the page cannot swap surfaces; the next resume re-resolves instead. */
    override fun onPhaseChanged() {
        if (!hasLiveView() || childFragmentManager.isStateSaved) {
            return
        }
        updateState()
        recordOpportunityVisit()
    }

    override fun navigateFromOpportunityHost(directions: NavDirections) {
        if (!isAdded) {
            return
        }
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.connect_opportunity_home_fragment) {
            return
        }
        navController.navigate(directions)
    }

    private fun updateState() {
        if (stateController.reResolveState()) {
            stateController.currentState?.let(::showState)
        }
    }

    private fun showState(state: ConnectOpportunityHomeState) {
        (activity as? ConnectActivity)?.refreshDrawerTopLevel()
        if (childFragmentManager.findFragmentByTag(state.name) != null) {
            return
        }
        childFragmentManager
            .beginTransaction()
            .replace(R.id.connect_opportunity_home_container, surfaceFor(state), state.name)
            .commit()
    }

    private fun surfaceFor(state: ConnectOpportunityHomeState): Fragment =
        when (state) {
            ConnectOpportunityHomeState.JOB_INTRO -> ConnectJobIntroFragment()
            ConnectOpportunityHomeState.LEARNING -> ConnectLearningProgressFragment()
            ConnectOpportunityHomeState.DELIVERY -> deliverySurface()
        }

    /** Passes a link's requested tab through to the delivery surface. */
    private fun deliverySurface(): Fragment {
        val tab =
            arguments?.getInt(ConnectDeliveryHomeFragment.TAB_POSITION, ConnectDeliveryHomeFragment.TAB_DASHBOARD)
                ?: ConnectDeliveryHomeFragment.TAB_DASHBOARD
        return ConnectDeliveryHomeFragment().apply {
            arguments = Bundle().apply { putInt(ConnectDeliveryHomeFragment.TAB_POSITION, tab) }
        }
    }
}
