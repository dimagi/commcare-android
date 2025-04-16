package org.commcare.activities;

import android.annotation.SuppressLint;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import android.view.View;
import android.view.ViewTreeObserver;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.adapters.HomeScreenAdapter;
import org.commcare.connect.ConnectIDManager;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.suite.model.Profile;

import java.util.Vector;

/**
 * Handles UI of the normal home screen
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
public class StandardHomeActivityUIController implements CommCareActivityUIController {

    private final StandardHomeActivity activity;

    private HomeScreenAdapter adapter;


    public StandardHomeActivityUIController(StandardHomeActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        activity.setContentView(R.layout.home_screen);
        adapter = new HomeScreenAdapter(activity, getHiddenButtons(), StandardHomeActivity.isDemoUser());
        setupGridView();
        updateSecondaryPhoneConfirmationTile();
    }

    @Override
    public void refreshView() {
        if (adapter != null) {
            // adapter can be null if backstack was cleared for memory reasons
            adapter.notifyDataSetChanged();
        }
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
        if (!ConnectIDManager.getInstance().shouldShowJobStatus(activity, ccApp.getUniqueId())) {
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

    private void updateSecondaryPhoneConfirmationTile() {
        boolean show = activity.getIntent().getBooleanExtra(LoginActivity.CONNECTID_MANAGED_LOGIN, false)
                && ConnectIDManager.getInstance().shouldShowSecondaryPhoneConfirmationTile(activity);
        updateConnectTile(show);
    }

    private void updateConnectTile(boolean show) {
        View connectTile = activity.findViewById(R.id.connect_alert_tile);
        ConnectIDManager.getInstance().updateSecondaryPhoneConfirmationTile(activity, connectTile, show, v -> {
            activity.performSecondaryPhoneVerification();
        });
    }
}
