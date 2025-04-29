package org.commcare.google.services.analytics;

import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.commcare.CommCareApplication;
import org.commcare.DiskUtils;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.preferences.MainConfigurablePreferences;
import org.commcare.suite.model.OfflineUserRestore;
import org.commcare.utils.EncryptionUtils;
import org.commcare.utils.FormUploadResult;
import org.javarosa.core.services.Logger;

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
        try {
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
        } catch(Exception e) {
            Logger.exception("Error logging analytics event", e);
        }
    }

    private static void reportEvent(String eventName, Bundle params) {
        if (analyticsDisabled()) {
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

        String buildProfileID = ReportingUtils.getAppBuildProfileId();
        if (!TextUtils.isEmpty(appId)) {
            analyticsInstance.setUserProperty(CCAnalyticsParam.CC_APP_BUILD_PROFILE_ID, buildProfileID);
        }

        String serverName = ReportingUtils.getServerName();
        if (!TextUtils.isEmpty(serverName)) {
            analyticsInstance.setUserProperty(CCAnalyticsParam.SERVER, serverName);
        }

        String freeDiskBucket = getFreeDiskBucket();
        if (!TextUtils.isEmpty(freeDiskBucket)) {
            analyticsInstance.setUserProperty(CCAnalyticsParam.FREE_DISK, freeDiskBucket);
        }
    }

    private static String getFreeDiskBucket() {
        long freeDiskInMB = DiskUtils.calculateFreeDiskSpaceInBytes(Environment.getDataDirectory().getPath()) / 1000000;
        if (freeDiskInMB > 1000) {
            return "gt_1000";
        } else if (freeDiskInMB > 500) {
            return "lt_1000";
        } else if (freeDiskInMB > 300) {
            return "lt_500";
        } else if (freeDiskInMB > 100) {
            return "lt_300";
        } else {
            return "lt_100";
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

    public static void reportFormEntry(String formId) {
        reportEvent(CCAnalyticsEvent.FORM_ENTRY_ATTEMPT,
                new String[]{CCAnalyticsParam.FORM_ID},
                new String[]{formId});
    }

    public static void reportFormNav(String direction, String method, String formId) {
        if (rateLimitReporting(.1)) {
            reportEvent(CCAnalyticsEvent.FORM_NAVIGATION,
                    new String[]{CCAnalyticsParam.DIRECTION, FirebaseAnalytics.Param.METHOD, CCAnalyticsParam.FORM_ID},
                    new String[]{direction, method, formId});
        }
    }

    public static void reportFormQuitAttempt(String method, String formId) {
        reportEvent(CCAnalyticsEvent.FORM_EXIT_ATTEMPT,
                new String[]{FirebaseAnalytics.Param.METHOD, CCAnalyticsParam.FORM_ID},
                new String[]{method, formId});
    }

    public static void reportFormFinishAttempt(String saveResult, String formId, boolean userTriggered) {
        String method = userTriggered ? AnalyticsParamValue.USER_TRIGGERED : AnalyticsParamValue.SYSTEM_TRIGGERED;
        reportEvent(CCAnalyticsEvent.FORM_FINISH_ATTEMPT,
                new String[]{CCAnalyticsParam.FORM_ID, CCAnalyticsParam.RESULT,
                        FirebaseAnalytics.Param.METHOD},
                new String[]{formId, saveResult, method});
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

    public static void reportSyncResult(boolean result, String trigger, String syncMode, String failureReason) {
        Bundle b = new Bundle();
        b.putString(FirebaseAnalytics.Param.SOURCE, trigger);
        b.putLong(FirebaseAnalytics.Param.SUCCESS, result ? 1 : 0);
        b.putString(FirebaseAnalytics.Param.METHOD, syncMode);
        b.putString(CCAnalyticsParam.REASON, failureReason);
        reportEvent(CCAnalyticsEvent.SYNC_ATTEMPT, b);
    }

    public static void reportInAppUpdateResult(boolean result, String failureReason) {
        Bundle bundle = new Bundle();
        bundle.putLong(FirebaseAnalytics.Param.VALUE, result ? 1 : 0);
        bundle.putString(CCAnalyticsParam.REASON, failureReason);
        reportEvent(CCAnalyticsEvent.IN_APP_UPDATE_EVENT, bundle);
    }

    public static void reportFeatureUsage(String feature) {
        if (analyticsDisabled()) {
            return;
        }
        reportEvent(CCAnalyticsEvent.FEATURE_USAGE,
                FirebaseAnalytics.Param.ITEM_CATEGORY, feature);
    }

    private static void reportFeatureUsage(String feature, String mode) {
        if (analyticsDisabled()) {
            return;
        }

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
                new String[]{privilegeName, EncryptionUtils.getMd5HashAsString(usernameUsedToActivate)});
    }

    public static void reportTimedSession(String sessionType, double timeInSeconds, double timeInMinutes) {
        if (rateLimitReporting(.25)) {
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, sessionType);
            bundle.putDouble(FirebaseAnalytics.Param.VALUE, timeInSeconds);
            bundle.putDouble(CCAnalyticsParam.TIME_IN_MINUTES, timeInMinutes);
        }
    }

    private static boolean analyticsDisabled() {
        return !MainConfigurablePreferences.isAnalyticsEnabled();
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

    public static void reportFormQuarantined(String quarantineReasonType) {
        reportEvent(CCAnalyticsEvent.FORM_QUARANTINE_EVENT,
                new String[]{FirebaseAnalytics.Param.ITEM_ID},
                new String[]{quarantineReasonType});
    }

    public static void reportMenuItemClick(String commandId) {
        reportEvent(CCAnalyticsEvent.MENU_SCREEN_ITEM_CLICK,
                new String[]{FirebaseAnalytics.Param.ITEM_ID},
                new String[]{commandId});
    }

    public static void reportFormUploadAttempt(FormUploadResult first, Integer second) {
        reportEvent(CCAnalyticsEvent.FORM_UPLOAD_ATTEMPT,
                new String[]{CCAnalyticsParam.RESULT, FirebaseAnalytics.Param.VALUE},
                new String[]{String.valueOf(first), String.valueOf(second)});
    }
}
