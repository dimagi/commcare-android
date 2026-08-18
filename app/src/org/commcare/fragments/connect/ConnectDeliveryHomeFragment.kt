package org.commcare.fragments.connect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import org.commcare.connect.repository.ConnectRepository
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

    private lateinit var viewModel: ConnectDeliveryHomeViewModel
    private lateinit var pagerAdapter: DeliveryViewStateAdapter
    private var initialTabPosition = TAB_DASHBOARD
    private var currentTabPosition = TAB_DASHBOARD

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
        binding.connectDeliveryCtaBar.setOnCtaClickListener { launchDeliveryApp() }

        observeDeliveryProgress()
        return view
    }

    private fun setupTabViewPager() {
        pagerAdapter = DeliveryViewStateAdapter(childFragmentManager, lifecycle, visibleTabs.map { it.fragmentFactory })

        val viewPager = binding.connectDeliveryHomeViewPager
        viewPager.adapter = pagerAdapter

        val tabLayout = binding.connectDeliveryHomeTabs
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.setText(visibleTabs[position].titleRes)
        }.attach()

        if (initialTabPosition in visibleTabs.indices) {
            currentTabPosition = initialTabPosition
            viewPager.setCurrentItem(initialTabPosition, false)
        }

        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    if (position == currentTabPosition) {
                        return
                    }
                    currentTabPosition = position
                    tabLayout.getTabAt(position)?.text?.let {
                        FirebaseAnalyticsUtil.reportConnectTabChange(it.toString())
                    }
                }
            },
        )
    }

    private fun observeDeliveryProgress() {
        observeDataState(
            viewModel.deliveryProgress,
            { cached ->
                job = cached
                refreshTabs()
            },
            { success ->
                job = success
                refreshTabs()
            },
        )
    }

    private fun refreshTabs() {
        childFragmentManager.fragments.forEach { fragment ->
            if (fragment.view != null && fragment is RefreshableTab) {
                fragment.updateView()
            }
        }
    }

    override fun refresh(forceRefresh: Boolean) {
        viewModel.loadDeliveryProgress(job, forceRefresh)
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

    private fun launchDeliveryApp() {
        val appId = job.deliveryAppInfo.appId
        if (AppUtils.isAppInstalled(appId)) {
            ConnectAppLaunchController(this).launchApp(appId, false, Runnable { popSelfOnceHidden() })
        } else {
            val directions =
                ConnectDeliveryHomeFragmentDirections
                    .actionConnectDeliveryHomeFragmentToConnectDownloadingFragment(
                        getString(R.string.connect_downloading_delivery),
                        false,
                    )
            findNavController().navigate(directions)
        }
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
