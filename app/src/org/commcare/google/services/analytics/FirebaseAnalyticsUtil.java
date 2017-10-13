package org.commcare.google.services.analytics;

import android.os.Build;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.commcare.CommCareApplication;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.suite.model.OfflineUserRestore;
import org.commcare.utils.EncryptionUtils;

/**
 * Created by amstone326 on 10/13/17.
 */

public class FirebaseAnalyticsUtil {

    public static void reportEvent(String eventName) {
        reportEvent(eventName, new String[0], new String[0]);
    }

    public static void reportEvent(String eventName, String paramKey, String paramVal) {
        reportEvent(eventName, new String[]{paramKey}, new String[]{paramVal});
    }

    public static void reportEvent(String eventName, String[] paramKeys, String[] paramVals) {
        if (analyticsDisabled() || versionIncompatible()) {
            return;
        }

        Bundle b = new Bundle();
        for (int i = 0; i < paramKeys.length; i++) {
            b.putString(paramKeys[i], paramVals[i]);
        }
        FirebaseAnalytics analyticsInstance = CommCareApplication.instance().getAnalyticsInstance();
        analyticsInstance.setUserId(CommCareApplication.instance().getCurrentUserId());
        analyticsInstance.logEvent(eventName, b);
    }

    public static void reportOptionsMenuEntry(Class location) {
        reportEvent(FirebaseAnalyticsEvent.OPEN_OPTIONS_MENU,
                FirebaseAnalytics.Param.LOCATION, location.getSimpleName());
    }

    /**
     * Report a user event of selecting an item within an options menu
     */
    public static void reportOptionsMenuItemClick(Class location, String itemLabel) {
        reportEvent(FirebaseAnalyticsEvent.SELECT_OPTIONS_MENU_ITEM,
                new String[]{FirebaseAnalytics.Param.LOCATION, FirebaseAnalyticsParam.OPTIONS_MENU_ITEM },
                new String[]{location.getSimpleName(), itemLabel});
    }

    /**
     * Report a user event of opening a preferences menu
     */
    public static void reportPrefActivityEntry(Class location) {
        reportEvent(FirebaseAnalyticsEvent.ENTER_PREF_ACTIVITY,
                FirebaseAnalytics.Param.LOCATION, location.getSimpleName());
    }

    public static void reportAppInstall(String installMethod) {
        reportEvent(FirebaseAnalyticsEvent.APP_INSTALL,
                FirebaseAnalyticsParam.METHOD, installMethod);
    }

    public static void reportAppStartup() {
        reportEvent(FirebaseAnalyticsEvent.APP_STARTUP,
                FirebaseAnalyticsParam.API_LEVEL, "" + Build.VERSION.SDK_INT);
    }

    public static void reportAppManagerAction(String action) {
        reportEvent(FirebaseAnalyticsEvent.APP_MANAGER_ACTION,
                FirebaseAnalyticsParam.ACTION_TYPE, action);
    }

    public static void reportAudioFileSelected() {
        reportAudioWidgetInteraction(FirebaseAnalyticsParamValues.CHOOSE_AUDIO_FILE);
    }

    public static void reportAudioPlayed() {
        reportAudioWidgetInteraction(FirebaseAnalyticsParamValues.PLAY_AUDIO);
    }

    public static void reportAudioPaused() {
        reportAudioWidgetInteraction(FirebaseAnalyticsParamValues.PAUSE_AUDIO);
    }

    public static void reportAudioFileSaved() {
        reportAudioWidgetInteraction(FirebaseAnalyticsParamValues.SAVE_RECORDING);
    }

    public static void reportRecordingStarted() {
        reportAudioWidgetInteraction(FirebaseAnalyticsParamValues.START_RECORDING);
    }

    public static void reportRecordingStopped() {
        reportAudioWidgetInteraction(FirebaseAnalyticsParamValues.STOP_RECORDING);
    }

    public static void reportRecordingRecycled() {
        reportAudioWidgetInteraction(FirebaseAnalyticsParamValues.RECORD_AGAIN);
    }

    private static void reportAudioWidgetInteraction(String interactionType) {
        reportEvent(FirebaseAnalyticsEvent.AUDIO_WIDGET_INTERACTION,
                FirebaseAnalyticsParam.ACTION_TYPE, interactionType);
    }

    public static void reportGraphViewAttached() {
        reportGraphingAction(FirebaseAnalyticsParamValues.GRAPH_ATTACH);
    }

    public static void reportGraphViewDetached() {
        reportGraphingAction(FirebaseAnalyticsParamValues.GRAPH_DETACH);
    }

    public static void reportGraphViewFullScreenOpened() {
        reportGraphingAction(FirebaseAnalyticsParamValues.GRAPH_FULLSCREEN_OPEN);
    }

    public static void reportGraphViewFullScreenClosed() {
        reportGraphingAction(FirebaseAnalyticsParamValues.GRAPH_FULLSCREEN_CLOSE);
    }

    private static void reportGraphingAction(String actionType) {
        reportEvent(FirebaseAnalyticsEvent.GRAPH_ACTION,
                FirebaseAnalyticsParam.ACTION_TYPE, actionType);
    }

    public static void reportFormNav(String direction, String method) {
        reportEvent(FirebaseAnalyticsEvent.FORM_NAVIGATION,
                new String[]{FirebaseAnalyticsParam.DIRECTION, FirebaseAnalyticsParam.METHOD},
                new String[]{direction, method});
    }

    public static void reportFormQuitAttempt(String method) {
        reportEvent(FirebaseAnalyticsEvent.FORM_EXIT_ATTEMPT, FirebaseAnalyticsParam.METHOD, method);
    }

    public static void reportFormEntry(String currentLocale) {
        reportEvent(FirebaseAnalyticsEvent.ENTER_FORM, FirebaseAnalyticsParam.LOCALE, currentLocale);
    }

    public static void reportSyncSuccess(String trigger, String syncMode) {
        reportEvent(FirebaseAnalyticsEvent.SYNC_ATTEMPT,
                new String[]{FirebaseAnalyticsParam.TRIGGER, FirebaseAnalyticsParam.OUTCOME,
                        FirebaseAnalyticsParam.MODE},
                new String[]{trigger, FirebaseAnalyticsParamValues.SYNC_SUCCESS, syncMode});
    }

    public static void reportSyncFailure(String trigger, String failureReason) {
        reportEvent(FirebaseAnalyticsEvent.SYNC_ATTEMPT,
                new String[]{FirebaseAnalyticsParam.TRIGGER, FirebaseAnalyticsParam.OUTCOME,
                        FirebaseAnalyticsParam.REASON},
                new String[]{trigger, FirebaseAnalyticsParamValues.SYNC_FAILURE, failureReason});
    }

    public static void reportFeatureUsage(String feature) {
        reportEvent(FirebaseAnalyticsEvent.FEATURE_USAGE,
                FirebaseAnalytics.Param.ITEM_CATEGORY, feature);
    }

    public static void reportFeatureUsage(String feature, String mode) {
        reportEvent(FirebaseAnalyticsEvent.FEATURE_USAGE,
                new String[]{FirebaseAnalytics.Param.ITEM_CATEGORY, FirebaseAnalyticsParam.MODE},
                new String[]{feature, mode});
    }

    public static void reportPracticeModeUsage(OfflineUserRestore currentOfflineUserRestoreResource) {
        reportFeatureUsage(
                FirebaseAnalyticsParamValues.FEATURE_practiceMode,
                currentOfflineUserRestoreResource == null ?
                        FirebaseAnalyticsParamValues.PRACTICE_MODE_DEFAULT :
                        FirebaseAnalyticsParamValues.PRACTICE_MODE_CUSTOM);
    }

    public static void reportPrivilegeEnabled(String privilegeName, String usernameUsedToActivate) {
        reportEvent(FirebaseAnalyticsEvent.ENABLE_PRIVILEGE,
                new String[]{FirebaseAnalytics.Param.ITEM_NAME, FirebaseAnalyticsParam.USERNAME},
                new String[]{privilegeName, EncryptionUtils.getMD5HashAsString(usernameUsedToActivate)});
    }

    private static boolean analyticsDisabled() {
        return !CommCarePreferences.isAnalyticsEnabled();
    }

    public static boolean versionIncompatible() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD;
    }
}
