package org.commcare.fragments.connect;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import org.commcare.connect.ConnectManager;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.views.connect.RoundedButton;
import org.commcare.views.connect.connecttextview.ConnectBoldTextView;
import org.commcare.views.connect.connecttextview.ConnectMediumTextView;
import org.commcare.views.connect.connecttextview.ConnectRegularTextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

/**
 * Fragment for showing delivery progress for a Connect job
 *
 * @author dviggiano
 */
public class ConnectDeliveryProgressFragment extends Fragment {
    private ConnectDeliveryProgressFragment.ViewStateAdapter viewStateAdapter;
    private TextView updateText;

    private CardView paymentAlertTile;
    private ConnectRegularTextView paymentAlertText;
    private ConnectJobPaymentRecord paymentToConfirm = null;
    private String tabPosition = "";
    boolean isTabChange = false;

    public ConnectDeliveryProgressFragment() {
        // Required empty public constructor
    }

    public static ConnectDeliveryProgressFragment newInstance() {
        ConnectDeliveryProgressFragment fragment = new ConnectDeliveryProgressFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        getActivity().setTitle(R.string.connect_progress_delivery);


        if (getArguments() != null) {
            tabPosition = getArguments().getString("tabPosition", "0");
        }

        View view = inflater.inflate(R.layout.fragment_connect_delivery_progress, container, false);

        updateText = view.findViewById(R.id.connect_delivery_last_update);
        updateUpdatedDate(job.getLastDeliveryUpdate());

        ImageView refreshButton = view.findViewById(R.id.connect_delivery_refresh);
        refreshButton.setOnClickListener(v -> refreshData());

        paymentAlertTile = view.findViewById(R.id.connect_delivery_progress_alert_tile);
        paymentAlertText = view.findViewById(R.id.connect_payment_confirm_label);
        RoundedButton paymentAlertNoButton = view.findViewById(R.id.connect_payment_confirm_no_button);
        paymentAlertNoButton.setOnClickListener(v -> {
            updatePaymentConfirmationTile(getContext(), true);
            FirebaseAnalyticsUtil.reportCccPaymentConfirmationInteraction(false);
        });

        RoundedButton paymentAlertYesButton = view.findViewById(R.id.connect_payment_confirm_yes_button);
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
                getLifecycle());
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

                    View view = viewStateAdapter.createFragment(position).getView();
                    if(view != null) {
                        pager.getLayoutParams().height = view.getMeasuredHeight();
                        pager.requestLayout();
                    }

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

        MenuHost host = (MenuHost)requireActivity();
        host.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                //Activity loads the menu
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.action_sync) {
                    refreshData();
                    return true;
                }

                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        updateConnectWarningMessage(view);
        updatePaymentConfirmationTile(getContext(), false);

        jobCardDataHandle(view,job);
        return view;
    }

    private void jobCardDataHandle(View view, ConnectJobRecord job) {
        View viewJobCard = view.findViewById(R.id.viewJobCard);
        ConnectMediumTextView viewMore = viewJobCard.findViewById(R.id.tv_view_more);
        ConnectBoldTextView tvJobTitle = viewJobCard.findViewById(R.id.tv_job_title);
        ConnectBoldTextView hoursTitle = viewJobCard.findViewById(R.id.tvDailyVisitTitle);
        ConnectBoldTextView tv_job_time = viewJobCard.findViewById(R.id.tv_job_time);
        ConnectMediumTextView tvJobDiscrepation = viewJobCard.findViewById(R.id.tv_job_discrepation);
        ConnectRegularTextView connectJobEndDate = viewJobCard.findViewById(R.id.connect_job_end_date);

        viewMore.setOnClickListener(view1 -> {
            Navigation.findNavController(viewMore).navigate(ConnectDeliveryProgressFragmentDirections.actionConnectJobDeliveryProgressFragmentToConnectJobDetailBottomSheetDialogFragment());
        });

        tvJobTitle.setText(job.getTitle());
        tvJobDiscrepation.setText(job.getDescription());
        connectJobEndDate.setText(getString(R.string.connect_learn_complete_by, ConnectManager.formatDate(job.getProjectEndDate())));

        String workingHours = job.getWorkingHours();
        boolean showHours = workingHours != null;
        tv_job_time.setVisibility(showHours ? View.VISIBLE : View.GONE);
        hoursTitle.setVisibility(showHours ? View.VISIBLE : View.GONE);
        if(showHours) {
            tv_job_time.setText(workingHours);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.action_sync) {
            refreshData();
            return true;
        }

        return false;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (ConnectManager.isConnectIdConfigured()) {
            refreshData();
        }
    }

    public void updateConnectWarningMessage(View cardView) {
        ConnectJobRecord job = ConnectManager.getActiveJob();

        int totalVisitCount = job.getDeliveries().size();
        int dailyVisitCount = job.numberOfDeliveriesToday();
        boolean finished = job.isFinished();
        String warningText = null;
        if (finished) {
            warningText = getString(R.string.connect_progress_warning_ended);
        } else if (job.getProjectStartDate().after(new Date())) {
            warningText = getString(R.string.connect_progress_warning_not_started);
        } else if (job.getIsUserSuspended()) {
            warningText = getString(R.string.user_suspended);
        } else if (job.isMultiPayment()) {
            List<String> warnings = new ArrayList<>();
            Hashtable<String, Integer> totalPaymentCounts = job.getDeliveryCountsPerPaymentUnit(false);
            Hashtable<String, Integer> todayPaymentCounts = job.getDeliveryCountsPerPaymentUnit(true);
            for (int i = 0; i < job.getPaymentUnits().size(); i++) {
                ConnectPaymentUnitRecord unit = job.getPaymentUnits().get(i);
                String stringKey = Integer.toString(unit.getUnitId());

                int totalCount = 0;
                if (totalPaymentCounts.containsKey(stringKey)) {
                    totalCount = totalPaymentCounts.get(stringKey);
                }

                if (totalCount >= unit.getMaxTotal()) {
                    //Reached max total for this type
                    warnings.add(getString(R.string.connect_progress_warning_max_reached_multi, unit.getName()));
                } else {
                    int todayCount = 0;
                    if (todayPaymentCounts.containsKey(stringKey)) {
                        todayCount = todayPaymentCounts.get(stringKey);
                    }

                    if (todayCount >= unit.getMaxDaily()) {
                        //Reached daily max for this type
                        warnings.add(getString(R.string.connect_progress_warning_daily_max_reached_multi,
                                unit.getName()));
                    }
                }
            }

            if (warnings.size() > 0) {
                warningText = String.join("\n", warnings);
            }
        } else {
            if (totalVisitCount >= job.getMaxVisits()) {
                warningText = getString(R.string.connect_progress_warning_max_reached_single);
            } else if (dailyVisitCount >= job.getMaxDailyVisits()) {
                warningText = getString(R.string.connect_progress_warning_daily_max_reached_single);
            }
        }

        CardView connectMessageCard = cardView.findViewById(R.id.cvConnectMessage);
        if(connectMessageCard != null) {
            connectMessageCard.setVisibility(warningText == null ? View.GONE : View.VISIBLE);
            if (warningText != null) {
                TextView tv = connectMessageCard.findViewById(R.id.tvConnectMessage);
                tv.setText(warningText);
            }
        }
    }

    public void refreshData() {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        ConnectManager.updateDeliveryProgress(getContext(), job, success -> {
            if (success) {
                try {
                    updateUpdatedDate(new Date());
                    updateConnectWarningMessage(getView());
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
        private final List<Fragment> fragmentList = new ArrayList<>();

        public ViewStateAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
            super(fragmentManager, lifecycle);
            fragmentList.add(ConnectDeliveryProgressDeliveryFragment.newInstance());
            fragmentList.add(ConnectResultsSummaryListFragment.newInstance());
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return fragmentList.get(position);
        }

        @Override
        public int getItemCount() {
            return fragmentList.size();
        }

        public void refresh() {
            for (Fragment fragment : fragmentList) {
                if (fragment instanceof ConnectDeliveryProgressDeliveryFragment) {
                    ((ConnectDeliveryProgressDeliveryFragment) fragment).updateView();
                } else if (fragment instanceof ConnectResultsSummaryListFragment) {
                    ((ConnectResultsSummaryListFragment) fragment).updateView();
                }
            }
        }
    }
}
