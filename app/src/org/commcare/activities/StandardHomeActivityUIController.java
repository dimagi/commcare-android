package org.commcare.activities;

import static org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_DELIVERING;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.adapters.ConnectProgressJobSummaryAdapter;
import org.commcare.adapters.HomeScreenAdapter;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectDeliveryPaymentSummaryInfo;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.suite.model.Profile;
import org.commcare.views.connect.connecttextview.ConnectBoldTextView;
import org.commcare.views.connect.connecttextview.ConnectMediumTextView;
import org.commcare.views.connect.connecttextview.ConnectRegularTextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

/**
 * Handles UI of the normal home screen
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
public class StandardHomeActivityUIController implements CommCareActivityUIController {

    private final StandardHomeActivity activity;
    private View viewJobCard;
    private CardView connectMessageCard;

    private ConstraintLayout connectTile;

    private HomeScreenAdapter adapter;
    List<ConnectDeliveryPaymentSummaryInfo> deliveryPaymentInfoList = new ArrayList<>();

    public StandardHomeActivityUIController(StandardHomeActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        activity.setContentView(R.layout.home_screen);
        connectTile = activity.findViewById(R.id.connect_alert_tile);
        connectTile.setVisibility(View.GONE);
        viewJobCard = activity.findViewById(R.id.viewJobCard);
        connectMessageCard = activity.findViewById(R.id.cvConnectMessage);
        updateConnectProgress();
        updateJobTileDetails();
        adapter = new HomeScreenAdapter(activity, getHiddenButtons(activity), StandardHomeActivity.isDemoUser());
        setupGridView();
    }

    private void updateJobTileDetails() {
        String appId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        ConnectAppRecord record = ConnectManager.getAppRecord(activity, appId);
        ConnectJobRecord job = ConnectManager.getActiveJob();
        boolean show = record != null && !record.getIsLearning();

        viewJobCard.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            ConnectBoldTextView tvJobTitle = viewJobCard.findViewById(R.id.tv_job_title);
            ConnectMediumTextView tvViewMore = viewJobCard.findViewById(R.id.tv_view_more);
            ConnectMediumTextView tvJobDiscrepation = viewJobCard.findViewById(R.id.tv_job_discrepation);
            ConnectBoldTextView hoursTitle = viewJobCard.findViewById(R.id.tvDailyVisitTitle);
            ConnectBoldTextView tv_job_time = viewJobCard.findViewById(R.id.tv_job_time);
            ConnectMediumTextView connectJobPay = viewJobCard.findViewById(R.id.connect_job_pay);
            ConnectRegularTextView connectJobEndDate = viewJobCard.findViewById(R.id.connect_job_end_date);

            tvJobTitle.setText(job.getTitle());
            tvViewMore.setVisibility(View.GONE);
            tvJobDiscrepation.setText(job.getDescription());
            connectJobPay.setText(job.getMoneyString(job.getBudgetPerVisit()));
            connectJobEndDate.setText(activity.getString(R.string.connect_learn_complete_by, ConnectManager.formatDate(job.getProjectEndDate())));

            String workingHours = job.getWorkingHours();
            boolean showHours = workingHours != null;
            tv_job_time.setVisibility(showHours ? View.VISIBLE : View.GONE);
            hoursTitle.setVisibility(showHours ? View.VISIBLE : View.GONE);
            if (showHours) {
                tv_job_time.setText(workingHours);
            }

            updateConnectProgress();
        }
    }

    private void updateOpportunityMessage() {
        String warningText = null;
        String appId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        ConnectAppRecord record = ConnectManager.getAppRecord(activity, appId);
        boolean show = record != null && !record.getIsLearning();
        if (show) {
            ConnectJobRecord job = ConnectManager.getActiveJob();
            if (job.isFinished()) {
                warningText = activity.getString(R.string.connect_progress_warning_ended);
            } else if (job.getProjectStartDate().after(new Date())) {
                warningText = activity.getString(R.string.connect_progress_warning_not_started);
            } else if (job.isMultiPayment()) {
                Hashtable<String, Integer> totalPaymentCounts = job.getDeliveryCountsPerPaymentUnit(false);
                Hashtable<String, Integer> todayPaymentCounts = job.getDeliveryCountsPerPaymentUnit(true);
                List<String> dailyMaxes = new ArrayList<>();
                List<String> totalMaxes = new ArrayList<>();
                for (int i = 0; i < job.getPaymentUnits().size(); i++) {
                    ConnectPaymentUnitRecord unit = job.getPaymentUnits().get(i);
                    String stringKey = Integer.toString(unit.getUnitId());

                    int totalCount = 0;
                    if (totalPaymentCounts.containsKey(stringKey)) {
                        totalCount = totalPaymentCounts.get(stringKey);
                    }

                    if (totalCount >= unit.getMaxTotal()) {
                        //Reached max total for this type
                        totalMaxes.add(unit.getName());
                    } else {
                        int todayCount = 0;
                        if (todayPaymentCounts.containsKey(stringKey)) {
                            todayCount = todayPaymentCounts.get(stringKey);
                        }

                        if (todayCount >= unit.getMaxDaily()) {
                            //Reached daily max for this type
                            dailyMaxes.add(unit.getName());
                        }
                    }
                }

                if (totalMaxes.size() > 0 || dailyMaxes.size() > 0) {
                    warningText = "";
                    if (totalMaxes.size() > 0) {
                        String maxes = String.join(", ", totalMaxes);
                        warningText = activity.getString(R.string.connect_progress_warning_max_reached_multi, maxes);
                    }

                    if (dailyMaxes.size() > 0) {
                        String maxes = String.join(", ", dailyMaxes);
                        warningText += activity.getString(R.string.connect_progress_warning_daily_max_reached_multi, maxes);
                    }
                }
            } else {
                if (job.getDeliveries().size() >= job.getMaxVisits()) {
                    warningText = activity.getString(R.string.connect_progress_warning_max_reached_single);
                } else if (job.numberOfDeliveriesToday() >= job.getMaxDailyVisits()) {
                    warningText = activity.getString(R.string.connect_progress_warning_daily_max_reached_single);
                }
            }
        }

        connectMessageCard.setVisibility(warningText == null ? View.GONE : View.VISIBLE);
        if (warningText != null) {
            TextView tv = connectMessageCard.findViewById(R.id.tvConnectMessage);
            tv.setText(warningText);
        }
    }

    @Override
    public void refreshView() {
        if (adapter != null) {
            // adapter can be null if backstack was cleared for memory reasons
            adapter.notifyDataSetChanged();
        }

        updateConnectProgress();
    }

    public void updateConnectTile(boolean show) {
        ConnectManager.updateSecondaryPhoneConfirmationTile(activity, connectTile, show, v -> {
            activity.performSecondaryPhoneVerification();
        });
    }

    public void updateConnectProgress() {
        RecyclerView recyclerView = viewJobCard.findViewById(R.id.rdDeliveryTypeList);
        ConnectJobRecord job = ConnectManager.getActiveJob();

        if (job.getStatus() == STATUS_DELIVERING && job.isFinished()) {
            recyclerView.setVisibility(View.GONE);
        }

        updateOpportunityMessage();

        deliveryPaymentInfoList.clear();

        if (job != null) {
            //Note: Only showing a single daily progress bar for now
            //Adding more entries to the list would show multiple progress bars
            //(i.e. one for each payment type)
            deliveryPaymentInfoList.add(new ConnectDeliveryPaymentSummaryInfo(
                    activity.getString(R.string.connect_job_tile_daily_visits),
                    job.numberOfDeliveriesToday(),
                    job.getMaxDailyVisits()
            ));
        }

        ConnectProgressJobSummaryAdapter connectProgressJobSummaryAdapter = new ConnectProgressJobSummaryAdapter(deliveryPaymentInfoList);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(connectProgressJobSummaryAdapter);
    }

    private static Vector<String> getHiddenButtons(Context context) {
        CommCareApp ccApp = CommCareApplication.instance().getCurrentApp();
        Vector<String> hiddenButtons = new Vector<>();

        Profile p = ccApp.getCommCarePlatform().getCurrentProfile();
        if ((p != null && !p.isFeatureActive(Profile.FEATURE_REVIEW))
                || !HiddenPreferences.isSavedFormsEnabled()) {
            hiddenButtons.add("saved");
        }

        if (!HiddenPreferences.isIncompleteFormsEnabled()) {
            hiddenButtons.add("incomplete");
        }
        if (!DeveloperPreferences.isHomeReportEnabled()) {
            hiddenButtons.add("report");
        }
        if (!CommCareApplication.instance().getCurrentApp().hasVisibleTrainingContent()) {
            hiddenButtons.add("training");
        }
        if (ConnectManager.getAppRecord(context, ccApp.getUniqueId()) == null) {
            hiddenButtons.add("connect");
        }

        return hiddenButtons;
    }

    private void setupGridView() {
        final RecyclerView grid = activity.findViewById(R.id.home_gridview_buttons);
        grid.setHasFixedSize(false);

        StaggeredGridLayoutManager gridView =
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        grid.setLayoutManager(gridView);
        grid.setItemAnimator(null);
        grid.setAdapter(adapter);

        grid.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @SuppressLint("NewApi")
            @Override
            public void onGlobalLayout() {
                grid.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                grid.requestLayout();
                adapter.notifyDataSetChanged();
                activity.rebuildOptionsMenu();
            }
        });
    }

    protected void updateSyncButtonMessage(String message) {
        // Manually route message payloads since RecyclerView payloads are a pain in the ass
        adapter.setMessagePayload(adapter.getSyncButtonPosition(), message);
        adapter.notifyItemChanged(adapter.getSyncButtonPosition());
    }
}
