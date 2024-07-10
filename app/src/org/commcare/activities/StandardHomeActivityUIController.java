package org.commcare.activities;

import android.annotation.SuppressLint;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import android.content.Context;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.connect.ConnectManager;
import org.commcare.adapters.HomeScreenAdapter;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.suite.model.Profile;

import java.util.Locale;
import java.util.Vector;

/**
 * Handles UI of the normal home screen
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
public class StandardHomeActivityUIController implements CommCareActivityUIController {

    private final StandardHomeActivity activity;

    private ConstraintLayout connectProgressTile;
    private ProgressBar connectProgressBar;
    private TextView connectProgressText;
    private TextView connectProgressMaxText;

    private ConstraintLayout connectTile;

    private HomeScreenAdapter adapter;

    public StandardHomeActivityUIController(StandardHomeActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        activity.setContentView(R.layout.home_screen);
        connectTile = activity.findViewById(R.id.connect_alert_tile);
        connectProgressTile = activity.findViewById(R.id.home_connect_progress_tile);
        connectProgressBar = activity.findViewById(R.id.home_connect_prog_bar);
        connectProgressText = activity.findViewById(R.id.home_connect_prog_text);
        connectProgressMaxText = activity.findViewById(R.id.home_connect_prog_max_text);

        ImageView refreshButton = activity.findViewById(R.id.home_connect_refresh);
        refreshButton.setOnClickListener(v -> activity.updateConnectJobProgress());

        updateConnectProgress();
        adapter = new HomeScreenAdapter(activity, getHiddenButtons(activity), StandardHomeActivity.isDemoUser());
        setupGridView();
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
        String appId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        ConnectAppRecord record = ConnectManager.getAppRecord(activity, appId);
        ConnectJobRecord job = ConnectManager.getActiveJob();
        boolean show = record != null && !record.getIsLearning() && job != null && !job.isFinished();

        if(connectProgressTile != null) {
            connectProgressTile.setVisibility(show ? View.VISIBLE : View.GONE);

            if (show) {
                int today = job.numberOfDeliveriesToday();
                int max = job.getMaxDailyVisits();

                //Configure the progress bar
                connectProgressBar.setMax(max);
                connectProgressBar.setProgress(today);

                //Configure the text fields
                connectProgressText.setText(activity.getString(R.string.connect_home_progress_today, today));
                connectProgressMaxText.setText(String.format(Locale.getDefault(), "%d", max));
            }
        }
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
        if(ConnectManager.getAppRecord(context, ccApp.getUniqueId()) == null) {
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
