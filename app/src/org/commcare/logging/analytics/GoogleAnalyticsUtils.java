package org.commcare.logging.analytics;

import android.os.Build;
import android.preference.Preference;
import android.preference.PreferenceManager;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareSetupActivity;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.preferences.CommCarePreferences;

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
                .setCustomDimension(1, CommCareApplication._().getCurrentUserId())
                .setCustomDimension(2, ReportingUtils.getDomain())
                .setCustomDimension(3, BuildConfig.FLAVOR)
                .setCustomDimension(4, "" + CommCareApplication._().isConsumerApp())
                .setCustomDimension(5, ReportingUtils.getAppId())
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
                .setCustomDimension(1, CommCareApplication._().getCurrentUserId())
                .setCustomDimension(2, ReportingUtils.getDomain())
                .setCustomDimension(3, BuildConfig.FLAVOR)
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
                .setCustomDimension(1, CommCareApplication._().getCurrentUserId())
                .setCustomDimension(2, ReportingUtils.getDomain())
                .setCustomDimension(3, BuildConfig.FLAVOR)
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

    public static void reportAdvancedActionItemClick(String action) {
        reportEvent(GoogleAnalyticsFields.CATEGORY_ADVANCED_ACTIONS, action);
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
                .setCustomDimension(1, CommCareApplication._().getCurrentUserId())
                .setCustomDimension(2, ReportingUtils.getDomain())
                .setCustomDimension(3, BuildConfig.FLAVOR)
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
     * @param label  - Communicates if the sync was successful
     * @param value  - Communicates the nature of the sync if it was successful,
     *               OR the reason for failure if the sync was unsuccessful
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

    public static void reportAppInstall(int lastInstallModeCode) {
        reportEvent(GoogleAnalyticsFields.CATEGORY_APP_INSTALL,
                CommCareSetupActivity.getAnalyticsActionFromInstallMode(lastInstallModeCode),
                CommCareApplication._().getCurrentVersionString());
    }

    /**
     * Report a user event of navigating backward out of the entity detail screen
     *
     * @param isSwipe - Toggles user's method of navigation to swipe or arrow press
     */
    public static void reportEntityDetailExit(boolean isSwipe, boolean isSingleTab) {
        reportEntityDetailNavigation(
                GoogleAnalyticsFields.ACTION_EXIT_FROM_DETAIL, isSwipe, isSingleTab);
    }

    /**
     * Report a user event of continuing forward out of the entity detail screen
     *
     * @param isSwipe - Toggles user's method of navigation to swipe or arrow press
     */
    public static void reportEntityDetailContinue(boolean isSwipe, boolean isSingleTab) {
        reportEntityDetailNavigation(
                GoogleAnalyticsFields.ACTION_CONTINUE_FROM_DETAIL, isSwipe, isSingleTab);
    }

    private static void reportEntityDetailNavigation(String action, boolean isSwipe, boolean isSingleTab) {
        reportEvent(
                GoogleAnalyticsFields.CATEGORY_MODULE_NAVIGATION,
                action,
                isSwipe ? GoogleAnalyticsFields.LABEL_SWIPE : GoogleAnalyticsFields.LABEL_ARROW,
                isSingleTab ? GoogleAnalyticsFields.VALUE_DOESNT_HAVE_TABS : GoogleAnalyticsFields.VALUE_HAS_TABS);
    }

    /**
     * Report usage of a specific feature
     *
     * @param action - Should be one of the actions listed under
     *               "Actions for CATEGORY_FEATURE_USAGE" in GoogleAnalyticsFields.java
     */
    public static void reportFeatureUsage(String action) {
        reportEvent(GoogleAnalyticsFields.CATEGORY_FEATURE_USAGE, action);
    }

    /**
     * Report an action in the app manager
     *
     * @param action - Should be one of the actions listed under
     *               "Actions for CATEGORY_APP_MANAGER" in GoogleAnalyticsFields.java
     */
    public static void reportAppManagerAction(String action) {
        reportEvent(GoogleAnalyticsFields.CATEGORY_APP_MANAGER, action);
    }

    public static void reportPrivilegeEnabled(String privilegeName, String username) {
        reportEvent(GoogleAnalyticsFields.CATEGORY_PRIVILEGE_ENABLED, privilegeName, username);
    }

    /**
     * Report the length of a certain user event/action/concept
     *
     * @param action - Communicates the event/action/concept whose length is being measured
     * @param value  - Communicates the duration, in seconds
     */
    public static void reportTimedEvent(String action, int value) {
        if (analyticsDisabled() || versionIncompatible()) {
            return;
        }
        getTracker().send(new HitBuilders.EventBuilder()
                .setCustomDimension(1, CommCareApplication._().getCurrentUserId())
                .setCustomDimension(2, ReportingUtils.getDomain())
                .setCustomDimension(3, BuildConfig.FLAVOR)
                .setCategory(GoogleAnalyticsFields.CATEGORY_TIMED_EVENTS)
                .setAction(action)
                .setValue(value)
                .build());
    }

    public static void createPreferenceOnClickListeners(PreferenceManager prefManager,
                                                        Map<String, String> menuIdToAnalyticsEvent, String category) {

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

}
