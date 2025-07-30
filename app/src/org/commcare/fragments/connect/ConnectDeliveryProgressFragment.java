package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.Navigation;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;

import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectDateUtils;
import org.commcare.connect.ConnectJobHelper;
import org.commcare.connect.PersonalIdManager;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectDeliveryProgressBinding;
import org.commcare.dalvik.databinding.ViewJobCardBinding;
import org.commcare.fragments.RefreshableFragment;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.ConnectivityStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ConnectDeliveryProgressFragment extends ConnectJobFragment
        implements RefreshableFragment {
    public static final String TAB_POSITION = "tabPosition";
    public static final int TAB_PROGRESS = 0;
    public static final int TAB_PAYMENT = 1;
    private FragmentConnectDeliveryProgressBinding binding;
    private ViewStateAdapter viewPagerAdapter;
    private ConnectJobPaymentRecord paymentToConfirm = null;
    private int initialTabPosition = 0;
    private boolean isProgrammaticTabChange = false;

    public static ConnectDeliveryProgressFragment newInstance() {
        return new ConnectDeliveryProgressFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentConnectDeliveryProgressBinding.inflate(inflater, container, false);
        requireActivity().setTitle(R.string.connect_progress_delivery);

        if (getArguments() != null) {
            initialTabPosition = getArguments().getInt(TAB_POSITION, TAB_PROGRESS);
        }

        setupTabViewPager();
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

        if (initialTabPosition == TAB_PAYMENT) {
            TabLayout.Tab tab = tabLayout.getTabAt(TAB_PAYMENT);
            if (tab != null) {
                isProgrammaticTabChange = true;
                tabLayout.selectTab(tab);
                binding.connectDeliveryProgressViewPager.setCurrentItem(TAB_PAYMENT, false);
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

    @Override
    public void refresh() {
        ConnectJobHelper.INSTANCE.updateDeliveryProgress(getContext(), job, success -> {
            if (success) {
                updateLastUpdatedText(new Date());
                updateWarningMessage();
                updatePaymentConfirmationTile(false);
                viewPagerAdapter.refresh();
            }
        });
    }

    private void setupRefreshAndConfirmationActions() {
        binding.connectDeliveryRefresh.setOnClickListener(v -> refresh());

        binding.connectPaymentConfirmNoButton.setOnClickListener(v -> {
            updatePaymentConfirmationTile(true);
            FirebaseAnalyticsUtil.reportCccPaymentConfirmationInteraction(false);
        });

        binding.connectPaymentConfirmYesButton.setOnClickListener(v -> {
            if (paymentToConfirm != null) {
                FirebaseAnalyticsUtil.reportCccPaymentConfirmationInteraction(true);
                ConnectJobHelper.INSTANCE.updatePaymentConfirmed(getContext(), paymentToConfirm, true, success -> {
                    updatePaymentConfirmationTile(true);
                });
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
                .setText(getString(R.string.connect_learn_complete_by,
                        ConnectDateUtils.INSTANCE.formatDate(job.getProjectEndDate())));

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
            refresh();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void updateWarningMessage() {
        String warningText = job.getWarningMessages(requireContext());
        binding.cvConnectMessage.setVisibility(warningText == null ? View.GONE : View.VISIBLE);
        if (warningText != null) {
            binding.tvConnectMessage.setText(warningText);
        }
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

        boolean showTile = paymentToConfirm != null && ConnectivityStatus.isNetworkAvailable(requireContext());
        binding.connectDeliveryProgressAlertTile.setVisibility(showTile ? View.VISIBLE : View.GONE);

        if (showTile) {
            String date = ConnectDateUtils.INSTANCE.formatDate(paymentToConfirm.getDate());
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
                getString(R.string.connect_last_update,
                        ConnectDateUtils.INSTANCE.formatDateTime(lastUpdate)));
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
