package org.commcare.google.services.analytics;

import android.os.Build;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.commcare.CommCareApplication;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.suite.model.OfflineUserRestore;
import org.commcare.utils.EncryptionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Created by amstone326 on 10/13/17.
 */
public class FirebaseAnalyticsUtil {

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

        // All events get domain name and app id
        b.putString(CCAnalyticsParam.CCHQ_DOMAIN, ReportingUtils.getDomain());
        b.putString(CCAnalyticsParam.CC_APP_ID, ReportingUtils.getAppId());

        CommCareApplication.instance().getAnalyticsInstance().logEvent(eventName, b);
    }

    public static void reportOptionsMenuEntry(Class location) {
        reportEvent(CCAnalyticsEvent.OPEN_OPTIONS_MENU,
                FirebaseAnalytics.Param.LOCATION, location.getSimpleName());
    }

    /**
     * Report a user event of selecting an item within an options menu
     */
    public static void reportOptionsMenuItemClick(Class location, String itemLabel) {
        reportEvent(CCAnalyticsEvent.SELECT_OPTIONS_MENU_ITEM,
                new String[]{FirebaseAnalytics.Param.LOCATION, CCAnalyticsParam.OPTIONS_MENU_ITEM},
                new String[]{location.getSimpleName(), itemLabel});
    }

    public static void reportPreferenceActivityEntry(Class location) {
        reportEvent(CCAnalyticsEvent.ENTER_PREF_MENU,
                FirebaseAnalytics.Param.LOCATION, location.getSimpleName());
    }

    /**
     * Report a user event of changing the value of a SharedPreference
     */
    public static void reportEditPreferenceItem(String preferenceKey, String value) {
        reportEvent(CCAnalyticsEvent.EDIT_PREFERENCE_ITEM,
                new String[]{FirebaseAnalytics.Param.ITEM_NAME, FirebaseAnalytics.Param.VALUE},
                new String[]{scrubDashes(preferenceKey), value});
    }

    private static String scrubDashes(String s) {
        return s.replace("-", "_");
    }

    public static void reportAdvancedActionSelected(String action) {
        reportEvent(CCAnalyticsEvent.ADVANCED_ACTION_SELECTED, CCAnalyticsParam.ACTION_TYPE, action);
    }

    public static void reportHomeButtonClick(String buttonName) {
        reportEvent(CCAnalyticsEvent.HOME_BUTTON_CLICK,
                FirebaseAnalytics.Param.ITEM_NAME, buttonName);
    }

    public static void reportViewArchivedFormsList(String formType) {
        reportEvent(CCAnalyticsEvent.VIEW_ARCHIVED_FORMS_LIST,
                CCAnalyticsParam.FORM_TYPE, formType);
    }

    public static void reportOpenArchivedForm(String formType) {
        reportEvent(CCAnalyticsEvent.OPEN_ARCHIVED_FORM,
                CCAnalyticsParam.FORM_TYPE, formType);
    }

    public static void reportAppInstall(String installMethod) {
        reportEvent(CCAnalyticsEvent.APP_INSTALL,
                CCAnalyticsParam.METHOD, installMethod);
    }

    public static void reportAppStartup() {
        reportEvent(CCAnalyticsEvent.APP_STARTUP,
                CCAnalyticsParam.API_LEVEL, "" + Build.VERSION.SDK_INT);
    }

    public static void reportAppManagerAction(String action) {
        reportEvent(CCAnalyticsEvent.APP_MANAGER_ACTION,
                CCAnalyticsParam.ACTION_TYPE, action);
    }

    public static void reportAudioFileSelected() {
        reportAudioWidgetInteraction(CCAnalyticsParamValue.CHOOSE_AUDIO_FILE);
    }

    public static void reportAudioPlayed() {
        reportAudioWidgetInteraction(CCAnalyticsParamValue.PLAY_AUDIO);
    }

    public static void reportAudioPaused() {
        reportAudioWidgetInteraction(CCAnalyticsParamValue.PAUSE_AUDIO);
    }

    public static void reportAudioFileSaved() {
        reportAudioWidgetInteraction(CCAnalyticsParamValue.SAVE_RECORDING);
    }

    public static void reportRecordingStarted() {
        reportAudioWidgetInteraction(CCAnalyticsParamValue.START_RECORDING);
    }

    public static void reportRecordingStopped() {
        reportAudioWidgetInteraction(CCAnalyticsParamValue.STOP_RECORDING);
    }

    public static void reportRecordingRecycled() {
        reportAudioWidgetInteraction(CCAnalyticsParamValue.RECORD_AGAIN);
    }

    private static void reportAudioWidgetInteraction(String interactionType) {
        reportEvent(CCAnalyticsEvent.AUDIO_WIDGET_INTERACTION,
                CCAnalyticsParam.ACTION_TYPE, interactionType);
    }

    public static void reportGraphViewAttached() {
        reportGraphingAction(CCAnalyticsParamValue.GRAPH_ATTACH);
    }

    public static void reportGraphViewDetached() {
        reportGraphingAction(CCAnalyticsParamValue.GRAPH_DETACH);
    }

    public static void reportGraphViewFullScreenOpened() {
        reportGraphingAction(CCAnalyticsParamValue.GRAPH_FULLSCREEN_OPEN);
    }

    public static void reportGraphViewFullScreenClosed() {
        reportGraphingAction(CCAnalyticsParamValue.GRAPH_FULLSCREEN_CLOSE);
    }

    private static void reportGraphingAction(String actionType) {
        reportEvent(CCAnalyticsEvent.GRAPH_ACTION,
                CCAnalyticsParam.ACTION_TYPE, actionType);
    }

    public static void reportFormNav(String direction, String method) {
        reportEvent(CCAnalyticsEvent.FORM_NAVIGATION,
                new String[]{ CCAnalyticsParam.DIRECTION, CCAnalyticsParam.METHOD},
                new String[]{direction, method});
    }

    public static void reportFormQuitAttempt(String method) {
        reportEvent(CCAnalyticsEvent.FORM_EXIT_ATTEMPT, CCAnalyticsParam.METHOD, method);
    }

    public static void reportFormEntry(String currentLocale) {
        reportEvent(CCAnalyticsEvent.ENTER_FORM, CCAnalyticsParam.LOCALE, currentLocale);
    }

    /**
     * Report a user event of navigating backward out of the entity detail screen
     */
    public static void reportEntityDetailExit(String navMethod, int detailTabCount) {
        reportEntityDetailNavigation(
                CCAnalyticsParamValue.DIRECTION_BACKWARD, navMethod, detailTabCount);
    }

    /**
     * Report a user event of continuing forward past the entity detail screen
     */
    public static void reportEntityDetailContinue(String navMethod, int detailTabCount) {
        reportEntityDetailNavigation(
                CCAnalyticsParamValue.DIRECTION_FORWARD, navMethod, detailTabCount);
    }

    private static void reportEntityDetailNavigation(String direction, String navMethod,
                                                     int detailTabCount) {
        String detailUiState =
                detailTabCount > 1 ? CCAnalyticsParamValue.DETAIL_WITH_TABS :
                        CCAnalyticsParamValue.DETAIL_NO_TABS;

        reportEvent(CCAnalyticsEvent.ENTITY_DETAIL_NAVIGATION,
                new String[]{
                        CCAnalyticsParam.DIRECTION,
                        CCAnalyticsParam.METHOD,
                        CCAnalyticsParam.UI_STATE},
                new String[]{direction, navMethod, detailUiState});
    }

    public static void reportSyncSuccess(String trigger, String syncMode) {
        reportEvent(CCAnalyticsEvent.SYNC_ATTEMPT,
                new String[]{ CCAnalyticsParam.TRIGGER, CCAnalyticsParam.OUTCOME,
                        CCAnalyticsParam.MODE},
                new String[]{trigger, CCAnalyticsParamValue.SYNC_SUCCESS, syncMode});
    }

    public static void reportSyncFailure(String trigger, String failureReason) {
        reportEvent(CCAnalyticsEvent.SYNC_ATTEMPT,
                new String[]{ CCAnalyticsParam.TRIGGER, CCAnalyticsParam.OUTCOME,
                        CCAnalyticsParam.REASON},
                new String[]{trigger, CCAnalyticsParamValue.SYNC_FAILURE, failureReason});
    }

    public static void reportFeatureUsage(String feature) {
        reportEvent(CCAnalyticsEvent.FEATURE_USAGE,
                FirebaseAnalytics.Param.ITEM_CATEGORY, feature);
    }

    public static void reportFeatureUsage(String feature, String mode) {
        reportEvent(CCAnalyticsEvent.FEATURE_USAGE,
                new String[]{FirebaseAnalytics.Param.ITEM_CATEGORY, CCAnalyticsParam.MODE},
                new String[]{feature, mode});
    }

    public static void reportPracticeModeUsage(OfflineUserRestore currentOfflineUserRestoreResource) {
        reportFeatureUsage(
                CCAnalyticsParamValue.FEATURE_practiceMode,
                currentOfflineUserRestoreResource == null ?
                        CCAnalyticsParamValue.PRACTICE_MODE_DEFAULT :
                        CCAnalyticsParamValue.PRACTICE_MODE_CUSTOM);
    }

    public static void reportPrivilegeEnabled(String privilegeName, String usernameUsedToActivate) {
        reportEvent(CCAnalyticsEvent.ENABLE_PRIVILEGE,
                new String[]{FirebaseAnalytics.Param.ITEM_NAME, CCAnalyticsParam.USERNAME},
                new String[]{privilegeName, EncryptionUtils.getMD5HashAsString(usernameUsedToActivate)});
    }

    public static void reportTimedSession(String sessionType, double timeInSeconds, double timeInMinutes) {
        reportEvent(CCAnalyticsEvent.TIMED_SESSION,
                new String[]{ CCAnalyticsParam.SESSION_TYPE,
                        CCAnalyticsParam.TIME_IN_SECONDS,
                        CCAnalyticsParam.TIME_IN_MINUTES},
                new String[]{sessionType,
                        ""+roundToOneDecimal(timeInSeconds),
                        ""+roundToOneDecimal(timeInMinutes)});
    }

    private static double roundToOneDecimal(double d) {
        return new BigDecimal(d).setScale(1, RoundingMode.HALF_DOWN).doubleValue();
    }

    private static boolean analyticsDisabled() {
        return !CommCarePreferences.isAnalyticsEnabled();
    }

    public static boolean versionIncompatible() {
        // According to https://firebase.google.com/docs/android/setup,
        // Firebase should only be used on devices running Android 4.0 and above
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }
}
