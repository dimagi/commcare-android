package org.commcare.google.services.analytics;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.commcare.CommCareApplication;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.preferences.MainConfigurablePreferences;
import org.commcare.suite.model.OfflineUserRestore;
import org.commcare.utils.EncryptionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

import static org.commcare.google.services.analytics.AnalyticsParamValue.CORRUPT_APP_STATE;
import static org.commcare.google.services.analytics.AnalyticsParamValue.STAGE_UPDATE_FAILURE;
import static org.commcare.google.services.analytics.AnalyticsParamValue.UPDATE_RESET;
import static org.commcare.google.services.analytics.AnalyticsParamValue.VIDEO_USAGE_FULL;
import static org.commcare.google.services.analytics.AnalyticsParamValue.VIDEO_USAGE_IMMEDIATE;
import static org.commcare.google.services.analytics.AnalyticsParamValue.VIDEO_USAGE_LENGTH_UNKNOWN;
import static org.commcare.google.services.analytics.AnalyticsParamValue.VIDEO_USAGE_MOST;
import static org.commcare.google.services.analytics.AnalyticsParamValue.VIDEO_USAGE_OTHER;
import static org.commcare.google.services.analytics.AnalyticsParamValue.VIDEO_USAGE_PARTIAL;

/**
 * Created by amstone326 on 10/13/17.
 */
public class FirebaseAnalyticsUtil {

    // constant to approximate time taken by an user to go to the video playing app after clicking on the video
    private static final long VIDEO_USAGE_ERROR_APPROXIMATION = 3;

    private static void reportEvent(String eventName, String paramKey, String paramVal) {
        reportEvent(eventName, new String[]{paramKey}, new String[]{paramVal});
    }

    private static void reportEvent(String eventName, String[] paramKeys, String[] paramVals) {
        Bundle b = new Bundle();
        for (int i = 0; i < paramKeys.length; i++) {
            // https://firebase.google.com/docs/reference/android/com/google/firebase/analytics/FirebaseAnalytics.Param
            // Param values can only be up to 100 characters.
            if (paramVals[i].length() > 100) {
                paramVals[i] = paramVals[i].substring(0, 100);
            }
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
                FirebaseAnalytics.Param.ITEM_NAME,
                location.getSimpleName() + "_" + itemLabel);
    }

    /**
     * Report a user event of changing the value of a SharedPreference
     */
    public static void reportEditPreferenceItem(String preferenceKey, String value) {
        reportEvent(CCAnalyticsEvent.EDIT_PREFERENCE_ITEM,
                new String[]{FirebaseAnalytics.Param.ITEM_NAME, FirebaseAnalytics.Param.ITEM_VARIANT},
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
                FirebaseAnalytics.Param.ITEM_LIST, formType);
    }

    public static void reportOpenArchivedForm(String formType) {
        reportEvent(CCAnalyticsEvent.OPEN_ARCHIVED_FORM,
                FirebaseAnalytics.Param.ITEM_NAME, formType);
    }

    public static void reportAppInstall(String installMethod) {
        reportEvent(CCAnalyticsEvent.APP_INSTALL,
                FirebaseAnalytics.Param.METHOD, installMethod);
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
                    new String[]{CCAnalyticsParam.DIRECTION, FirebaseAnalytics.Param.METHOD},
                    new String[]{direction, method});
        }
    }

    public static void reportFormQuitAttempt(String method) {
        reportEvent(CCAnalyticsEvent.FORM_EXIT_ATTEMPT, FirebaseAnalytics.Param.METHOD, method);
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
                        FirebaseAnalytics.Param.METHOD,
                        CCAnalyticsParam.UI_STATE},
                new String[]{direction, navMethod, detailUiState});
    }

    public static void reportSyncSuccess(String trigger, String syncMode) {
        Bundle b = new Bundle();
        b.putString(FirebaseAnalytics.Param.SOURCE, trigger);
        b.putLong(FirebaseAnalytics.Param.SUCCESS, 1);
        b.putString(FirebaseAnalytics.Param.METHOD, syncMode);
        reportEvent(CCAnalyticsEvent.SYNC_ATTEMPT, b);
    }

    public static void reportSyncFailure(String trigger, String syncMode, String failureReason) {
        Bundle b = new Bundle();
        b.putString(FirebaseAnalytics.Param.SOURCE, trigger);
        b.putLong(FirebaseAnalytics.Param.SUCCESS, 0);
        b.putString(FirebaseAnalytics.Param.METHOD, syncMode);
        b.putString(CCAnalyticsParam.REASON, failureReason);
        reportEvent(CCAnalyticsEvent.SYNC_ATTEMPT, b);
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
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, sessionType);
            bundle.putDouble(FirebaseAnalytics.Param.VALUE, timeInSeconds);
            bundle.putDouble(CCAnalyticsParam.TIME_IN_MINUTES, timeInMinutes);
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

    private static String getVideoUsage(long totalDuration, long playDuration) {
        if (totalDuration == -1) {
            return VIDEO_USAGE_LENGTH_UNKNOWN;
        } else {
            double timeSpendInFraction = playDuration / totalDuration;

            if (timeSpendInFraction <= 0.25) {
                return VIDEO_USAGE_IMMEDIATE;
            } else if (timeSpendInFraction <= 0.5) {
                return VIDEO_USAGE_PARTIAL;
            } else if (timeSpendInFraction <= 0.75) {
                return VIDEO_USAGE_MOST;
            } else if (timeSpendInFraction <= 2) {
                return VIDEO_USAGE_FULL;
            } else {
                return VIDEO_USAGE_OTHER;
            }
        }
    }

    public static void reportVideoPlayEvent(String videoName, long videoDuration, long videoStartTime) {
        long timeSpend = new Date().getTime() - videoStartTime;
        timeSpend = timeSpend + VIDEO_USAGE_ERROR_APPROXIMATION;
        String videoUsage = getVideoUsage(videoDuration, timeSpend);

        reportEvent(CCAnalyticsEvent.VIEW_QUESTION_MEDIA,
                new String[]{FirebaseAnalytics.Param.ITEM_ID, CCAnalyticsParam.USER_RETURNED},
                new String[]{videoName, videoUsage});
    }

    public static void reportInlineVideoPlayEvent(String videoName, long totalDuration, long playDuration) {
        String videoUsage = getVideoUsage(totalDuration, playDuration);
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