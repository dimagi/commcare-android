package org.commcare.fragments.connect

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import org.commcare.AppUtils
import org.commcare.activities.CommonBaseActivity
import org.commcare.connect.ConnectAppLaunchController
import org.commcare.connect.database.ConnectTaskUtils
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.connect.viewmodel.ConnectDeliveryHomeViewModel
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentConnectDeliveryHomeBinding
import org.commcare.fragments.RefreshableFragment
import org.commcare.fragments.RefreshableTab
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil

/**
 * Shell hosting the delivery opportunity tabs (Dashboard, Payment, Visits, More) and the bottom
 * launch CTA. Individual tab content lives in the child fragments.
 */
class ConnectDeliveryHomeFragment :
    ConnectJobFragment<FragmentConnectDeliveryHomeBinding>(),
    RefreshableFragment {
    /**
     * This screen's tab strip sits under the toolbar, so the loading and status bars go below the tabs.
     */
    override val loadingBarViewId = R.id.tab_network_loading
    override val statusBarContainerViewId = R.id.tab_status_bar

    private data class TabItem(
        val titleRes: Int,
        val fragmentFactory: () -> Fragment,
        val visible: Boolean = true,
    )

    private val tabs =
        listOf(
            TabItem(R.string.connect_dashboard, { ConnectDeliveryDashboardFragment.newInstance() }),
            TabItem(R.string.connect_payment, { ConnectDeliveryPaymentFragment.newInstance() }),
            TabItem(R.string.connect_visits, { ConnectDeliveryVisitsFragment.newInstance() }),
            TabItem(R.string.connect_more, { ConnectDeliveryMoreFragment.newInstance() }),
        )

    private val visibleTabs get() = tabs.filter { it.visible }

    private val moreTabPosition get() = visibleTabs.indexOfFirst { it.titleRes == R.string.connect_more }

    private lateinit var viewModel: ConnectDeliveryHomeViewModel
    private lateinit var pagerAdapter: DeliveryViewStateAdapter
    private var initialTabPosition = TAB_DASHBOARD
    private var currentTabPosition = TAB_DASHBOARD
    private var learningRetryCallback: ConnectivityManager.NetworkCallback? = null

    private var networkValidated = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        updateActionBarTitle()

        arguments?.let { initialTabPosition = it.getInt(TAB_POSITION, TAB_DASHBOARD) }

        setWaitDialogEnabled(false)
        viewModel =
            ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application),
            )[ConnectDeliveryHomeViewModel::class.java]

        setupTabViewPager()
        binding.connectDeliveryCtaBar.setOnCtaClickListener { launchApp(isLearning = false) }

        observeDeliveryProgress()
        retryLearningFetchOnReconnect()
        return view
    }

    private fun setupTabViewPager() {
        pagerAdapter = DeliveryViewStateAdapter(childFragmentManager, lifecycle, visibleTabs.map { it.fragmentFactory })

        val viewPager = binding.connectDeliveryHomeViewPager
        viewPager.adapter = pagerAdapter

        val tabLayout = binding.connectDeliveryHomeTabs
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.setCustomView(R.layout.view_connect_tab_label)
            tab.setText(visibleTabs[position].titleRes)
        }.attach()

        if (initialTabPosition in visibleTabs.indices) {
            currentTabPosition = initialTabPosition
            viewPager.setCurrentItem(initialTabPosition, false)
        }
        updateCtaBarVisibility()

        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    if (position == currentTabPosition) {
                        return
                    }
                    currentTabPosition = position
                    updateCtaBarVisibility()
                    tabLayout.getTabAt(position)?.text?.let {
                        FirebaseAnalyticsUtil.reportConnectTabChange(it.toString())
                    }
                }
            },
        )
    }

    /**
     * The More tab makes its highest-priority task the primary action, so the shared launch bar gets
     * out of its way.
     */
    private fun updateCtaBarVisibility() {
        binding.connectDeliveryCtaBar.isVisible = currentTabPosition != moreTabPosition
    }

    private fun updateMoreTabBadge() {
        val tab = binding.connectDeliveryHomeTabs.getTabAt(moreTabPosition) ?: return
        val badge = tab.customView?.findViewById<TextView>(R.id.tab_badge) ?: return
        val pendingTasks = ConnectTaskUtils.getPendingTasksForJob(requireContext(), job.jobUUID).size

        badge.isVisible = pendingTasks > 0
        badge.text = pendingTasks.toString()
    }

    /**
     * The tabs read the opportunity back off the activity, and the repository hands back a fresh
     * instance each sync, so the refreshed job has to be published there and not just kept here.
     */
    private fun observeDeliveryProgress() {
        observeDataState(
            viewModel.deliveryProgress,
            { cached ->
                setActiveJob(cached)
                refreshTabs()
            },
            { success ->
                setActiveJob(success)
                refreshTabs()
                loadLearningProgressIfMissing()
            },
        )
        // Observed directly: the user did not ask for this fetch, so it must not show them loading
        // bars or sync errors.
        viewModel.learningProgress.observe(viewLifecycleOwner) { state ->
            if (state is DataState.Success) {
                job.learnings = state.data.learnings
                job.assessments = state.data.assessments
                if (job.latestLearningActivityDate != null) {
                    refreshTabs()
                }
            }
        }
    }

    private fun refreshTabs() {
        updateMoreTabBadge()
        childFragmentManager.fragments.forEach { fragment ->
            if (fragment.view != null && fragment is RefreshableTab) {
                fragment.updateView()
            }
        }
    }

    override fun refresh(forceRefresh: Boolean) {
        viewModel.loadDeliveryProgress(job, forceRefresh)
    }

    /**
     * Learning finished before delivery began, so its records are fetched only for a device that is
     * missing them, and only once delivery progress has landed: both write the opportunity row, and
     * running them together lets one revert the other's fields.
     */
    private fun loadLearningProgressIfMissing() {
        if (job.latestLearningActivityDate == null) {
            viewModel.loadLearningProgress(job)
        }
    }

    private fun retryLearningFetchOnReconnect() {
        val manager = requireContext().getSystemService(ConnectivityManager::class.java) ?: return
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (validated && !networkValidated) {
                        view?.post {
                            if (isAdded && job.latestLearningActivityDate == null) {
                                refresh(false)
                            }
                        }
                    }
                    networkValidated = validated
                }

                override fun onLost(network: Network) {
                    networkValidated = false
                }
            }
        manager.registerDefaultNetworkCallback(callback)
        learningRetryCallback = callback
    }

    override fun onDestroyView() {
        learningRetryCallback?.let {
            requireContext().getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it)
        }
        learningRetryCallback = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        updateActionBarTitle()
        refresh(false)
    }

    private fun updateActionBarTitle() {
        (requireActivity() as CommonBaseActivity)
            .setActionBarTitle(job.title, getString(R.string.connect_progress_delivery))
    }

    /**
     * Launches the opportunity's learn or delivery app, sending the user to the download screen when
     * it isn't installed yet. Tabs route their own launches through here so the install check and the
     * download hand-off live in one place.
     */
    fun launchApp(isLearning: Boolean) {
        val appId = if (isLearning) job.learnAppInfo.appId else job.deliveryAppInfo.appId
        if (AppUtils.isAppInstalled(appId)) {
            ConnectAppLaunchController(this).launchApp(appId, isLearning, Runnable { popSelfOnceHidden() })
            return
        }

        val downloadTitle =
            if (isLearning) R.string.connect_downloading_learn else R.string.connect_downloading_delivery
        val directions =
            ConnectDeliveryHomeFragmentDirections
                .actionConnectDeliveryHomeFragmentToConnectDownloadingFragment(
                    getString(downloadTitle),
                    isLearning,
                )
        findNavController().navigate(directions)
    }

    override fun getEndpoint(): String = ConnectRepository.SYNC_KEY_DELIVERY_PREFIX + job.jobUUID

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentConnectDeliveryHomeBinding = FragmentConnectDeliveryHomeBinding.inflate(inflater, container, false)

    private class DeliveryViewStateAdapter(
        fm: FragmentManager,
        lifecycle: Lifecycle,
        private val fragmentFactories: List<() -> Fragment>,
    ) : FragmentStateAdapter(fm, lifecycle) {
        override fun getItemCount() = fragmentFactories.size

        override fun createFragment(position: Int) = fragmentFactories[position]()
    }

    companion object {
        const val TAB_POSITION = "tab_position"
        const val TAB_DASHBOARD = 0
        const val TAB_PAYMENT = 1
        const val TAB_VISITS = 2
        const val TAB_MORE = 3

        @JvmStatic
        fun newInstance() = ConnectDeliveryHomeFragment()
    }
}
