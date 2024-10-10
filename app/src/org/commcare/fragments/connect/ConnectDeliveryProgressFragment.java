package org.commcare.fragments.connect;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;

import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;

import java.util.Date;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Fragment for showing delivery progress for a Connect job
 *
 * @author dviggiano
 */
public class ConnectDeliveryProgressFragment extends Fragment {
    private ConnectDeliveryProgressFragment.ViewStateAdapter viewStateAdapter;
    private TextView updateText;

    private ConstraintLayout paymentAlertTile;
    private TextView paymentAlertText;
    private ConnectJobPaymentRecord paymentToConfirm = null;
    private boolean showLearningLaunch = true;
    private boolean showDeliveryLaunch = true;
    private String tabPosition = "";
    boolean isTabChange = false;

    public ConnectDeliveryProgressFragment() {
        // Required empty public constructor
    }

    public static ConnectDeliveryProgressFragment newInstance(boolean showLearningLaunch, boolean showDeliveryLaunch) {
        ConnectDeliveryProgressFragment fragment = new ConnectDeliveryProgressFragment();
        fragment.showLearningLaunch = showLearningLaunch;
        fragment.showDeliveryLaunch = showDeliveryLaunch;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        getActivity().setTitle(getString(R.string.connect_delivery_progress_title));

        if (getArguments() != null) {
            showLearningLaunch = getArguments().getBoolean("showLaunch", true);
            showDeliveryLaunch = getArguments().getBoolean("showLaunch", true);
            tabPosition = getArguments().getString("tabPosition", "0");
        }

        View view = inflater.inflate(R.layout.fragment_connect_delivery_progress, container, false);

        updateText = view.findViewById(R.id.connect_delivery_last_update);
        updateUpdatedDate(job.getLastDeliveryUpdate());

        ImageView refreshButton = view.findViewById(R.id.connect_delivery_refresh);
        refreshButton.setOnClickListener(v -> refreshData());

        paymentAlertTile = view.findViewById(R.id.connect_delivery_progress_alert_tile);
        paymentAlertText = view.findViewById(R.id.connect_payment_confirm_label);
        TextView paymentAlertNoButton = view.findViewById(R.id.connect_payment_confirm_no_button);
        paymentAlertNoButton.setOnClickListener(v -> {
            updatePaymentConfirmationTile(getContext(), true);
            FirebaseAnalyticsUtil.reportCccPaymentConfirmationInteraction(false);
        });

        TextView paymentAlertYesButton = view.findViewById(R.id.connect_payment_confirm_yes_button);
        paymentAlertYesButton.setOnClickListener(v -> {
            final ConnectJobPaymentRecord payment = paymentToConfirm;
            //Dismiss the tile
            updatePaymentConfirmationTile(getContext(), true);

            if (payment != null) {
                FirebaseAnalyticsUtil.reportCccPaymentConfirmationInteraction(true);

                ConnectManager.updatePaymentConfirmed(getContext(), payment, true, success -> {
                    //Nothing to do
                });
            }
        });

        final ViewPager2 pager = view.findViewById(R.id.connect_delivery_progress_view_pager);
        viewStateAdapter = new ConnectDeliveryProgressFragment.ViewStateAdapter(getChildFragmentManager(),
                getLifecycle(), showLearningLaunch, showDeliveryLaunch);
        pager.setAdapter(viewStateAdapter);

        final TabLayout tabLayout = view.findViewById(R.id.connect_delivery_progress_tabs);
        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_progress));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_payment));

        if (tabPosition.equals("1")) {
            TabLayout.Tab tab = tabLayout.getTabAt(Integer.parseInt(tabPosition));
            if (tab != null) {
                isTabChange = true;
                tabLayout.selectTab(tab);
                pager.setCurrentItem(Integer.parseInt(tabPosition), false);
            }
        }

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                // This flag is used to handle cases when a tab is set programmatically,
                // ensuring that onPageSelection does not set the default tab in such scenarios.
                if (!isTabChange) {
                    TabLayout.Tab tab = tabLayout.getTabAt(position);
                    tabLayout.selectTab(tab);

                    FirebaseAnalyticsUtil.reportConnectTabChange(tab.getText().toString());
                } else {
                    isTabChange = false;
                }
            }
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                pager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        updatePaymentConfirmationTile(getContext(), false);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (ConnectManager.isConnectIdConfigured()) {
            refreshData();
        }
    }

    public void refreshData() {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        ConnectManager.updateDeliveryProgress(getContext(), job, success -> {
            if (success) {
                try {
                    updateUpdatedDate(new Date());
                    updatePaymentConfirmationTile(getContext(), false);
                    viewStateAdapter.refresh();
                } catch (Exception e) {
                    //Ignore exception, happens if we leave the page before API call finishes
                }
            }
        });
    }

    private void updatePaymentConfirmationTile(Context context, boolean forceHide) {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        paymentToConfirm = null;
        if (!forceHide) {
            //Look for at least one payment that needs to be confirmed
            for (ConnectJobPaymentRecord payment : job.getPayments()) {
                if (payment.allowConfirm()) {
                    paymentToConfirm = payment;
                    break;
                }
            }
        }

        //NOTE: Checking for network connectivity here
        boolean show = paymentToConfirm != null;
        if (show) {
            show = ConnectNetworkHelper.isOnline(context);
            FirebaseAnalyticsUtil.reportCccPaymentConfirmationOnlineCheck(show);
        }

        paymentAlertTile.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            String date = ConnectManager.formatDate(paymentToConfirm.getDate());
            paymentAlertText.setText(getString(R.string.connect_payment_confirm_text, paymentToConfirm.getAmount(), job.getCurrency(), date));

            FirebaseAnalyticsUtil.reportCccPaymentConfirmationDisplayed();
        }
    }

    private void updateUpdatedDate(Date lastUpdate) {
        updateText.setText(getString(R.string.connect_last_update, ConnectManager.formatDateTime(lastUpdate)));
    }

    private static class ViewStateAdapter extends FragmentStateAdapter {
        private static ConnectDeliveryProgressDeliveryFragment deliveryFragment = null;
        private static ConnectResultsSummaryListFragment verificationFragment = null;
        private final boolean showLearningLaunch;
        private final boolean showDeliveryLaunch;

        public ViewStateAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle, boolean showLearningLaunch, boolean showDeliveryLaunch) {
            super(fragmentManager, lifecycle);
            this.showLearningLaunch = showLearningLaunch;
            this.showDeliveryLaunch = showDeliveryLaunch;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                deliveryFragment = ConnectDeliveryProgressDeliveryFragment.newInstance(showLearningLaunch, showDeliveryLaunch);
                return deliveryFragment;
            }

            verificationFragment = ConnectResultsSummaryListFragment.newInstance();
            return verificationFragment;
        }

        @Override
        public int getItemCount() {
            return 2;
        }

        public void refresh() {
            if (deliveryFragment != null) {
                deliveryFragment.updateView();
            }

            if (verificationFragment != null) {
                verificationFragment.updateView();
            }
        }
    }
}
