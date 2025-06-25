package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.Navigation;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;

import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.connect.ConnectJobHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.PersonalIdManager;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectDeliveryProgressBinding;
import org.commcare.dalvik.databinding.ViewJobCardBinding;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.ConnectivityStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

public class ConnectDeliveryProgressFragment extends ConnectJobFragment {

    private FragmentConnectDeliveryProgressBinding binding;
    private ViewStateAdapter viewPagerAdapter;
    private ConnectJobPaymentRecord paymentToConfirm = null;
    private String initialTabPosition = "";
    private boolean isProgrammaticTabChange = false;

    public static ConnectDeliveryProgressFragment newInstance() {
        return new ConnectDeliveryProgressFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentConnectDeliveryProgressBinding.inflate(inflater, container, false);
        requireActivity().setTitle(R.string.connect_progress_delivery);

        if (getArguments() != null) {
            initialTabPosition = getArguments().getString("tabPosition", "0");
        }

        setupTabViewPager();
        setupMenuProvider();
        setupJobCard(job);
        setupRefreshAndConfirmationActions();

        updateLastUpdatedText(job.getLastDeliveryUpdate());
        updateWarningMessage();
        updatePaymentConfirmationTile(false);

        return binding.getRoot();
    }

    private void setupTabViewPager() {
        viewPagerAdapter = new ViewStateAdapter(getChildFragmentManager(), getLifecycle());
        binding.connectDeliveryProgressViewPager.setAdapter(viewPagerAdapter);

        TabLayout tabLayout = binding.connectDeliveryProgressTabs;
        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_progress));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_payment));

        if (initialTabPosition.equals("1")) {
            TabLayout.Tab tab = tabLayout.getTabAt(1);
            if (tab != null) {
                isProgrammaticTabChange = true;
                tabLayout.selectTab(tab);
                binding.connectDeliveryProgressViewPager.setCurrentItem(1, false);
            }
        }

        binding.connectDeliveryProgressViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (!isProgrammaticTabChange) {
                    tabLayout.selectTab(tabLayout.getTabAt(position));
                    FirebaseAnalyticsUtil.reportConnectTabChange(tabLayout.getTabAt(position).getText().toString());
                } else {
                    isProgrammaticTabChange = false;
                }
            }
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                binding.connectDeliveryProgressViewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupMenuProvider() {
        MenuHost host = requireActivity();
        host.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {}

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.action_sync) {
                    refreshData();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void setupRefreshAndConfirmationActions() {
        binding.connectDeliveryRefresh.setOnClickListener(v -> refreshData());

        binding.connectPaymentConfirmNoButton.setOnClickListener(v -> {
            updatePaymentConfirmationTile(true);
            FirebaseAnalyticsUtil.reportCccPaymentConfirmationInteraction(false);
        });

        binding.connectPaymentConfirmYesButton.setOnClickListener(v -> {
            updatePaymentConfirmationTile(true);
            if (paymentToConfirm != null) {
                FirebaseAnalyticsUtil.reportCccPaymentConfirmationInteraction(true);
                ConnectJobHelper.INSTANCE.updatePaymentConfirmed(getContext(), paymentToConfirm, true, success -> {});
            }
        });
    }

    private void setupJobCard(ConnectJobRecord job) {
        ViewJobCardBinding jobCard =binding.viewJobCard;
        jobCard.tvViewMore.setOnClickListener(v -> Navigation.findNavController(v)
                .navigate(ConnectDeliveryProgressFragmentDirections.actionConnectJobDeliveryProgressFragmentToConnectJobDetailBottomSheetDialogFragment()));

        jobCard.tvJobTitle.setText(job.getTitle());
        jobCard.tvJobDescription.setText(job.getDescription());
        jobCard.connectJobEndDate
                .setText(getString(R.string.connect_learn_complete_by, ConnectManager.formatDate(job.getProjectEndDate())));

        String workingHours = job.getWorkingHours();
        boolean hasHours = workingHours != null;
        jobCard.tvJobTime.setVisibility(hasHours ? View.VISIBLE : View.GONE);
        jobCard.tvDailyVisitTitle.setVisibility(hasHours ? View.VISIBLE : View.GONE);
        if (hasHours) {
            (jobCard.tvJobTime).setText(workingHours);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (PersonalIdManager.getInstance().isloggedIn()) {
            refreshData();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public void refreshData() {
        ConnectJobHelper.INSTANCE.updateDeliveryProgress(getContext(), job, success -> {
            if (success) {
                try {
                    updateLastUpdatedText(new Date());
                    updateWarningMessage();
                    updatePaymentConfirmationTile(false);
                    viewPagerAdapter.refresh();
                } catch (Exception ignored) {}
            }
        });
    }

    private void updateWarningMessage() {

        String warningText = computeWarningText(job);

        CardView warningCard = binding.getRoot().findViewById(R.id.cvConnectMessage);
        if (warningCard != null) {
            warningCard.setVisibility(warningText == null ? View.GONE : View.VISIBLE);
            if (warningText != null) {
                ((TextView) warningCard.findViewById(R.id.tvConnectMessage)).setText(warningText);
            }
        }
    }

    private String computeWarningText(ConnectJobRecord job) {
        if (job.isFinished()) {
            return getString(R.string.connect_progress_warning_ended);
        } else if (job.getProjectStartDate().after(new Date())) {
            return getString(R.string.connect_progress_warning_not_started);
        } else if (job.getIsUserSuspended()) {
            return getString(R.string.user_suspended);
        } else if (job.isMultiPayment()) {
            List<String> warnings = new ArrayList<>();
            Hashtable<String, Integer> total = job.getDeliveryCountsPerPaymentUnit(false);
            Hashtable<String, Integer> today = job.getDeliveryCountsPerPaymentUnit(true);

            for (ConnectPaymentUnitRecord unit : job.getPaymentUnits()) {
                String key = String.valueOf(unit.getUnitId());
                int totalCount = total.containsKey(key) ? total.get(key) : 0;
                int todayCount = today.containsKey(key) ? today.get(key) : 0;

                if (totalCount >= unit.getMaxTotal()) {
                    warnings.add(getString(R.string.connect_progress_warning_max_reached_multi, unit.getName()));
                } else if (todayCount >= unit.getMaxDaily()) {
                    warnings.add(getString(R.string.connect_progress_warning_daily_max_reached_multi, unit.getName()));
                }
            }
            return warnings.isEmpty() ? null : String.join("\n", warnings);
        } else {
            if (job.getDeliveries().size() >= job.getMaxVisits()) {
                return getString(R.string.connect_progress_warning_max_reached_single);
            } else if (job.numberOfDeliveriesToday() >= job.getMaxDailyVisits()) {
                return getString(R.string.connect_progress_warning_daily_max_reached_single);
            }
        }
        return null;
    }

    private void updatePaymentConfirmationTile(boolean forceHide) {
        paymentToConfirm = null;

        if (!forceHide) {
            for (ConnectJobPaymentRecord payment : job.getPayments()) {
                if (payment.allowConfirm()) {
                    paymentToConfirm = payment;
                    break;
                }
            }
        }

        boolean showTile = paymentToConfirm != null && ConnectivityStatus.isNetworkAvailable(getContext());
        binding.connectDeliveryProgressAlertTile.setVisibility(showTile ? View.VISIBLE : View.GONE);

        if (showTile) {
            String date = ConnectManager.formatDate(paymentToConfirm.getDate());
            binding.connectPaymentConfirmLabel.setText(getString(
                    R.string.connect_payment_confirm_text,
                    paymentToConfirm.getAmount(),
                    job.getCurrency(),
                    date));
            FirebaseAnalyticsUtil.reportCccPaymentConfirmationDisplayed();
        }
    }

    private void updateLastUpdatedText(Date lastUpdate) {
        binding.connectDeliveryLastUpdate.setText(
                getString(R.string.connect_last_update, ConnectManager.formatDateTime(lastUpdate)));
    }

    private static class ViewStateAdapter extends FragmentStateAdapter {
        private final List<Fragment> fragments;

        public ViewStateAdapter(@NonNull FragmentManager fm, @NonNull Lifecycle lifecycle) {
            super(fm, lifecycle);
            fragments = new ArrayList<>();
            fragments.add(ConnectDeliveryProgressDeliveryFragment.newInstance());
            fragments.add(ConnectResultsSummaryListFragment.newInstance());
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return fragments.get(position);
        }

        @Override
        public int getItemCount() {
            return fragments.size();
        }

        public void refresh() {
            for (Fragment fragment : fragments) {
                if (fragment instanceof ConnectDeliveryProgressDeliveryFragment deliveryFragment) {
                    deliveryFragment.updateProgressSummary();
                } else if (fragment instanceof ConnectResultsSummaryListFragment summaryFragment) {
                    summaryFragment.updateView();
                }
            }
        }
    }
}
