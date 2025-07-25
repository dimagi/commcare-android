package org.commcare.activities;

import static org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_DELIVERING;

import android.annotation.SuppressLint;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
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
import org.commcare.connect.ConnectDateUtils;
import org.commcare.connect.ConnectJobHelper;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.suite.model.Profile;

import java.util.ArrayList;
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
    private ConnectProgressJobSummaryAdapter connectProgressJobSummaryAdapter;

    private HomeScreenAdapter adapter;

    public StandardHomeActivityUIController(StandardHomeActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        activity.setContentView(R.layout.home_screen);
        viewJobCard = activity.findViewById(R.id.viewJobCard);
        connectMessageCard = activity.findViewById(R.id.cvConnectMessage);

        connectProgressJobSummaryAdapter = new ConnectProgressJobSummaryAdapter(new ArrayList<>());
        RecyclerView recyclerView = viewJobCard.findViewById(R.id.rdDeliveryTypeList);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(connectProgressJobSummaryAdapter);

        updateJobTileDetails();
        adapter = new HomeScreenAdapter(activity, getHiddenButtons(), StandardHomeActivity.isDemoUser());
        setupGridView();
    }

    private void updateJobTileDetails() {
        ConnectJobRecord job = activity.getActiveJob();
        boolean show = job != null;

        viewJobCard.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            TextView tvJobTitle = viewJobCard.findViewById(R.id.tv_job_title);
            TextView tvViewMore = viewJobCard.findViewById(R.id.tv_view_more);
            TextView tvJobDescription = viewJobCard.findViewById(R.id.tv_job_description);
            TextView hoursTitle = viewJobCard.findViewById(R.id.tvDailyVisitTitle);
            TextView tv_job_time = viewJobCard.findViewById(R.id.tv_job_time);
            TextView connectJobEndDate = viewJobCard.findViewById(R.id.connect_job_end_date);

            tvJobTitle.setText(job.getTitle());
            tvViewMore.setVisibility(View.GONE);
            tvJobDescription.setText(job.getShortDescription());
            connectJobEndDate.setText(activity.getString(R.string.connect_learn_complete_by,
                    ConnectDateUtils.INSTANCE.formatDate(job.getProjectEndDate())));

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
        ConnectAppRecord record = ConnectJobUtils.getAppRecord(activity, appId);
        ConnectJobRecord job = activity.getActiveJob();
        if (job != null && record != null) {
            warningText = job.getWarningMessages(activity);
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

    public void updateConnectProgress() {
        ConnectJobRecord job = activity.getActiveJob();
        if (job == null) {
            return;
        }

        RecyclerView recyclerView = viewJobCard.findViewById(R.id.rdDeliveryTypeList);
        if (job.getStatus() != STATUS_DELIVERING || job.isFinished()) {
            recyclerView.setVisibility(View.GONE);
        }

        updateOpportunityMessage();

        //Note: Only showing a single daily progress bar for now
        //Adding more entries to the list would show multiple progress bars
        //(i.e. one for each payment type)
        List<ConnectDeliveryPaymentSummaryInfo> list = new ArrayList<>();
        list.add(new ConnectDeliveryPaymentSummaryInfo(
                activity.getString(R.string.connect_job_tile_daily_visits),
                job.numberOfDeliveriesToday(),
                job.getMaxDailyVisits()
        ));

        connectProgressJobSummaryAdapter.setDeliverySummaries(list);
    }

    private Vector<String> getHiddenButtons() {
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
        if (!ConnectJobHelper.INSTANCE.shouldShowJobStatus(activity, ccApp.getUniqueId())) {
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
