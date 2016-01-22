package org.commcare.android.analytics;

import android.os.Build;
import android.preference.Preference;
import android.preference.PreferenceManager;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;

import java.util.Map;

/**
 * All methods used to report events to google analytics, and all supporting utils
 *
 * @author amstone
 */
public class GoogleAnalyticsUtils {

    /**
     * Report a google analytics event that has only a category and an action
     */
    private static void reportEvent(String category, String action) {
        if (analyticsDisabled() || versionIncompatible()) {
            return;
        }
        getTracker().send(new HitBuilders.EventBuilder()
                .setCategory(category)
                .setAction(action)
                .build());
    }

    /**
     * Report a google analytics event that has a category, action, and label
     */
    private static void reportEvent(String category, String action, String label) {
        if (analyticsDisabled() || versionIncompatible()) {
            return;
        }
        getTracker().send(new HitBuilders.EventBuilder()
                .setCategory(category)
                .setAction(action)
                .setLabel(label)
                .build());
    }

    /**
     * Report a google analytics event that has a category, action, label, and value
     */
    private static void reportEvent(String category, String action, String label, int value) {
        if (analyticsDisabled() || versionIncompatible()) {
            return;
        }
        getTracker().send(new HitBuilders.EventBuilder()
                .setCategory(category)
                .setAction(action)
                .setLabel(label)
                .setValue(value)
                .build());
    }

    /**
     * Report a user event of navigating forward in form entry
     *
     * @param label - Communicates the user's method of navigation (swipe vs. arrow press)
     * @param value - Communicates if form was in completed state when navigation occurred
     */
    public static void reportFormNavForward(String label, int value) {
        reportEvent(
                GoogleAnalyticsFields.CATEGORY_FORM_ENTRY,
                GoogleAnalyticsFields.ACTION_FORWARD,
                label, value);
    }

    /**
     * Report a user event of navigating backward in form entry
     *
     * @param label - Communicates the user's method of navigation (swipe vs. arrow press)
     */
    public static void reportFormNavBackward(String label) {
        reportEvent(
                GoogleAnalyticsFields.CATEGORY_FORM_ENTRY,
                GoogleAnalyticsFields.ACTION_BACKWARD,
                label);
    }

    /**
     * Report a user event of triggering a form exit attempt, and which mode they used to do so
     *
     * @param label - Indicates the way in which the user triggered the form exit
     */
    public static void reportFormQuitAttempt(String label) {
        reportEvent(GoogleAnalyticsFields.CATEGORY_FORM_ENTRY,
                GoogleAnalyticsFields.ACTION_TRIGGER_QUIT_ATTEMPT, label);
    }

    /**
     * Report an event of a form being exited
     *
     * @param label - Communicates which option the user selected on the exit form dialog, or none
     *              if form exit occurred without showing the dialog at all
     */
    public static void reportFormExit(String label) {
        reportEvent(GoogleAnalyticsFields.CATEGORY_FORM_ENTRY,
                GoogleAnalyticsFields.ACTION_EXIT_FORM, label);
    }

    public static void reportHomeButtonClick(String buttonLabel) {
        reportEvent(GoogleAnalyticsFields.CATEGORY_HOME_SCREEN,
                GoogleAnalyticsFields.ACTION_BUTTON,
                buttonLabel);
    }

    /**
     * Report a user event of opening an options menu
     */
    public static void reportOptionsMenuEntry(String category) {
        reportEvent(category, GoogleAnalyticsFields.ACTION_OPTIONS_MENU);
    }

    /**
     * Report a user event of selecting an item within an options menu
     */
    public static void reportOptionsMenuItemEntry(String category, String label) {
        reportEvent(category, GoogleAnalyticsFields.ACTION_OPTIONS_MENU_ITEM, label);
    }

    /**
     * Report a user event of opening a preferences menu
     */
    public static void reportPrefActivityEntry(String category) {
        reportEvent(category, GoogleAnalyticsFields.ACTION_PREF_MENU);
    }

    /**
     * Report a user event of opening the edit dialog for an item in a preferences menu
     */
    public static void reportPrefItemClick(String category, String label) {
        reportEvent(category, GoogleAnalyticsFields.ACTION_VIEW_PREF, label);
    }

    /**
     * Report a user event of changing the value of an item in a preferences menu
     */
    public static void reportEditPref(String category, String label, int value) {
        if (analyticsDisabled() || versionIncompatible()) {
            return;
        }
        HitBuilders.EventBuilder builder = new HitBuilders.EventBuilder();
        builder.setCategory(category)
                .setAction(GoogleAnalyticsFields.ACTION_EDIT_PREF)
                .setLabel(label);
        if (value != -1) {
            builder.setValue(value);
        }
        getTracker().send(builder.build());
    }

    public static void reportEditPref(String category, String label) {
        reportEditPref(category, label, -1);
    }

    /**
     * Report an event of an attempted sync
     *
     * @param action - Communicates whether the sync was user-triggered or auto-triggered
     * @param label - Communicates if the sync was successful
     * @param value - Communicates the nature of the sync if it was successful,
     *              OR the reason for failure if the sync was unsuccessful
     */
    public static void reportSyncAttempt(String action, String label, int value) {
        reportEvent(GoogleAnalyticsFields.CATEGORY_SERVER_COMMUNICATION, action, label, value);
    }

    /**
     * Report a user event of viewing a list of archived forms
     *
     * @param label - Communicates whether the user is viewing incomplete forms or saved forms
     */
    public static void reportViewArchivedFormsList(String label) {
        reportEvent(GoogleAnalyticsFields.CATEGORY_ARCHIVED_FORMS,
                GoogleAnalyticsFields.ACTION_VIEW_FORMS_LIST, label);
    }

    /**
     * Report a user event of opening up a form from a list of archived forms
     *
     * @param label - Communicates whether the form was from the list of incomplete or saved forms
     */
    public static void reportOpenArchivedForm(String label) {
        reportEvent(GoogleAnalyticsFields.CATEGORY_ARCHIVED_FORMS,
                GoogleAnalyticsFields.ACTION_OPEN_ARCHIVED_FORM, label);
    }

    public static void reportAppInstall() {
        reportEvent(GoogleAnalyticsFields.CATEGORY_PRE_LOGIN_STATS,
                GoogleAnalyticsFields.ACTION_APP_INSTALL,
                CommCareApplication._().getCurrentVersionString());
    }

    /**
     * Report the length of a certain user event/action/concept
     *
     * @param action - Communicates the event/action/concept whose length is being measured
     * @param value - Communicates the duration, in seconds
     */
    public static void reportTimedEvent(String action, int value) {
        if (analyticsDisabled() || versionIncompatible()) {
            return;
        }
        getTracker().send(new HitBuilders.EventBuilder()
                .setCategory(GoogleAnalyticsFields.CATEGORY_TIMED_EVENTS)
                .setAction(action)
                .setValue(value)
                .build());
    }

    public static void createPreferenceOnClickListeners(PreferenceManager prefManager,
            Map<String, String> menuIdToAnalyticsEvent, String category ) {

        for (String prefKey : menuIdToAnalyticsEvent.keySet()) {
            createPreferenceOnClickListener(prefManager, prefKey, category,
                    menuIdToAnalyticsEvent.get(prefKey));
        }
    }

    public static void createPreferenceOnClickListener(PreferenceManager manager,
                                                       String prefKey,
                                                       final String category,
                                                       final String analyticsLabel) {
        Preference pref = manager.findPreference(prefKey);
        pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportPrefItemClick(category, analyticsLabel);
                return true;
            }
        });
    }

    private static Tracker getTracker() {
        return CommCareApplication._().getDefaultTracker();
    }

    private static boolean analyticsDisabled() {
        return !CommCarePreferences.isAnalyticsEnabled();
    }

    public static boolean versionIncompatible() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD;
    }

    // Currently unused, should remove later if it doesn't get used
    private static void dispatchQueuedEvents() {
        CommCareApplication._().getAnalyticsInstance().dispatchLocalHits();
    }

}
