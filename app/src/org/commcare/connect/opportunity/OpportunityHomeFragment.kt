package org.commcare.connect.opportunity

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.viewmodel.ConnectAppInstallViewModel
import org.commcare.dalvik.R
import org.commcare.fragments.RefreshableFragment
import org.commcare.fragments.base.BaseConnectFragment
import org.commcare.fragments.connect.ConnectDeliveryHomeFragment
import org.commcare.fragments.connect.ConnectJobIntroFragment
import org.commcare.fragments.connect.ConnectLearningProgressFragment
import org.commcare.fragments.extensions.hasLiveView

/** Per-opportunity page that shows the status surface for the opportunity's current phase. */
class OpportunityHomeFragment :
    Fragment(R.layout.fragment_opportunity_home),
    OpportunityHomeHost,
    OpportunityNavigator,
    RefreshableFragment {
    private val stateController by lazy { OpportunityHomeStateController(this) }

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
    }

    override fun onResume() {
        super.onResume()
        updateState()
    }

    /** A surface held by an install or launch may no longer fit once that is over, however it ended. */
    private fun observeAppInstall() {
        ConnectAppInstallViewModel.of(requireActivity()).installState.observe(viewLifecycleOwner) {
            if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                onPhaseChanged()
            }
        }
    }

    override fun refresh(forceRefresh: Boolean) {
        (childFragmentManager.findFragmentById(R.id.opportunity_home_container) as? RefreshableFragment)
            ?.refresh(forceRefresh)
    }

    override fun jobStatus(): Int = job.status

    override fun shouldBlockPhaseTransition(): Boolean =
        (childFragmentManager.findFragmentById(R.id.opportunity_home_container) as? BaseConnectFragment<*>)
            ?.holdsAppInstall == true

    /** Ignored while the page cannot swap surfaces; the next resume re-resolves instead. */
    override fun onPhaseChanged() {
        if (!hasLiveView() || childFragmentManager.isStateSaved) {
            return
        }
        updateState()
    }

    override fun navigateFromOpportunityHost(directions: NavDirections) {
        if (!isAdded) {
            return
        }
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.opportunity_home_fragment) {
            return
        }
        navController.navigate(directions)
    }

    private fun updateState() {
        if (stateController.reResolveState()) {
            stateController.currentState?.let(::showState)
        }
    }

    private fun showState(state: OpportunityHomeState) {
        if (childFragmentManager.findFragmentByTag(state.name) != null) {
            return
        }
        childFragmentManager
            .beginTransaction()
            .replace(R.id.opportunity_home_container, surfaceFor(state), state.name)
            .commit()
    }

    private fun surfaceFor(state: OpportunityHomeState): Fragment =
        when (state) {
            OpportunityHomeState.JOB_INTRO -> ConnectJobIntroFragment()
            OpportunityHomeState.LEARNING -> ConnectLearningProgressFragment()
            OpportunityHomeState.DELIVERY -> deliverySurface()
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
