package org.commcare.connect.opportunity

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.viewmodel.ConnectAppInstallViewModel
import org.commcare.dalvik.R
import org.commcare.fragments.RefreshableFragment
import org.commcare.fragments.connect.ConnectDeliveryHomeFragment
import org.commcare.fragments.connect.ConnectJobIntroFragment
import org.commcare.fragments.connect.ConnectLearningProgressFragment

/** Per-opportunity page that shows the status surface for the opportunity's current phase. */
class OpportunityHomeFragment :
    Fragment(R.layout.fragment_opportunity_home),
    OpportunityHomeHost,
    OpportunityNavigator,
    RefreshableFragment {
    private val stateController by lazy { OpportunityHomeStateController(this) }

    private val installViewModel: ConnectAppInstallViewModel by lazy {
        ViewModelProvider(
            requireActivity(),
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application),
        )[ConnectAppInstallViewModel::class.java]
    }

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
        viewLifecycleOwner.lifecycle.addObserver(stateController)
    }

    override fun refresh(forceRefresh: Boolean) {
        (childFragmentManager.findFragmentById(R.id.opportunity_home_container) as? RefreshableFragment)
            ?.refresh(forceRefresh)
    }

    override fun phase(): OpportunityPhase = OpportunityPhase.of(job)

    override fun isBusyInstalling(): Boolean = installViewModel.isInstalling

    override fun showState(state: OpportunityHomeState) {
        if (childFragmentManager.findFragmentByTag(state.name) != null) {
            return
        }
        childFragmentManager
            .beginTransaction()
            .replace(R.id.opportunity_home_container, surfaceFor(state), state.name)
            .commit()
    }

    override fun onPhaseChanged() {
        stateController.reResolveState()
    }

    override fun navigateFromPage(directions: NavDirections) {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.opportunity_home_fragment) {
            return
        }
        navController.navigate(directions)
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
