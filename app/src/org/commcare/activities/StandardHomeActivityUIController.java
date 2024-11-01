package org.commcare.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ProgressBar;
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
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

/**
 * Handles UI of the normal home screen
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
public class StandardHomeActivityUIController implements CommCareActivityUIController {

    private final StandardHomeActivity activity;
    private View viewJobCard;
    private CardView cvDailyLimitView;

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
        viewJobCard = activity.findViewById(R.id.viewJobCard);
        cvDailyLimitView = activity.findViewById(R.id.cvDailyLimitView);
        updateConnectProgress();
        updateJobTileDetails();
        adapter = new HomeScreenAdapter(activity, getHiddenButtons(activity), StandardHomeActivity.isDemoUser());
        setupGridView();
    }

    private void updateJobTileDetails() {
        String appId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        ConnectAppRecord record = ConnectManager.getAppRecord(activity, appId);
        ConnectJobRecord job = ConnectManager.getActiveJob();
        boolean show = record != null && !record.getIsLearning() && job != null && !job.isFinished();

        cvDailyLimitView.setVisibility(show && job.getDaysRemaining() == 0 ? View.VISIBLE : View.GONE);
        viewJobCard.setVisibility(show ? View.VISIBLE : View.GONE);

        if(show) {
            ConnectBoldTextView tvJobTitle = viewJobCard.findViewById(R.id.tv_job_title);
            ConnectMediumTextView tvViewMore = viewJobCard.findViewById(R.id.tv_view_more);
            ConnectMediumTextView tvJobDiscrepation = viewJobCard.findViewById(R.id.tv_job_discrepation);
            ConnectBoldTextView tv_job_time = viewJobCard.findViewById(R.id.tv_job_time);
            ConnectMediumTextView connectJobPay = viewJobCard.findViewById(R.id.connect_job_pay);
            ConnectRegularTextView connectJobEndDate = viewJobCard.findViewById(R.id.connect_job_end_date);

            tvJobTitle.setText(job.getTitle());
            tvViewMore.setVisibility(View.GONE);
            tvJobDiscrepation.setText(job.getDescription());
            connectJobPay.setText(activity.getString(R.string.connect_job_tile_price, String.valueOf(job.getBudgetPerVisit())));
            connectJobEndDate.setText(activity.getString(R.string.connect_learn_complete_by, ConnectManager.formatDate(job.getProjectEndDate())));

            String dailyStart = job.getDailyStartTime();
            if(dailyStart.length() == 0) {
                dailyStart = "00:00";
            } else if(dailyStart.length() > 5) {
                dailyStart = dailyStart.substring(0, 5);
            }

            String dailyFinish = job.getDailyFinishTime();
            if(dailyFinish.length() == 0) {
                dailyFinish = "23:59";
            } else if(dailyFinish.length() > 5) {
                dailyFinish = dailyFinish.substring(0, 5);
            }
            tv_job_time.setText(dailyStart + " - " + dailyFinish);

            updateConnectProgress();
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

        deliveryPaymentInfoList.clear();

        if(job != null) {
            Hashtable<String, Integer> todayDeliveryCounts = job.getDeliveryCountsPerPaymentUnit(true);
            for (int j = 0; j < job.getPaymentUnits().size(); j++) {
                ConnectPaymentUnitRecord unit = job.getPaymentUnits().get(j);
                String stringKey = Integer.toString(unit.getUnitId());
                int amount = 0;
                if (todayDeliveryCounts.containsKey(stringKey)) {
                    amount = todayDeliveryCounts.get(stringKey);
                }
                deliveryPaymentInfoList.add(new ConnectDeliveryPaymentSummaryInfo(
                        unit.getName(),
                        amount,
                        unit.getMaxDaily()
                ));
            }
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
