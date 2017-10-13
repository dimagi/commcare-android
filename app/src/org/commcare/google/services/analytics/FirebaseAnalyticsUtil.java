package org.commcare.google.services.analytics;

import android.os.Build;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.commcare.CommCareApplication;
import org.commcare.preferences.CommCarePreferences;

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
        getAnalyticsInstance().logEvent(eventName, b);
    }

    public static void reportOptionsMenuEntry(Class location) {
        reportEvent(FirebaseAnalyticsEvent.OPEN_OPTIONS_MENU,
                FirebaseAnalytics.Param.LOCATION, location.getSimpleName());
    }

    /**
     * Report a user event of selecting an item within an options menu
     */
    public static void reportOptionsMenuItemClick(Class location, String itemLabel) {
        reportEvent(FirebaseAnalyticsEvent.CLICK_OPTIONS_MENU_ITEM,
                new String[]{FirebaseAnalytics.Param.LOCATION, FirebaseAnalyticsParam.OPTIONS_MENU_ITEM },
                new String[]{location.getSimpleName(), itemLabel});
    }

    public static void reportAppStartup() {
        reportEvent(FirebaseAnalyticsEvent.APP_STARTUP,
                FirebaseAnalyticsParam.API_LEVEL, "" + Build.VERSION.SDK_INT);
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

    private static FirebaseAnalytics getAnalyticsInstance() {
        return CommCareApplication.instance().getAnalyticsInstance();
    }

    private static boolean analyticsDisabled() {
        return !CommCarePreferences.isAnalyticsEnabled();
    }

    public static boolean versionIncompatible() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD;
    }
}
