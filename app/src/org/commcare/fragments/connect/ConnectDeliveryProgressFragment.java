package org.commcare.fragments.connect;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.Navigation;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;

import org.commcare.activities.connect.ConnectActivity;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectDateUtils;
import org.commcare.connect.ConnectJobHelper;
import org.commcare.connect.PersonalIdManager;
import org.commcare.core.services.CommCarePreferenceManagerFactory;
import org.commcare.core.services.ICommCarePreferenceManager;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectDeliveryProgressBinding;
import org.commcare.dalvik.databinding.ViewJobCardBinding;
import org.commcare.fragments.RefreshableFragment;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.ConnectivityStatus;
import org.javarosa.core.model.utils.DateUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.commcare.connect.ConnectConstants.PAYMENT_CONFIRMATION_HIDDEN_SINCE_TIME;

public class ConnectDeliveryProgressFragment extends ConnectJobFragment<FragmentConnectDeliveryProgressBinding>
        implements RefreshableFragment {
    public static final String TAB_POSITION = "tabPosition";
    public static final int TAB_PROGRESS = 0;
    public static final int TAB_PAYMENT = 1;
    private ViewStateAdapter viewPagerAdapter;
    private ArrayList<ConnectJobPaymentRecord> paymentsToConfirm = new ArrayList<>();
    private int totalUnconfirmedPaymentAmount = 0;
    private int initialTabPosition = 0;
    private boolean isProgrammaticTabChange = false;

    public static ConnectDeliveryProgressFragment newInstance() {
        return new ConnectDeliveryProgressFragment();
    }

    @Override
    public @NotNull View onCreateView(@NotNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        requireActivity().setTitle(R.string.connect_progress_delivery);

        if (getArguments() != null) {
            initialTabPosition = getArguments().getInt(TAB_POSITION, TAB_PROGRESS);
        }

        setupTabViewPager();
        setupJobCard(job);
        setupRefreshAndConfirmationActions();

        updateLastUpdatedText(job.getLastDeliveryUpdate());
        updateCardMessage();
        updatePaymentConfirmationTile(false);

        return view;
    }

    private void setupTabViewPager() {
        viewPagerAdapter = new ViewStateAdapter(getChildFragmentManager(), getLifecycle());
        getBinding().connectDeliveryProgressViewPager.setAdapter(viewPagerAdapter);

        TabLayout tabLayout = getBinding().connectDeliveryProgressTabs;
        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_progress));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_payment));

        if (initialTabPosition == TAB_PAYMENT) {
            TabLayout.Tab tab = tabLayout.getTabAt(TAB_PAYMENT);
            if (tab != null) {
                isProgrammaticTabChange = true;
                tabLayout.selectTab(tab);
                getBinding().connectDeliveryProgressViewPager.setCurrentItem(TAB_PAYMENT, false);
            }
        }

        getBinding().connectDeliveryProgressViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
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
                getBinding().connectDeliveryProgressViewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    @Override
    public void refresh() {
        setWaitDialogEnabled(false);
        ConnectJobHelper.INSTANCE.updateDeliveryProgress(getContext(), job, true, this, success -> {
            if (success && isAdded()) {
                updateLastUpdatedText(new Date());
                updateCardMessage();
                updatePaymentConfirmationTile(false);
                viewPagerAdapter.refresh();
            }
        });
    }

    private void setWaitDialogEnabled(boolean enabled) {
        Activity activity = getActivity();
        if(activity instanceof ConnectActivity connectActivity) {
            connectActivity.setWaitDialogEnabled(enabled);
        }
    }

    private void setupRefreshAndConfirmationActions() {
        getBinding().connectDeliveryRefresh.setOnClickListener(v -> refresh());

        getBinding().connectPaymentConfirmNoButton.setOnClickListener(v ->
                handlePaymentConfirmationNoClick()
        );

        getBinding().connectPaymentConfirmYesButton.setOnClickListener(v ->
                handlePaymentConfirmYesButtonClick()
        );
    }

    private void handlePaymentConfirmationNoClick() {
        updatePaymentConfirmationTile(true);
        FirebaseAnalyticsUtil.reportCccPaymentConfirmationInteraction(false);

        ICommCarePreferenceManager preferenceManager = CommCarePreferenceManagerFactory.getCommCarePreferenceManager();
        preferenceManager.putLong(PAYMENT_CONFIRMATION_HIDDEN_SINCE_TIME, new Date().getTime());
    }

    private void handlePaymentConfirmYesButtonClick() {
        if (paymentsToConfirm.isEmpty()) {
            return;
        }

        FirebaseAnalyticsUtil.reportCccPaymentConfirmationInteraction(true);

        for (ConnectJobPaymentRecord paymentToConfirm : paymentsToConfirm) {
            ConnectJobHelper.INSTANCE.updatePaymentConfirmed(
                    getContext(),
                    paymentToConfirm,
                    true,
                    success -> {
                        boolean paymentConfirmationTileVisible =
                                getBinding().connectDeliveryProgressAlertTile.getVisibility() == View.VISIBLE;

                        if (isAdded() && paymentConfirmationTileVisible) {
                            updatePaymentConfirmationTile(true);
                        }
                    }
            );
        }

        redirectToPaymentTab();
        refresh();
    }

    private void redirectToPaymentTab() {
        TabLayout tabLayout = getBinding().connectDeliveryProgressTabs;
        TabLayout.Tab tab = tabLayout.getTabAt(TAB_PAYMENT);

        isProgrammaticTabChange = true;
        tabLayout.selectTab(tab);
        getBinding().connectDeliveryProgressViewPager.setCurrentItem(TAB_PAYMENT, true);
    }

    private void setupJobCard(ConnectJobRecord job) {
        ViewJobCardBinding jobCard =getBinding().viewJobCard;
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

    private void updateCardMessage() {
        String messageText = job.getCardMessageText(requireContext());

        if (messageText != null) {
            @ColorRes int textColorRes;
            @ColorRes int backgroundColorRes;

            if (job.deliveryComplete()) {
                textColorRes = R.color.connect_blue_color;
                backgroundColorRes = R.color.porcelain_grey;
            } else {
                textColorRes = R.color.connect_warning_color;
                backgroundColorRes = R.color.connect_light_orange_color;
            }

            getBinding().tvConnectMessage.setTextColor(
                    ContextCompat.getColor(requireActivity(), textColorRes)
            );
            getBinding().cvConnectMessage.setCardBackgroundColor(
                    ContextCompat.getColor(requireActivity(), backgroundColorRes)
            );
            getBinding().tvConnectMessage.setText(messageText);
            getBinding().cvConnectMessage.setVisibility(View.VISIBLE);
        } else {
            getBinding().cvConnectMessage.setVisibility(View.GONE);
        }
    }

    private void updatePaymentConfirmationTile(boolean forceHide) {
        paymentsToConfirm.clear();
        totalUnconfirmedPaymentAmount = 0;

        if (!forceHide) {
            for (ConnectJobPaymentRecord payment : job.getPayments()) {
                if (payment.allowConfirm()) {
                    paymentsToConfirm.add(payment);
                    totalUnconfirmedPaymentAmount += Integer.parseInt(payment.getAmount());
                }
            }
        }

        ICommCarePreferenceManager preferenceManager = CommCarePreferenceManagerFactory.getCommCarePreferenceManager();
        long hiddenSinceTimeMs = preferenceManager.getLong(PAYMENT_CONFIRMATION_HIDDEN_SINCE_TIME, -1);
        long timeElapsedSinceLastHiddenMs = new Date().getTime() - hiddenSinceTimeMs;

        boolean userHidTileInPast = hiddenSinceTimeMs != -1;
        boolean showTile = !paymentsToConfirm.isEmpty()
                && ConnectivityStatus.isNetworkAvailable(requireContext())
                && (!userHidTileInPast || timeElapsedSinceLastHiddenMs > DateUtils.DAY_IN_MS * 7);

        getBinding().connectDeliveryProgressAlertTile.setVisibility(showTile ? View.VISIBLE : View.GONE);

        if (showTile) {
            getBinding().connectPaymentConfirmLabel.setText(
                    getString(
                            R.string.connect_payment_confirm_text,
                            totalUnconfirmedPaymentAmount,
                            job.getCurrency(),
                            job.getTitle()
                    )
            );
            FirebaseAnalyticsUtil.reportCccPaymentConfirmationDisplayed();
        }
    }

    private void updateLastUpdatedText(Date lastUpdate) {
        getBinding().connectDeliveryLastUpdate.setText(
                getString(R.string.connect_last_update,
                        ConnectDateUtils.INSTANCE.formatDateTime(lastUpdate)));
    }

    @Override
    protected @NotNull FragmentConnectDeliveryProgressBinding inflateBinding(@NotNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return FragmentConnectDeliveryProgressBinding.inflate(inflater, container, false);
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
                if (fragment instanceof ConnectDeliveryProgressDeliveryFragment deliveryFragment
                        && deliveryFragment.getView() != null) {
                    deliveryFragment.updateProgressSummary();
                } else if (fragment instanceof ConnectResultsSummaryListFragment summaryFragment
                        && summaryFragment.getView() != null) {
                    summaryFragment.updateView();
                }
            }
        }
    }
}
