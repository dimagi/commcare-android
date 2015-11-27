package org.commcare.dalvik.activities;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.Spannable;
import android.text.format.DateUtils;
import android.util.Pair;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import org.commcare.android.adapters.HomeScreenAdapter;
import org.commcare.android.database.UserStorageClosedException;
import org.commcare.android.view.SquareButtonWithNotification;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.suite.model.Profile;
import org.javarosa.core.services.locale.Localization;

import java.text.DateFormat;
import java.util.Date;
import java.util.Vector;

/**
 * @author amstone326
 */
public class HomeActivityUIController {

    private final static String UNSENT_FORM_NUMBER_KEY = "unsent-number-limit";
    private final static String UNSENT_FORM_TIME_KEY = "unsent-time-limit";

    private final CommCareHomeActivity activity;

    private HomeScreenAdapter adapter;
    private View mTopBanner;

    private static String syncKey = "home.sync";

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
        activity.setContentView(R.layout.home_screen);
        adapter = new HomeScreenAdapter(activity, getHiddenButtons(), activity.isDemoUser());
        mTopBanner = View.inflate(activity, R.layout.grid_header_top_banner, null);
        setupGridView();
    }

    private static Vector<String> getHiddenButtons() {
        Vector<String> hiddenButtons = new Vector<>();

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
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

        StaggeredGridLayoutManager gridView = new StaggeredGridLayoutManager(2, 1);
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
        }
    }

    private void setupButtons() {
        refreshDataFromSyncDetails();
        setIncompleteFormsText(activity, null, numIncompleteForms); // TODO PLM: null -> the incomplete form button
        setSyncButtonText(activity, null, syncKey, numUnsentForms);
        setLogoutButtonText(activity, null);
    }

    protected void refreshView() {
        // TODO PLM: refresh localization of start button text
        refreshDataFromSyncDetails();
        setIncompleteFormsText(activity, null, numIncompleteForms);
        setSyncButtonText(activity, null, syncKey, numUnsentForms);
        setLogoutButtonText(activity, null);
        showSyncMessage();

        activity.updateCommCareBanner();
        adapter.notifyDataSetChanged();
        //adapter.notifyItemChanged();
    }

    private static void setLogoutButtonText(CommCareHomeActivity activity, SquareButtonWithNotification button) {
        if (button != null) {
            button.setNotificationText(activity.getActivityTitle());
        }
    }

    private static void setSyncButtonText(CommCareHomeActivity activity, SquareButtonWithNotification button, String syncTextKey, int numUnsentForms) {
        if (button == null) {
            return;
        }
        if (numUnsentForms > 0) {
            Spannable syncIndicator = (activity.localize("home.sync.indicator",
                    new String[]{String.valueOf(numUnsentForms), Localization.get(syncTextKey)}));
            button.setNotificationText(syncIndicator);
        } else {
            button.setText(activity.localize(syncTextKey));
        }
    }

    private static void setIncompleteFormsText(CommCareHomeActivity activity, SquareButtonWithNotification button, int numIncompleteForms) {
        if (button != null) {
            if (numIncompleteForms > 0) {
                Spannable incompleteIndicator = (activity.localize("home.forms.incomplete.indicator",
                        new String[]{String.valueOf(numIncompleteForms), Localization.get("home.forms.incomplete")}));
                button.setText(incompleteIndicator);
            } else {
                button.setText(activity.localize("home.forms.incomplete"));
            }
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

        message += Localization.get("home.sync.message.last", new String[]{syncTimeMessage.toString()});
        displayMessage(message, isSyncStronglyNeeded(), true);
    }

    // TODO: Use syncNeeded flag to change color of sync message
    protected void displayMessage(String message, boolean syncNeeded, boolean suppressToast) {
        if (!suppressToast) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }
        //adapter.setNotificationTextForButton(R.layout.home_sync_button, message);
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
}
