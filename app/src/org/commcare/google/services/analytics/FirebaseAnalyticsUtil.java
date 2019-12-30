package org.commcare.google.services.analytics;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.commcare.CommCareApplication;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.MainConfigurablePreferences;
import org.commcare.suite.model.OfflineUserRestore;
import org.commcare.utils.EncryptionUtils;
import org.commcare.utils.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

/**
 * Created by amstone326 on 10/13/17.
 */
public class FirebaseAnalyticsUtil {



    // constants related to video usage event
    private static final String VIDEO_USAGE_IMMEDIATE = "immediate";
    private static final String VIDEO_USAGE_PARTIAL = "partial";
    private static final String VIDEO_USAGE_MOST = "most";
    private static final String VIDEO_USAGE_FULL = "full";
    private static final String VIDEO_USAGE_OTHER = "other";


    // constants related to common commcare event
    private static final String STAGE_UPDATE_FAILURE = "stage_update_failure";
    private static final String UPDATE_RESET = "update_reset";
    private static final String CORRUPT_APP_STATE = "corrupt_app_state";

    public static final String UPDATE_RESET_REASON_CORRUPT = "update_corrupt";
    public static final String UPDATE_RESET_REASON_TIMEOUT = "update_timeout";
    public static final String UPDATE_RESET_REASON_OVERSHOOT_TRIALS = "update_overshoot_trials";
    public static final String UPDATE_RESET_REASON_NEWER_VERSION_AVAILABLE = "update_newer_version_available";

    private static void reportEvent(String eventName, String paramKey, String paramVal) {
        reportEvent(eventName, new String[]{paramKey}, new String[]{paramVal});
    }

    private static void reportEvent(String eventName, String[] paramKeys, String[] paramVals) {
        Bundle b = new Bundle();
        for (int i = 0; i < paramKeys.length; i++) {
            b.putString(paramKeys[i], paramVals[i]);
        }
        reportEvent(eventName, b);
    }

    private static void reportEvent(String eventName, Bundle params) {
        if (analyticsDisabled() || versionIncompatible()) {
            return;
        }

        FirebaseAnalytics analyticsInstance = CommCareApplication.instance().getAnalyticsInstance();
        setUserProperties(analyticsInstance);
        analyticsInstance.logEvent(eventName, params);
    }

    private static void setUserProperties(FirebaseAnalytics analyticsInstance) {
        String domain = ReportingUtils.getDomain();
        if (!TextUtils.isEmpty(domain)) {
            analyticsInstance.setUserProperty(CCAnalyticsParam.CCHQ_DOMAIN, domain);
        }

        String appId = ReportingUtils.getAppId();
        if (!TextUtils.isEmpty(appId)) {
            analyticsInstance.setUserProperty(CCAnalyticsParam.CC_APP_ID, appId);
        }

        String serverName = ReportingUtils.getServerName();
        if (!TextUtils.isEmpty(serverName)) {
            analyticsInstance.setUserProperty(CCAnalyticsParam.SERVER, serverName);
        }
    }

    /**
     * Report a user event of selecting an item within an options menu
     */
    public static void reportOptionsMenuItemClick(Class location, String itemLabel) {
        reportEvent(CCAnalyticsEvent.SELECT_OPTIONS_MENU_ITEM,
                new String[]{FirebaseAnalytics.Param.LOCATION, CCAnalyticsParam.OPTIONS_MENU_ITEM},
                new String[]{location.getSimpleName(), itemLabel});
    }

    /**
     * Report a user event of changing the value of a SharedPreference
     */
    public static void reportEditPreferenceItem(String preferenceKey, String value) {
        reportEvent(CCAnalyticsEvent.EDIT_PREFERENCE_ITEM,
                new String[]{FirebaseAnalytics.Param.ITEM_NAME, FirebaseAnalytics.Param.VALUE},
                new String[]{preferenceKey, value});
    }

    public static void reportAdvancedActionSelected(String action) {
        reportEvent(CCAnalyticsEvent.ADVANCED_ACTION_SELECTED, CCAnalyticsParam.ACTION_TYPE, action);
    }

    public static void reportHomeButtonClick(String buttonName) {
        reportEvent(CCAnalyticsEvent.HOME_BUTTON_CLICK,
                FirebaseAnalytics.Param.ITEM_NAME, buttonName);
    }

    public static void reportViewArchivedFormsList(boolean forIncomplete) {
        String formType = forIncomplete ? AnalyticsParamValue.INCOMPLETE : AnalyticsParamValue.SAVED;
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

    public static void reportAppManagerAction(String action) {
        reportEvent(CCAnalyticsEvent.APP_MANAGER_ACTION,
                CCAnalyticsParam.ACTION_TYPE, action);
    }

    public static void reportGraphViewAttached() {
        reportGraphingAction(AnalyticsParamValue.GRAPH_ATTACH);
    }

    public static void reportGraphViewDetached() {
        reportGraphingAction(AnalyticsParamValue.GRAPH_DETACH);
    }

    public static void reportGraphViewFullScreenOpened() {
        reportGraphingAction(AnalyticsParamValue.GRAPH_FULLSCREEN_OPEN);
    }

    public static void reportGraphViewFullScreenClosed() {
        reportGraphingAction(AnalyticsParamValue.GRAPH_FULLSCREEN_CLOSE);
    }

    private static void reportGraphingAction(String actionType) {
        reportEvent(CCAnalyticsEvent.GRAPH_ACTION,
                CCAnalyticsParam.ACTION_TYPE, actionType);
    }

    public static void reportFormNav(String direction, String method) {
        if (rateLimitReporting(.1)) {
            reportEvent(CCAnalyticsEvent.FORM_NAVIGATION,
                    new String[]{CCAnalyticsParam.DIRECTION, CCAnalyticsParam.METHOD},
                    new String[]{direction, method});
        }
    }

    public static void reportFormQuitAttempt(String method) {
        reportEvent(CCAnalyticsEvent.FORM_EXIT_ATTEMPT, CCAnalyticsParam.METHOD, method);
    }

    /**
     * Report a user event of navigating backward out of the entity detail screen
     */
    public static void reportEntityDetailExit(String navMethod, int detailTabCount) {
        reportEntityDetailNavigation(
                AnalyticsParamValue.DIRECTION_BACKWARD, navMethod, detailTabCount);
    }

    /**
     * Report a user event of continuing forward past the entity detail screen
     */
    public static void reportEntityDetailContinue(String navMethod, int detailTabCount) {
        reportEntityDetailNavigation(
                AnalyticsParamValue.DIRECTION_FORWARD, navMethod, detailTabCount);
    }

    private static void reportEntityDetailNavigation(String direction, String navMethod,
                                                     int detailTabCount) {
        String detailUiState =
                detailTabCount > 1 ? AnalyticsParamValue.DETAIL_WITH_TABS :
                        AnalyticsParamValue.DETAIL_NO_TABS;

        reportEvent(CCAnalyticsEvent.ENTITY_DETAIL_NAVIGATION,
                new String[]{
                        CCAnalyticsParam.DIRECTION,
                        CCAnalyticsParam.METHOD,
                        CCAnalyticsParam.UI_STATE},
                new String[]{direction, navMethod, detailUiState});
    }

    public static void reportSyncSuccess(String trigger, String syncMode) {
        reportEvent(CCAnalyticsEvent.SYNC_ATTEMPT,
                new String[]{CCAnalyticsParam.TRIGGER, CCAnalyticsParam.OUTCOME,
                        CCAnalyticsParam.MODE},
                new String[]{trigger, AnalyticsParamValue.SYNC_SUCCESS, syncMode});
    }

    public static void reportSyncFailure(String trigger, String failureReason) {
        reportEvent(CCAnalyticsEvent.SYNC_ATTEMPT,
                new String[]{CCAnalyticsParam.TRIGGER, CCAnalyticsParam.OUTCOME,
                        CCAnalyticsParam.REASON},
                new String[]{trigger, AnalyticsParamValue.SYNC_FAILURE, failureReason});
    }

    public static void reportFeatureUsage(String feature) {
        reportEvent(CCAnalyticsEvent.FEATURE_USAGE,
                FirebaseAnalytics.Param.ITEM_CATEGORY, feature);
    }

    private static void reportFeatureUsage(String feature, String mode) {
        reportEvent(CCAnalyticsEvent.FEATURE_USAGE,
                new String[]{FirebaseAnalytics.Param.ITEM_CATEGORY, CCAnalyticsParam.MODE},
                new String[]{feature, mode});
    }

    public static void reportPracticeModeUsage(OfflineUserRestore currentOfflineUserRestoreResource) {
        reportFeatureUsage(
                AnalyticsParamValue.FEATURE_PRACTICE_MODE,
                currentOfflineUserRestoreResource == null ?
                        AnalyticsParamValue.PRACTICE_MODE_DEFAULT :
                        AnalyticsParamValue.PRACTICE_MODE_CUSTOM);
    }

    public static void reportPrivilegeEnabled(String privilegeName, String usernameUsedToActivate) {
        reportEvent(CCAnalyticsEvent.ENABLE_PRIVILEGE,
                new String[]{FirebaseAnalytics.Param.ITEM_NAME, CCAnalyticsParam.USERNAME},
                new String[]{privilegeName, EncryptionUtils.getMD5HashAsString(usernameUsedToActivate)});
    }

    public static void reportTimedSession(String sessionType, double timeInSeconds, double timeInMinutes) {
        if (rateLimitReporting(.25)) {
            reportEvent(CCAnalyticsEvent.TIMED_SESSION,
                    new String[]{CCAnalyticsParam.SESSION_TYPE,
                            CCAnalyticsParam.TIME_IN_SECONDS,
                            CCAnalyticsParam.TIME_IN_MINUTES},
                    new String[]{sessionType,
                            "" + roundToOneDecimal(timeInSeconds),
                            "" + roundToOneDecimal(timeInMinutes)});
        }
    }

    private static double roundToOneDecimal(double d) {
        return new BigDecimal(d).setScale(1, RoundingMode.HALF_DOWN).doubleValue();
    }

    private static boolean analyticsDisabled() {
        return !MainConfigurablePreferences.isAnalyticsEnabled();
    }

    private static boolean versionIncompatible() {
        // According to https://firebase.google.com/docs/android/setup,
        // Firebase should only be used on devices running Android 4.0 and above
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    private static boolean rateLimitReporting(double percentOfEventsToReport) {
        return Math.random() < percentOfEventsToReport;
    }

    public static void reportVideoPlayEvent(String videoName, long videoDuration, long videoStartTime) {
        long timeSpend = new Date().getTime() - videoStartTime;
        double timeSpendInFraction = timeSpend / videoDuration;

        String videoUsage;
        if (timeSpendInFraction < 0.25) {
            videoUsage = VIDEO_USAGE_IMMEDIATE;
        } else if (timeSpendInFraction < 0.5) {
            videoUsage = VIDEO_USAGE_PARTIAL;
        } else if (timeSpendInFraction < 0.75) {
            videoUsage = VIDEO_USAGE_MOST;
        } else if (timeSpendInFraction < 2) {
            videoUsage = VIDEO_USAGE_FULL;
        } else {
            videoUsage = VIDEO_USAGE_OTHER;
        }

        reportEvent(CCAnalyticsEvent.VIEW_QUESTION_MEDIA,
                new String[]{FirebaseAnalytics.Param.ITEM_ID, CCAnalyticsParam.USER_RETURNED},
                new String[]{videoName, videoUsage});
    }

    public static void reportStageUpdateAttemptFailure(String reason) {
        reportEvent(CCAnalyticsEvent.COMMON_COMMCARE_EVENT,
                new String[]{FirebaseAnalytics.Param.ITEM_ID, CCAnalyticsParam.REASON},
                new String[]{STAGE_UPDATE_FAILURE, reason});
    }

    public static void reportUpdateReset(String reason) {
        reportEvent(CCAnalyticsEvent.COMMON_COMMCARE_EVENT,
                new String[]{FirebaseAnalytics.Param.ITEM_ID, CCAnalyticsParam.REASON},
                new String[]{UPDATE_RESET, reason});
    }

    public static void reportCorruptAppState() {
        reportEvent(CCAnalyticsEvent.COMMON_COMMCARE_EVENT,
                new String[]{FirebaseAnalytics.Param.ITEM_ID},
                new String[]{CORRUPT_APP_STATE});
    }
}
