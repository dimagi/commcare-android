package org.commcare.dalvik.activities;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.Spannable;
import android.text.format.DateUtils;
import android.util.Pair;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import com.etsy.android.grid.StaggeredGridView;

import org.commcare.android.adapters.HomeScreenAdapter;
import org.commcare.android.analytics.GoogleAnalyticsFields;
import org.commcare.android.analytics.GoogleAnalyticsUtils;
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
    private SquareButtonWithNotification viewSavedFormsButton;

    private HomeScreenAdapter adapter;
    private GridViewWithHeaderAndFooter gridView;
    private StaggeredGridView newGridView;
    private View mTopBanner;

    private String syncKey = "home.sync";
    private String lastMessageKey = "home.sync.message.last";
    private String homeMessageKey = "home.start";
    private String logoutMessageKey = "home.logout";
    private String savedFormsKey = "home.forms.saved";

    private long lastSyncTime;
    private int numUnsentForms;
    private int numIncompleteForms;

    public HomeActivityUIController(CommCareHomeActivity activity) {
        this.activity = activity;
        setupUI();
    }

    public View getTopBanner() {
        return mTopBanner;
    }

    private void setupUI() {
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
        setupButtons();
        activity.rebuildMenus();
    }

    private void setupButtons() {
        refreshDataFromSyncDetails();
        setupStartButton();
        setupIncompleteFormsButton();
        setupLogoutButton();
        setupViewSavedFormsButton();
        setupSyncButton();
    }

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
            setIncompleteFormsText();
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

    private void setupViewSavedFormsButton() {
        viewSavedFormsButton = adapter.getButton(R.layout.home_savedforms_button);
        if (viewSavedFormsButton != null) {
            viewSavedFormsButton.setText(Localization.get(savedFormsKey));
        }
        adapter.setOnClickListenerForButton(R.layout.home_savedforms_button, getViewOldFormsListener());
    }

    private void setupSyncButton() {
        syncButton = adapter.getButton(R.layout.home_sync_button);
        if (syncButton != null) {
            setSyncButtonText(null);
        } 
        adapter.setOnClickListenerForButton(R.layout.home_sync_button, getSyncButtonListener());
    }

    private View.OnClickListener getViewOldFormsListener() {
        return new View.OnClickListener() {
            public void onClick(View v) {
                reportButtonClick(GoogleAnalyticsFields.LABEL_SAVED_FORMS);
                activity.goToFormArchive(false);
            }
        };
    }

    private View.OnClickListener getSyncButtonListener() {
        return new View.OnClickListener() {
            public void onClick(View v) {
                reportButtonClick(GoogleAnalyticsFields.LABEL_SYNC);
                activity.attemptSync();
            }
        };
    }

    private View.OnClickListener getStartButtonListener() {
        return new View.OnClickListener() {
            public void onClick(View v) {
                reportButtonClick(GoogleAnalyticsFields.LABEL_START);
                activity.enterRootModule();
            }
        };
    }

    private View.OnClickListener getIncompleteButtonListener() {
        return new View.OnClickListener() {
            public void onClick(View v) {
                reportButtonClick(GoogleAnalyticsFields.LABEL_INCOMPLETE_FORMS);
                activity.goToFormArchive(true);
            }
        };
    }

    private View.OnClickListener getLogoutButtonListener() {
        return new View.OnClickListener() {
            public void onClick(View v) {
                reportButtonClick(GoogleAnalyticsFields.LABEL_LOGOUT);
                CommCareApplication._().closeUserSession();
                activity.userTriggeredLogout();
            }
        };
    }

    public static void reportButtonClick(String buttonLabel) {
        GoogleAnalyticsUtils.reportButtonClick(GoogleAnalyticsFields.SCREEN_HOME,
                buttonLabel);
    }

    private void setSyncButtonText(String syncTextKey) {
        if (syncTextKey == null) {
            syncTextKey = activity.isDemoUser() ? "home.sync.demo" : "home.sync";
        }
        if (numUnsentForms > 0) {
            Spannable syncIndicator = (activity.localize("home.sync.indicator",
                    new String[]{String.valueOf(numUnsentForms), Localization.get(syncTextKey)}));
            syncButton.setNotificationText(syncIndicator);
            adapter.notifyDataSetChanged();
        } else {
            syncButton.setText(activity.localize(syncTextKey));
        }
    }

    private void setIncompleteFormsText() {
        if (numIncompleteForms > 0) {
            Spannable incompleteIndicator = (activity.localize("home.forms.incomplete.indicator",
                    new String[]{String.valueOf(numIncompleteForms), Localization.get("home.forms.incomplete")}));
            if (viewIncompleteFormsButton != null) {
                viewIncompleteFormsButton.setText(incompleteIndicator);
            }
        } else {
            if (viewIncompleteFormsButton != null) {
                viewIncompleteFormsButton.setText(activity.localize("home.forms.incomplete"));
            }
        }
    }

    protected void refreshView() {
        refreshButtonTextSources();
        refreshHomeAndLogoutButtons();

        refreshDataFromSyncDetails();
        setIncompleteFormsText();
        refreshSyncButton();
        refreshSavedFormsButton();
        showSyncMessage();

        setButtonVisibilities();
        activity.updateCommCareBanner();
        adapter.notifyDataSetChanged();
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
            logoutButton.setNotificationText(activity.getActivityTitle());
        } 
    }

    /**
     * Call this method before a new isolated instance of using any of the 3 fields for which it
     * obtains values
     */
    private void refreshDataFromSyncDetails() {
        try {
            Pair<Long, int[]> syncDetails = CommCareApplication._().getSyncDisplayParameters();
            this.lastSyncTime = syncDetails.first;
            this.numUnsentForms = syncDetails.second[0];
            this.numIncompleteForms = syncDetails.second[1];
        } catch (UserStorageClosedException e) {
            activity.launchLogin();
            return;
        }
    }

    private void refreshSyncButton() {
        if (syncButton != null) {
            setSyncButtonText(syncKey);
        } 
    }

    private void refreshSavedFormsButton() {
        if (viewSavedFormsButton != null) {
            viewSavedFormsButton.setText(Localization.get(savedFormsKey));
        }
    }

    private void showSyncMessage() {
        CharSequence syncTimeMessage;
        if (lastSyncTime == 0) {
            syncTimeMessage = Localization.get("home.sync.message.last.never");
        } else {
            syncTimeMessage = DateUtils.formatSameDayTime(lastSyncTime, new Date().getTime(), DateFormat.DEFAULT, DateFormat.DEFAULT);
        }

        String message = "";
        if (numUnsentForms == 1) {
            message += Localization.get("home.sync.message.unsent.singular") + "\n";
        } else if (numUnsentForms > 1) {
            message += Localization.get("home.sync.message.unsent.plural", new String[]{String.valueOf(numUnsentForms)}) + "\n";
        }

        message += Localization.get(lastMessageKey, new String[]{syncTimeMessage.toString()});
        displayMessage(message, isSyncStronglyNeeded(), true);
    }

    // TODO: Use syncNeeded flag to change color of sync message
    protected void displayMessage(String message, boolean syncNeeded, boolean suppressToast) {
        if (!suppressToast) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }
        adapter.setNotificationTextForButton(R.layout.home_sync_button, message);
    }

    private boolean isSyncStronglyNeeded() {
        return unsentFormNumberLimitExceeded() || unsentFormTimeLimitExceeded();
    }

    private boolean unsentFormNumberLimitExceeded() {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        int unsentFormNumberLimit = Integer.parseInt(prefs.getString(UNSENT_FORM_NUMBER_KEY, "5"));
        return numUnsentForms > unsentFormNumberLimit;
    }

    private boolean unsentFormTimeLimitExceeded() {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        int unsentFormTimeLimit = Integer.parseInt(prefs.getString(UNSENT_FORM_TIME_KEY, "5"));

        long then = this.lastSyncTime;
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

}
