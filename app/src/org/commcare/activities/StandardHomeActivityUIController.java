package org.commcare.activities;

import android.annotation.SuppressLint;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
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

import static org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_DELIVERING;

/**
 * Handles UI of the normal home screen
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
public class StandardHomeActivityUIController implements CommCareActivityUIController {

    private final StandardHomeActivity activity;
    private View viewJobCard;
    private CardView connectMessageCard;
    private ImageView connectMessageWarningIcon;
    private ConnectProgressJobSummaryAdapter connectProgressJobSummaryAdapter;

    private HomeScreenAdapter adapter;

    public StandardHomeActivityUIController(StandardHomeActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        activity.setContentView(R.layout.home_screen);

        setupConnectJobTile();
        adapter = new HomeScreenAdapter(activity, getHiddenButtons(), StandardHomeActivity.isDemoUser());
        setupGridView();
        activity.toggleDrawerSetUp(true);
        activity.checkForDrawerSetUp();
        setUpToolBar();
    }

    private void setUpToolBar() {
        androidx.appcompat.widget.Toolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar != null) {
            activity.setSupportActionBar(toolbar);
            ActionBar actionBar = activity.getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setTitle(CommCareActivity.getTopLevelTitleName(activity));
            }
        }
    }

    private void setupConnectJobTile() {
        viewJobCard = activity.findViewById(R.id.viewJobCard);
        connectMessageCard = activity.findViewById(R.id.cvConnectMessage);
        connectMessageWarningIcon = activity.findViewById(R.id.ivConnectMessageWarningIcon);
        connectProgressJobSummaryAdapter = new ConnectProgressJobSummaryAdapter(new ArrayList<>());
        RecyclerView recyclerView = viewJobCard.findViewById(R.id.rdDeliveryTypeList);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(connectProgressJobSummaryAdapter);

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

            @StringRes int dateMessageStringRes = job.deliveryComplete()
                    ? R.string.connect_job_ended : R.string.connect_learn_complete_by;
            String formattedEndDate = ConnectDateUtils.INSTANCE.formatDate(job.getProjectEndDate());
            connectJobEndDate.setText(activity.getString(dateMessageStringRes, formattedEndDate));

            String workingHours = job.getWorkingHours();
            boolean showHours = workingHours != null;
            tv_job_time.setVisibility(showHours ? View.VISIBLE : View.GONE);
            hoursTitle.setVisibility(showHours ? View.VISIBLE : View.GONE);

            if (showHours) {
                tv_job_time.setText(workingHours);
            }

            updateConnectJobProgress();
        }
    }

    private void updateConnectJobMessage() {
        String messageText = null;
        String appId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        ConnectAppRecord record = ConnectJobUtils.getAppRecord(activity, appId);
        ConnectJobRecord job = activity.getActiveJob();

        if (job != null && record != null) {
            messageText = job.getCardMessageText(activity);
        }

        if (messageText != null) {
            @ColorRes int textColorRes;
            @ColorRes int backgroundColorRes;

            if (job.readyToTransitionToDelivery()) {
                textColorRes = R.color.connect_green;
                backgroundColorRes = R.color.connect_light_green;
                connectMessageWarningIcon.setVisibility(View.GONE);
            } else if (job.deliveryComplete()) {
                textColorRes = R.color.connect_blue_color;
                backgroundColorRes = R.color.porcelain_grey;
                connectMessageWarningIcon.setVisibility(View.VISIBLE);
            } else {
                textColorRes = R.color.connect_warning_color;
                backgroundColorRes = R.color.connect_light_orange_color;
                connectMessageWarningIcon.setVisibility(View.VISIBLE);
            }

            TextView textView = connectMessageCard.findViewById(R.id.tvConnectMessage);
            textView.setText(messageText);
            textView.setTextColor(ContextCompat.getColor(activity, textColorRes));

            connectMessageCard.setCardBackgroundColor(ContextCompat.getColor(activity, backgroundColorRes));
            connectMessageCard.setVisibility(View.VISIBLE);
        } else {
            connectMessageCard.setVisibility(View.GONE);
            connectMessageWarningIcon.setVisibility(View.GONE);
        }
    }

    @Override
    public void refreshView() {
        if (adapter != null) {
            // adapter can be null if backstack was cleared for memory reasons
            adapter.notifyDataSetChanged();
        }

        updateConnectJobProgress();
    }

    public void updateConnectJobProgress() {
        ConnectJobRecord job = activity.getActiveJob();
        if (job == null) {
            return;
        }

        RecyclerView recyclerView = viewJobCard.findViewById(R.id.rdDeliveryTypeList);
        if (job.getStatus() != STATUS_DELIVERING || job.isFinished()) {
            recyclerView.setVisibility(View.GONE);
        }

        updateConnectJobMessage();

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
