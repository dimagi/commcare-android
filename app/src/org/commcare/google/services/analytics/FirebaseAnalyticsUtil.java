package org.commcare.google.services.analytics;

import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;

import com.google.firebase.BuildConfig;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.commcare.CommCareApplication;
import org.commcare.DiskUtils;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.connect.ConnectIDManager;
import org.commcare.preferences.MainConfigurablePreferences;
import org.commcare.suite.model.OfflineUserRestore;
import org.commcare.utils.EncryptionUtils;
import org.commcare.utils.FormUploadResult;

import java.util.Date;

import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.FragmentNavigator;

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


    private static void reportEvent(String eventName) {
        reportEvent(eventName, new Bundle());
    }

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

        String buildProfileId = ReportingUtils.getAppBuildProfileId();
        if (!TextUtils.isEmpty(buildProfileId)) {
            analyticsInstance.setUserProperty(CCAnalyticsParam.CC_APP_BUILD_PROFILE_ID, buildProfileId);
        }

        String serverName = ReportingUtils.getServerName();
        if (!TextUtils.isEmpty(serverName)) {
            analyticsInstance.setUserProperty(CCAnalyticsParam.SERVER, serverName);
        }

        String freeDiskBucket = getFreeDiskBucket();
        if (!TextUtils.isEmpty(freeDiskBucket)) {
            analyticsInstance.setUserProperty(CCAnalyticsParam.FREE_DISK, freeDiskBucket);
        }

        analyticsInstance.setUserProperty(CCAnalyticsParam.CCC_ENABLED,
                String.valueOf(ConnectIDManager.getInstance().isloggedIn()));
    }

    private static String getFreeDiskBucket() {
        long freeDiskInMb = DiskUtils.calculateFreeDiskSpaceInBytes(
                Environment.getDataDirectory().getPath()) / 1000000;
        if (freeDiskInMb > 1000) {
            return "gt_1000";
        } else if (freeDiskInMb > 500) {
            return "lt_1000";
        } else if (freeDiskInMb > 300) {
            return "lt_500";
        } else if (freeDiskInMb > 100) {
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

    public static void reportCccSignIn(String method) {
        reportEvent(CCAnalyticsEvent.CCC_SIGN_IN,
                new String[]{CCAnalyticsParam.PARAM_CCC_SIGN_IN_METHOD},
                new String[]{method});
    }

    public static void reportCccRecovery(boolean success, String method) {
        Bundle b = new Bundle();
        b.putLong(CCAnalyticsParam.PARAM_CCC_RECOVERY_SUCCESS, success ? 1 : 0);
        b.putString(CCAnalyticsParam.PARAM_CCC_RECOVERY_METHOD, method);
        reportEvent(CCAnalyticsEvent.CCC_RECOVERY, b);
    }

    public static void reportCccDeconfigure(String reason) {
        Bundle b = new Bundle();
        b.putString(CCAnalyticsParam.REASON, reason);
        reportEvent(CCAnalyticsEvent.CCC_DECONFIGURE, b);
    }

    public static void reportCccAppLaunch(String type, String appId) {
        reportEvent(CCAnalyticsEvent.CCC_LAUNCH_APP,
                new String[]{CCAnalyticsParam.PARAM_CCC_LAUNCH_APP_TYPE,
                        CCAnalyticsParam.PARAM_CCC_APP_NAME},
                new String[]{type, appId});
    }

    public static void reportCccAppAutoLoginWithLocalPassphrase(String app) {
        reportEvent(CCAnalyticsEvent.CCC_AUTO_LOGIN_LOCAL_PASSPHRASE,
                new String[]{CCAnalyticsParam.PARAM_CCC_APP_NAME},
                new String[]{app});
    }

    public static void reportCccAppFailedAutoLogin(String app) {
        reportEvent(CCAnalyticsEvent.CCC_AUTO_LOGIN_FAILED,
                new String[]{CCAnalyticsParam.PARAM_CCC_APP_NAME},
                new String[]{app});
    }

    public static void reportCccApiJobs(boolean success, int totalJobs, int newJobs) {
        Bundle b = new Bundle();
        b.putLong(CCAnalyticsParam.PARAM_API_SUCCESS, success ? 1 : 0);
        b.putInt(CCAnalyticsParam.PARAM_API_NEW_JOBS, newJobs);
        b.putInt(CCAnalyticsParam.PARAM_API_TOTAL_JOBS, totalJobs);
        reportEvent(CCAnalyticsEvent.CCC_API_JOBS, b);
    }

    public static void reportCccApiStartLearning(boolean success) {
        Bundle b = new Bundle();
        b.putLong(CCAnalyticsParam.PARAM_API_SUCCESS, success ? 1 : 0);
        reportEvent(CCAnalyticsEvent.CCC_API_START_LEARNING, b);
    }

    public static void reportCccApiLearnProgress(boolean success) {
        Bundle b = new Bundle();
        b.putLong(CCAnalyticsParam.PARAM_API_SUCCESS, success ? 1 : 0);
        reportEvent(CCAnalyticsEvent.CCC_API_LEARN_PROGRESS, b);
    }

    public static void reportCccApiClaimJob(boolean success) {
        Bundle b = new Bundle();
        b.putLong(CCAnalyticsParam.PARAM_API_SUCCESS, success ? 1 : 0);
        reportEvent(CCAnalyticsEvent.CCC_API_CLAIM_JOB, b);
    }

    public static void reportCccApiDeliveryProgress(boolean success) {
        Bundle b = new Bundle();
        b.putLong(CCAnalyticsParam.PARAM_API_SUCCESS, success ? 1 : 0);
        reportEvent(CCAnalyticsEvent.CCC_API_DELIVERY_PROGRESS, b);
    }

    public static void reportCccApiPaymentConfirmation(boolean success) {
        Bundle b = new Bundle();
        b.putLong(CCAnalyticsParam.PARAM_API_SUCCESS, success ? 1 : 0);
        reportEvent(CCAnalyticsEvent.CCC_API_PAYMENT_CONFIRMATION, b);
    }

    public static void reportCccPaymentConfirmationOnlineCheck(boolean success) {
        Bundle b = new Bundle();
        b.putLong(CCAnalyticsParam.PARAM_API_SUCCESS, success ? 1 : 0);
        reportEvent(CCAnalyticsEvent.CCC_PAYMENT_CONFIRMATION_CHECK, b);
    }

    public static void reportCccPaymentConfirmationDisplayed() {
        Bundle b = new Bundle();
        reportEvent(CCAnalyticsEvent.CCC_PAYMENT_CONFIRMATION_DISPLAY, b);
    }

    public static void reportCccPaymentConfirmationInteraction(boolean positive) {
        Bundle b = new Bundle();
        b.putLong(CCAnalyticsParam.PARAM_API_SUCCESS, positive ? 1 : 0);
        reportEvent(CCAnalyticsEvent.CCC_PAYMENT_CONFIRMATION_INTERACT, b);
    }


    public static void reportCccSignOut() {
        reportEvent(CCAnalyticsEvent.CCC_SIGN_OUT);
    }

    public static void reportLoginClicks() {
        reportEvent(CCAnalyticsEvent.LOGIN_CLICK);
    }

    public static NavController.OnDestinationChangedListener getDestinationChangeListener() {
        return (navController, navDestination, args) -> {
            String currentFragmentClassName = "UnknownDestination";
            NavDestination destination = navController.getCurrentDestination();
            if (destination instanceof FragmentNavigator.Destination) {
                currentFragmentClassName = ((FragmentNavigator.Destination)destination).getClassName();
            }

            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, navDestination.getLabel().toString());
            bundle.putString(FirebaseAnalytics.Param.SCREEN_CLASS, currentFragmentClassName);
            reportEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle);
        };
    }

    public static void reportConnectTabChange(String tabName) {
        reportEvent(CCAnalyticsEvent.CCC_TAB_CHANGE,
                new String[]{CCAnalyticsParam.PARAM_CCC_TAB_CHANGE_NAME},
                new String[]{tabName});
    }

    public static void reportNotificationType(String notificationType) {
        reportEvent(CCAnalyticsEvent.CCC_NOTIFICATION_TYPE,
                CCAnalyticsParam.NOTIFICATION_TYPE, notificationType);
    }

    public static void reportRekeyedDatabase() {
        reportEvent(CCAnalyticsEvent.CCC_REKEYED_DB);
    }
}
