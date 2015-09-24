package org.commcare.dalvik.activities;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.Spannable;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.Toast;

import com.etsy.android.grid.StaggeredGridView;

import org.commcare.android.adapters.HomeScreenAdapter;
import org.commcare.android.database.UserStorageClosedException;
import org.commcare.android.view.SquareButtonWithNotification;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.suite.model.Profile;
import org.javarosa.core.services.locale.Localization;

import java.text.DateFormat;
import java.util.Date;

import in.srain.cube.views.GridViewWithHeaderAndFooter;

/**
 * Created by amstone326 on 9/17/15.
 */
public class HomeActivityUIController {

    private final static String UNSENT_FORM_NUMBER_KEY = "unsent-number-limit";
    private final static String UNSENT_FORM_TIME_KEY = "unsent-time-limit";

    private final CommCareHomeActivity activity;
    private SquareButtonWithNotification startButton;
    private SquareButtonWithNotification logoutButton;
    private SquareButtonWithNotification viewIncompleteFormsButton;
    private SquareButtonWithNotification syncButton;

    private HomeScreenAdapter adapter;
    private GridViewWithHeaderAndFooter gridView;
    private StaggeredGridView newGridView;
    private View mTopBanner;

    private String syncKey = "home.sync";
    private String lastMessageKey = "home.sync.message.last";
    private String homeMessageKey = "home.start";
    private String logoutMessageKey = "home.logout";
    private Pair<Long, int[]> syncDetails;

    public HomeActivityUIController(CommCareHomeActivity activity) {
        this.activity = activity;
    }

    public View getTopBanner() {
        return mTopBanner;
    }

    public void setupUI() {
        setMainView();
        adapter = new HomeScreenAdapter(activity);
        mTopBanner = View.inflate(activity, R.layout.grid_header_top_banner, null);
        setupGridView();
    }

    private void setMainView() {
        // TODO: discover why Android is not loading the correct layout from layout[-land]-v10 and remove this
        if (usingNewLayout()) {
            activity.setContentView(R.layout.mainnew_modern_v10);
        } else {
            activity.setContentView(R.layout.mainnew_modern);
        }
    }

    private boolean usingNewLayout() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1;
    }

    private void setupGridView() {
        final View grid = activity.findViewById(R.id.home_gridview_buttons);

        if (usingNewLayout()) {
            newGridView = (StaggeredGridView) grid;
            newGridView.addHeaderView(mTopBanner);
            newGridView.setAdapter(adapter);
        } else {
            gridView = (GridViewWithHeaderAndFooter)grid;
            gridView.addHeaderView(mTopBanner);
            gridView.setAdapter(adapter);
        }

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
                adapter.notifyDataSetChanged(); // is going to populate the grid with buttons from the adapter (hardcoded there)
                configUI();
            }
        });
    }

    protected void configUI() {
        if (CommCareApplication._().getCurrentApp() == null) {
            // This method depends on there being a seated app, so don't proceed with it if we
            // don't have one
            return;
        }
        setupVersion();
        setupButtons();
        activity.rebuildMenus();
    }

    private void setupVersion() {
        TextView version = (TextView) activity.findViewById(R.id.str_version);
        if (version != null) {
            version.setText(CommCareApplication._().getCurrentVersionString());
        }
    }

    private void setupButtons() {
        setupStartButton();
        setupIncompleteFormsButton();
        setupLogoutButton();
        setupViewOldFormsButton();
        setupSyncButton();
    }

    // region - setup methods for all buttons

    private void setupStartButton() {
        startButton = adapter.getButton(R.layout.home_start_button);
        if (startButton != null) {
            startButton.setText(Localization.get("home.start"));
        }
        adapter.setOnClickListenerForButton(R.layout.home_start_button, getStartButtonListener());
    }

    private void setupIncompleteFormsButton() {
        viewIncompleteFormsButton = adapter.getButton(R.layout.home_incompleteforms_button);
        if (viewIncompleteFormsButton != null) {
            setIncompleteFormsText(CommCareApplication._().getSyncDisplayParameters());
        }
        adapter.setOnClickListenerForButton(R.layout.home_incompleteforms_button, getIncompleteButtonListener());
    }

    private void setupLogoutButton() {
        logoutButton = adapter.getButton(R.layout.home_disconnect_button);
        if (logoutButton != null) {
            logoutButton.setText(Localization.get("home.logout"));
            logoutButton.setNotificationText(activity.getActivityTitle());
            adapter.notifyDataSetChanged();
        }
        adapter.setOnClickListenerForButton(R.layout.home_disconnect_button, getLogoutButtonListener());
    }

    private void setupViewOldFormsButton() {
        SquareButtonWithNotification viewOldForms = adapter.getButton(R.layout.home_savedforms_button);
        if (viewOldForms != null) {
            viewOldForms.setText(Localization.get("home.forms.saved"));
        }
        adapter.setOnClickListenerForButton(R.layout.home_savedforms_button, getViewOldFormsListener());
    }

    private void setupSyncButton() {
        syncButton = adapter.getButton(R.layout.home_sync_button);
        if (syncButton != null) {
            setSyncButtonText(CommCareApplication._().getSyncDisplayParameters(), null);
        }
        adapter.setOnClickListenerForButton(R.layout.home_sync_button, getSyncButtonListener());
    }

    // endregion


    // region - generators for the listeners for each button

    private View.OnClickListener getViewOldFormsListener() {
        return new View.OnClickListener() {
            public void onClick(View v) {
                activity.goToFormArchive(false);
            }
        };
    }

    private View.OnClickListener getSyncButtonListener() {
        return new View.OnClickListener() {
            public void onClick(View v) {
                activity.attemptSync();
            }
        };
    }

    private View.OnClickListener getStartButtonListener() {
        return new View.OnClickListener() {
            public void onClick(View v) {
                activity.enterRootModule();
            }
        };
    }

    private View.OnClickListener getIncompleteButtonListener() {
        return new View.OnClickListener() {
            public void onClick(View v) {
                activity.goToFormArchive(true);
            }
        };
    }

    private View.OnClickListener getLogoutButtonListener() {
        return new View.OnClickListener() {
            public void onClick(View v) {
                CommCareApplication._().closeUserSession();
                activity.returnToLogin();
            }
        };
    }

    // endregion


    // region - text setters for all buttons

    private void setSyncButtonText(Pair<Long, int[]> syncDetails, String syncTextKey) {
        if (syncTextKey == null) {
            syncTextKey = activity.isDemoUser() ? "home.sync.demo" : "home.sync";
        }
        if (syncDetails.second[0] > 0) {
            Spannable syncIndicator = (activity.localize("home.sync.indicator", new String[]{String.valueOf(syncDetails.second[0]), Localization.get(syncTextKey)}));
            syncButton.setNotificationText(syncIndicator);
            adapter.notifyDataSetChanged();
        } else {
            syncButton.setText(activity.localize(syncTextKey));
        }
    }

    private void setIncompleteFormsText(Pair<Long, int[]> syncDetails) {
        if (syncDetails.second[1] > 0) {
            Log.i("syncDetails", "SyncDetails has count " + syncDetails.second[1]);
            Spannable incompleteIndicator = (activity.localize("home.forms.incomplete.indicator", new String[]{String.valueOf(syncDetails.second[1]), Localization.get("home.forms.incomplete")}));
            if (viewIncompleteFormsButton != null) {
                viewIncompleteFormsButton.setText(incompleteIndicator);
            }
        } else {
            Log.i("syncDetails", "SyncDetails has no count");
            if (viewIncompleteFormsButton != null) {
                viewIncompleteFormsButton.setText(activity.localize("home.forms.incomplete"));
            }
        }
    }

    // endregion


    protected void refreshView() {
        refreshVersionText();
        refreshButtonTextSources();
        refreshHomeAndLogoutButtons();
        refreshSyncDetails();
        setIncompleteFormsText(syncDetails);
        refreshSyncButton();
        showSyncMessage();
        setButtonVisibilities();
        activity.updateCommCareBanner();
        adapter.notifyDataSetChanged();
    }


    // region - all helper methods used by refreshView()

    private void refreshVersionText() {
        TextView version = (TextView)activity.findViewById(R.id.str_version);
        if (version == null) {
            return;
        }
        version.setText(CommCareApplication._().getCurrentVersionString());
    }

    private void refreshButtonTextSources() {
        if (activity.isDemoUser()) {
            syncKey = "home.sync.demo";
            lastMessageKey = "home.sync.message.last";
            homeMessageKey = "home.start.demo";
            logoutMessageKey = "home.logout.demo";
        } else {
            syncKey = "home.sync";
            lastMessageKey = "home.sync.message.last";
            homeMessageKey = "home.start";
            logoutMessageKey = "home.logout";
        }
    }

    private void refreshHomeAndLogoutButtons() {
        if (startButton != null) {
            startButton.setText(Localization.get(homeMessageKey));
        }
        if (logoutButton != null) {
            logoutButton.setText(Localization.get(logoutMessageKey));
        }
    }

    private void refreshSyncDetails() {
        try {
            syncDetails = CommCareApplication._().getSyncDisplayParameters();
        } catch (UserStorageClosedException e) {
            activity.returnToLogin();
            return;
        }
    }

    private void refreshSyncButton() {
        if (syncButton != null) {
            setSyncButtonText(syncDetails, syncKey);
        }
    }

    private void showSyncMessage() {
        CharSequence lastSyncTime;
        if (syncDetails.first == 0) {
            lastSyncTime = Localization.get("home.sync.message.last.never");
        } else {
            lastSyncTime = DateUtils.formatSameDayTime(syncDetails.first, new Date().getTime(), DateFormat.DEFAULT, DateFormat.DEFAULT);
        }

        String message = "";
        if (syncDetails.second[0] == 1) {
            message += Localization.get("home.sync.message.unsent.singular") + "\n";
        } else if (syncDetails.second[0] > 1) {
            message += Localization.get("home.sync.message.unsent.plural", new String[]{String.valueOf(syncDetails.second[0])}) + "\n";
        }

        message += Localization.get(lastMessageKey, new String[]{lastSyncTime.toString()});
        displayMessage(message, syncNotOk(), true);
    }

    protected void displayMessage(String message, boolean bad, boolean suppressToast) {
        if (!suppressToast) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }
        adapter.setNotificationTextForButton(R.layout.home_sync_button, message);
    }

    private boolean syncNotOk() {
        return unsentFormNumberLimitExceeded() || unsentFormTimeLimitExceeded();
    }

    private boolean unsentFormNumberLimitExceeded() {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        int unsentFormNumberLimit = Integer.parseInt(prefs.getString(UNSENT_FORM_NUMBER_KEY, "5"));
        return syncDetails.second[0] > unsentFormNumberLimit;
    }

    private boolean unsentFormTimeLimitExceeded() {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        int unsentFormTimeLimit = Integer.parseInt(prefs.getString(UNSENT_FORM_TIME_KEY, "5"));

        long then = syncDetails.first;
        long now = new Date().getTime();
        int secs_ago = (int)((then - now) / 1000);
        int days_ago = secs_ago / 86400;

        return ((-days_ago) > unsentFormTimeLimit) &&
                prefs.getString("server-tether", "push-only").equals("sync");
    }

    private void setButtonVisibilities() {
        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        if (p != null && p.isFeatureActive(Profile.FEATURE_REVIEW)) {
            adapter.setButtonVisibility(R.layout.home_savedforms_button, false);
        }

        boolean showSavedForms = CommCarePreferences.isSavedFormsEnabled();
        adapter.setButtonVisibility(R.layout.home_savedforms_button, !showSavedForms);

        boolean showIncompleteForms = CommCarePreferences.isIncompleteFormsEnabled();
        adapter.setButtonVisibility(R.layout.home_incompleteforms_button, !showIncompleteForms);
    }

    // endregion

}
