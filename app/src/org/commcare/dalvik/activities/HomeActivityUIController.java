package org.commcare.dalvik.activities;

import android.annotation.SuppressLint;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import org.commcare.android.adapters.HomeScreenAdapter;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.suite.model.Profile;

import java.util.Arrays;
import java.util.Vector;

/**
 * @author amstone326
 */
public class HomeActivityUIController {

    private final CommCareHomeActivity activity;

    private HomeScreenAdapter adapter;

    public HomeActivityUIController(CommCareHomeActivity activity) {
        this.activity = activity;
        setupUI();
    }

    private void setupUI() {
        activity.setContentView(R.layout.home_screen);
        adapter = new HomeScreenAdapter(activity, getHiddenButtons(), CommCareHomeActivity.isDemoUser());
        setupGridView();
    }

    private static Vector<String> getHiddenButtons() {
        CommCareApp ccApp = CommCareApplication._().getCurrentApp();
        Vector<String> hiddenButtons = new Vector<>();
        if (ccApp == null) {
            // TODO PLM: Needed since we sometimes try to show buttons before
            // actually entering the home screen. We should eventually fix this.
            hiddenButtons.addAll(Arrays.asList(HomeButtons.buttonNames));
            return hiddenButtons;
        }

        Profile p = ccApp.getCommCarePlatform().getCurrentProfile();
        if ((p != null && !p.isFeatureActive(Profile.FEATURE_REVIEW)) || !CommCarePreferences.isSavedFormsEnabled()) {
            hiddenButtons.add("saved");
        }

        if (!CommCarePreferences.isIncompleteFormsEnabled()) {
            hiddenButtons.add("incomplete");
        }
        if (!DeveloperPreferences.isHomeReportEnabled()) {
            hiddenButtons.add("report");
        }

        return hiddenButtons;
    }

    private void setupGridView() {
        final RecyclerView grid = (RecyclerView)activity.findViewById(R.id.home_gridview_buttons);
        grid.setHasFixedSize(true);

        StaggeredGridLayoutManager gridView =
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        grid.setLayoutManager(gridView);
        grid.setItemAnimator(null);
        grid.setAdapter(adapter);

        grid.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @SuppressLint("NewApi")
            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    grid.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                } else {
                    grid.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }

                grid.requestLayout();
                adapter.notifyDataSetChanged();
                configUI();
            }
        });
    }

    protected void configUI() {
        if (CommCareApplication._().getCurrentApp() != null) {
            activity.rebuildMenus();
        }
    }

    protected void refreshView() {
        adapter.notifyDataSetChanged();
    }

    // TODO: Use syncNeeded flag to change color of sync message
    protected void displayMessage(String message, boolean syncNeeded, boolean suppressToast) {
        if (!suppressToast) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }

        adapter.notifyItemChanged(adapter.getSyncButtonPosition(), message);
    }
}
