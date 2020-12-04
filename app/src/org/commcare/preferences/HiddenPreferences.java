package org.commcare.preferences;

import android.content.SharedPreferences;

import org.commcare.AppUtils;
import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.GeoPointActivity;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.GeoUtils;
import org.commcare.utils.MapLayer;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

/**
 * Provides hooks for reading and writing all preferences that are not configurable by the user in
 * the mobile UI. There are 2 classes of settings that fall into this category:
 * 1) Those whose values are sent down from HQ via the app profile
 * 2) Those whose values are set based upon actions that a user takes during their usage of CommCare
 *
 * Created by amstone326 on 11/14/17.
 */
public class HiddenPreferences {

    // Preferences that are set in the course of CommCare usage, based upon a user's workflow/actions
    public final static String HAS_DISMISSED_PIN_CREATION = "has-dismissed-pin-creation";
    public final static String LAST_LOGGED_IN_USER = "last_logged_in_user";
    final static String LAST_PASSWORD = "last_password";
    public final static String POST_UPDATE_SYNC_NEEDED = "post-update-sync-needed";
    public final static String PRE_UPDATE_SYNC_NEEDED = "pre-update-sync-needed";
    public final static String AUTO_UPDATE_IN_PROGRESS = "cc-trying-to-auto-update";
    public final static String LAST_UPDATE_ATTEMPT = "cc-last_up";
    public final static String LAST_UPLOAD_SYNC_ATTEMPT = "last-upload-sync";
    public final static String LOG_LAST_DAILY_SUBMIT = "log_prop_last_daily";
    public final static String ID_OF_INTERRUPTED_SSD = "interrupted-ssd-id";
    private final static String LATEST_RECOVERY_MEASURE = "latest-recovery-measure-exectued";
    public static final String LAST_SUCCESSFUL_CC_VERSION = "last_successful_commcare_version";
    public final static String FIRST_COMMCARE_RUN = "first-commcare-run";
    public final static String LATEST_COMMCARE_VERSION = "latest-commcare-version";
    public final static String LATEST_APP_VERSION = "latest-app-version";
    private static final String LAST_LOG_DELETION_TIME = "last_log_deletion_time";
    private final static String FORCE_LOGS = "force-logs";
    private final static String COMMCARE_UPDATE_CANCELLATION_COUNTER = "cc_update_cancellation_counter";
    private final static String DISABLE_RATE_LIMIT_POPUP = "disable-rate-limit-popup";
    private final static String LAZY_MEDIA_DOWNLOAD_COMPLETE = "lazy-media-download-complete";
    private final static String RAW_MEDIA_CLEANUP_COMPLETE = "raw_media_cleanup_complete";

    // Preferences whose values are only ever set by being sent down from HQ via the profile file
    private final static String USE_MAPBOX_MAP = "cc-use-mapbox-map";
    private final static String LABEL_REQUIRED_QUESTIONS_WITH_ASTERISK = "cc-label-required-questions-with-asterisk";
    private final static String MAPS_DEFAULT_LAYER = "cc-maps-default-layer";
    public final static String AUTO_SYNC_FREQUENCY = "cc-autosync-freq";
    private final static String ENABLE_SAVED_FORMS = "cc-show-saved";
    private final static String ENABLE_INCOMPLETE_FORMS = "cc-show-incomplete";
    public final static String BRAND_BANNER_LOGIN = "brand-banner-login";
    public final static String BRAND_BANNER_HOME = "brand-banner-home";
    public final static String BRAND_BANNER_HOME_DEMO = "brand-banner-home-demo";
    private final static String LOGIN_DURATION = "cc-login-duration-seconds";
    private final static String GPS_AUTO_CAPTURE_ACCURACY = "cc-gps-auto-capture-accuracy";
    private final static String GPS_AUTO_CAPTURE_TIMEOUT_MINS = "cc-gps-auto-capture-timeout";
    private final static String GPS_WIDGET_GOOD_ACCURACY = "cc-gps-widget-good-accuracy";
    private final static String GPS_WIDGET_ACCEPTABLE_ACCURACY = "cc-gps-widget-acceptable-accuracy";
    private final static String GPS_WIDGET_TIMEOUT_SECS = "cc-gps-widget-timeout-secs";
    private static final String APP_VERSION_TAG = "cc-app-version-tag";
    private final static String LOG_ENTITY_DETAIL = "cc-log-entity-detail-enabled";
    public static final String DUMP_FOLDER_PATH = "dump-folder-path";
    private final static String RESIZING_METHOD = "cc-resize-images";
    private static final String KEY_TARGET_DENSITY = "cc-inflation-target-density";
    // Used to make it so that CommCare will not conduct a multimedia validation check
    public final static String MM_VALIDATED_FROM_HQ = "cc-content-valid";
    private static final String USER_DOMAIN_SUFFIX = "cc_user_domain";
    private final static String LOGS_ENABLED = "logenabled";
    public final static String LOGS_ENABLED_YES = "Enabled";
    public final static String LOGS_ENABLED_NO = "Disabled";
    public final static String LOGS_ENABLED_ON_DEMAND = "on_demand";
    private final static String RELEASED_ON_TIME_FOR_ONGOING_APP_DOWNLOAD = "released-on-time-for-ongoing-app-download";
    private final static String FILE_OVERSIZE_WARNING = "cc-disable-file-oversize-warning";
    private final static String ALLOW_RUN_ON_ROOTED_DEVICE = "cc-allow-run-on-rooted-device";


    // Boolean pref to determine whether user has already been through the update information form
    public final static String SHOW_XFORM_UPDATE_INFO = "show-xform-update-info";

    // last known filepath where ccz was installed from
    private static final String LAST_KNOWN_CCZ_LOCATION = "last_known_ccz_location";

    // Internal pref to bypass PRE_UPDATE_SYNC_NEEDED using advanced settings
    private static final String BYPASS_PRE_UPDATE_SYNC = "bypass_pre_update_sync";
    private static final String DISABLE_BACKGROUND_WORK_TIME = "disable-background-work-time";


    private static final long NO_OF_HOURS_TO_WAIT_TO_RESUME_BACKGROUND_WORK = 36;


    /**
     * @return How many seconds should a user session remain open before expiring?
     */
    public static int getLoginDuration() {
        final int oneDayInSecs = 60 * 60 * 24;

        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();

        // try loading setting but default to 24 hours
        try {
            return Integer.parseInt(properties.getString(LOGIN_DURATION,
                    Integer.toString(oneDayInSecs)));
        } catch (NumberFormatException e) {
            return oneDayInSecs;
        }
    }

    // Controls whether to show the number of unsent forms on Sync button when there are no unsent forms
    private final static String SHOW_UNSENT_FORMS_WHEN_ZERO = "cc-show_unsent_forms_when_zero";

    /**
     * @return Accuracy needed for GPS auto-capture to stop polling during form entry
     */
    public static double getGpsAutoCaptureAccuracy() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        try {
            return Double.parseDouble(properties.getString(GPS_AUTO_CAPTURE_ACCURACY,
                    Double.toString(GeoUtils.AUTO_CAPTURE_GOOD_ACCURACY)));
        } catch (NumberFormatException e) {
            return GeoUtils.AUTO_CAPTURE_GOOD_ACCURACY;
        }
    }

    /**
     * Time to wait in milliseconds before stopping GPS auto-capture if it
     * hasn't already obtained an accurate reading
     */
    public static int getGpsAutoCaptureTimeoutInMilliseconds() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        try {
            return (int)TimeUnit.MINUTES.toMillis(Long.parseLong(
                    properties.getString(GPS_AUTO_CAPTURE_TIMEOUT_MINS,
                            Integer.toString(GeoUtils.AUTO_CAPTURE_MAX_WAIT_IN_MINUTES))));
        } catch (NumberFormatException e) {
            return (int)TimeUnit.MINUTES.toMillis(GeoUtils.AUTO_CAPTURE_MAX_WAIT_IN_MINUTES);
        }
    }

    /**
     * Accuracy in meters needed for the GPS question widget to auto-close
     */
    public static double getGpsWidgetGoodAccuracy() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        try {
            return Double.parseDouble(properties.getString(GPS_WIDGET_GOOD_ACCURACY,
                    Double.toString(GeoUtils.DEFAULT_GOOD_ACCURACY)));
        } catch (NumberFormatException e) {
            return GeoUtils.DEFAULT_GOOD_ACCURACY;
        }
    }

    /**
     * Accuracy in meters needed for the GPS question widget to begin storing location.
     */
    public static double getGpsWidgetAcceptableAccuracy() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        try {
            return Double.parseDouble(properties.getString(GPS_WIDGET_ACCEPTABLE_ACCURACY,
                    Double.toString(GeoUtils.DEFAULT_ACCEPTABLE_ACCURACY)));
        } catch (NumberFormatException e) {
            return GeoUtils.DEFAULT_ACCEPTABLE_ACCURACY;
        }
    }

    /**
     * Duration in milliseconds before GPS question widget starts storing the
     * current GPS location, no matter how accurate.
     */
    public static int getGpsWidgetTimeoutInMilliseconds() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        try {
            return (int)TimeUnit.SECONDS.toMillis(Long.parseLong(
                    properties.getString(GPS_WIDGET_TIMEOUT_SECS,
                            Integer.toString(GeoPointActivity.DEFAULT_MAX_WAIT_IN_SECS))));
        } catch (NumberFormatException e) {
            return (int)TimeUnit.SECONDS.toMillis(GeoPointActivity.DEFAULT_MAX_WAIT_IN_SECS);
        }
    }

    public static String getResizeMethod() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        // If there is a setting for form management it takes precedence
        if (properties.contains(RESIZING_METHOD)) {
            return properties.getString(RESIZING_METHOD, PrefValues.NONE);
        }

        // Otherwise, see if we're in sense mode
        return PrefValues.NONE;
    }

    public static boolean isSmartInflationEnabled() {
        CommCareApp app = CommCareApplication.instance().getCurrentApp();
        if (app == null) {
            return false;
        }
        String targetDensitySetting = app.getAppPreferences().getString(KEY_TARGET_DENSITY,
                PrefValues.NONE);
        return !targetDensitySetting.equals(PrefValues.NONE);
    }

    public static int getTargetInflationDensity() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return Integer.parseInt(properties.getString(KEY_TARGET_DENSITY, PrefValues.DEFAULT_TARGET_DENSITY));
    }

    public static boolean isEntityDetailLoggingEnabled() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return properties.getString(LOG_ENTITY_DETAIL, PrefValues.FALSE).equals(PrefValues.TRUE);
    }

    public static boolean isSavedFormsEnabled() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        // If there is a setting for form management it takes precedence
        return !properties.contains(ENABLE_SAVED_FORMS) ||
                properties.getString(ENABLE_SAVED_FORMS, PrefValues.YES).equals(PrefValues.YES);
    }

    public static boolean isLoggingEnabled() {
        if (CommCareApplication.instance().getCurrentApp() == null) {
            return true;
        }

        String logsEnabled = getLogsEnabled();
        return logsEnabled.equals(LOGS_ENABLED_YES) || logsEnabled.equals(LOGS_ENABLED_ON_DEMAND);
    }

    public static String getLogsEnabled() {
        if (CommCareApplication.instance().getCurrentApp() == null) {
            return LOGS_ENABLED_NO;
        }

        return CommCareApplication.instance().getCurrentApp().getAppPreferences().getString(LOGS_ENABLED, LOGS_ENABLED_YES);
    }


    public static boolean isIncompleteFormsEnabled() {
        if (CommCareApplication.instance().isConsumerApp()) {
            return false;
        }

        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        // If there is a setting for form management it takes precedence
        return !properties.contains(ENABLE_INCOMPLETE_FORMS) ||
                properties.getString(ENABLE_INCOMPLETE_FORMS, PrefValues.YES).equals(PrefValues.YES);
    }

    public static String getUserDomain() {
        SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return prefs.getString(USER_DOMAIN_SUFFIX, null);
    }

    public static void setPostUpdateSyncNeeded(boolean b) {
        CommCareApplication.instance().getCurrentApp().getAppPreferences().edit()
                .putBoolean(POST_UPDATE_SYNC_NEEDED, b).apply();
    }

    public static void setInterruptedSSD(int ssdId) {
        String currentUserId = CommCareApplication.instance().getCurrentUserId();
        CommCareApplication.instance().getCurrentApp().getAppPreferences().edit()
                .putInt(ID_OF_INTERRUPTED_SSD + currentUserId, ssdId).apply();
    }

    public static int getIdOfInterruptedSSD() {
        String currentUserId = CommCareApplication.instance().getCurrentUserId();
        return CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .getInt(ID_OF_INTERRUPTED_SSD + currentUserId, -1);
    }

    public static void clearInterruptedSSD() {
        String currentUserId = CommCareApplication.instance().getCurrentUserId();
        CommCareApplication.instance().getCurrentApp().getAppPreferences().edit()
                .putInt(ID_OF_INTERRUPTED_SSD + currentUserId, -1).apply();
    }

    public static long getLatestRecoveryMeasureExecuted() {
        return CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .getLong(LATEST_RECOVERY_MEASURE, -1);
    }

    public static void setLatestRecoveryMeasureExecuted(long latestSequenceNumber) {
        System.out.println("Executed recovery measure # " + latestSequenceNumber);
        // The measure we executed may have been an app uninstall, so it's possible this will be null
        if (CommCareApplication.instance().getCurrentApp() != null) {
            CommCareApplication.instance().getCurrentApp().getAppPreferences()
                    .edit().putLong(LATEST_RECOVERY_MEASURE, latestSequenceNumber)
                    .apply();
        }
    }

    public static void setShowXformUpdateInfo(boolean showXformUpdateInfo) {
        CommCareApplication.instance().getCurrentApp().getAppPreferences().edit()
                .putBoolean(SHOW_XFORM_UPDATE_INFO, showXformUpdateInfo).apply();
    }

    public static Boolean shouldShowXformUpdateInfo() {
        return CommCareApplication.instance().getCurrentApp()
                .getAppPreferences().getBoolean(SHOW_XFORM_UPDATE_INFO, false);
    }

    public static Boolean shouldLabelRequiredQuestionsWithAsterisk() {
        return CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .getString(LABEL_REQUIRED_QUESTIONS_WITH_ASTERISK, PrefValues.NO).equals(PrefValues.YES);
    }

    public static void setLatestCommcareVersion(String ccVersion) {
        PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance())
                .edit()
                .putString(LATEST_COMMCARE_VERSION, ccVersion).apply();
    }

    public static String getLatestCommcareVersion() {
        return PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance())
                .getString(LATEST_COMMCARE_VERSION, ReportingUtils.getCommCareVersionString());
    }

    public static void setLatestAppVersion(int appVersion) {
        CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .edit()
                .putInt(LATEST_APP_VERSION, appVersion).apply();
    }

    public static String getAppVersionTag() {
        return CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .getString(APP_VERSION_TAG, "");
    }

    public static MapLayer getMapsDefaultLayer() {
        try {
            String mapType = CommCareApplication.instance().getCurrentApp().getAppPreferences()
                    .getString(MAPS_DEFAULT_LAYER, "normal");
            return MapLayer.valueOf(mapType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MapLayer.NORMAL;
        }
    }

    public static void setMapsDefaultLayer(MapLayer mapLayer) {
        CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .edit()
                .putString(MAPS_DEFAULT_LAYER, mapLayer.toString())
                .apply();
    }

    public static int getLatestAppVersion() {
        return CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .getInt(LATEST_APP_VERSION, -1);
    }

    public static void setLastKnownCczLocation(String cczPath) {
        PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance())
                .edit()
                .putString(LAST_KNOWN_CCZ_LOCATION, cczPath).apply();
    }

    @Nullable
    public static String getLastKnownCczLocation() {
        return PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance())
                .getString(LAST_KNOWN_CCZ_LOCATION, null);
    }


    public static void setForceLogs(String userId, boolean forceLogs) {
        PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance())
                .edit()
                .putBoolean(getUserSpecificKey(userId, FORCE_LOGS), forceLogs)
                .apply();

    }


    public static boolean shouldForceLogs(String userId) {
        return PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance())
                .getBoolean(getUserSpecificKey(userId, FORCE_LOGS), false);
    }

    public static void updateLastUploadSyncAttemptTime() {
        String userId = CommCareApplication.instance().getSession().getLoggedInUser().getUniqueId();
        CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .edit()
                .putLong(getUserSpecificKey(userId, LAST_UPLOAD_SYNC_ATTEMPT), new Date().getTime())
                .apply();
    }

    public static long getLastUploadSyncAttempt() {
        String userId = CommCareApplication.instance().getSession().getLoggedInUser().getUniqueId();
        return CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .getLong(getUserSpecificKey(userId, LAST_UPLOAD_SYNC_ATTEMPT), 0);
    }

    public static boolean shouldShowUnsentFormsWhenZero() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return properties.getString(SHOW_UNSENT_FORMS_WHEN_ZERO, PrefValues.NO).equals(PrefValues.YES);
    }


    public static boolean preUpdateSyncNeeded() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return properties.getString(PRE_UPDATE_SYNC_NEEDED, PrefValues.NO).equals(PrefValues.YES);
    }

    public static void setPreUpdateSyncNeeded(String value) {
        CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .edit()
                .putString(PRE_UPDATE_SYNC_NEEDED, value)
                .apply();
    }

    public static void setReleasedOnTimeForOngoingAppDownload(AndroidCommCarePlatform platform, long releasedOnTime) {
        if (platform.getApp() == null) {
            return;
        }

        platform.getApp().getAppPreferences()
                .edit()
                .putLong(RELEASED_ON_TIME_FOR_ONGOING_APP_DOWNLOAD, releasedOnTime)
                .apply();
    }

    public static long geReleasedOnTimeForOngoingAppDownload() {
        if (CommCareApplication.instance().getCurrentApp() == null) {
            return 0;
        }
        long releasedOnTime = CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .getLong(RELEASED_ON_TIME_FOR_ONGOING_APP_DOWNLOAD, 0);
        // Since phone date time can change, we wanna restrict the released on time to the current time
        releasedOnTime = Math.min(releasedOnTime, new Date().getTime());
        return releasedOnTime;
    }


    public static void updateLastLogDeletionTime() {
        String userId = CommCareApplication.instance().getSession().getLoggedInUser().getUniqueId();
        CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .edit()
                .putLong(getUserSpecificKey(userId, LAST_LOG_DELETION_TIME), new Date().getTime())
                .apply();
    }

    public static long getLastLogDeletionTime() {
        String userId = CommCareApplication.instance().getSession().getLoggedInUser().getUniqueId();
        return CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .getLong(getUserSpecificKey(userId, LAST_LOG_DELETION_TIME), 0);
    }

    private static String getUserSpecificKey(String userId, String preferenceName) {
        return userId + "_" + preferenceName;
    }

    public static void enableBypassPreUpdateSync(boolean enable) {
        CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .edit()
                .putBoolean(BYPASS_PRE_UPDATE_SYNC, enable)
                .apply();
    }

    public static boolean shouldBypassPreUpdateSync() {
        return CommCareApplication.instance().getCurrentApp().getAppPreferences().getBoolean(BYPASS_PRE_UPDATE_SYNC, false);
    }

    public static boolean shouldUseMapboxMap() {
        return CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .getString(USE_MAPBOX_MAP, PrefValues.NO).equals(PrefValues.YES);
    }

    public static void setDisableBackgroundWorkTime(boolean disableBackgroundWork) {
        long time = disableBackgroundWork ? -1 : new Date().getTime();
        CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .edit()
                .putLong(DISABLE_BACKGROUND_WORK_TIME, time)
                .apply();
    }

    public static boolean shouldDisableBackgroundWork() {
        long referenceTime = CommCareApplication.instance()
                .getCurrentApp()
                .getAppPreferences()
                .getLong(DISABLE_BACKGROUND_WORK_TIME, -1);

        return referenceTime != -1 &&
                new Date().getTime() - referenceTime < TimeUnit.HOURS.toMillis(NO_OF_HOURS_TO_WAIT_TO_RESUME_BACKGROUND_WORK);
    }

    public static void incrementCommCareUpdateCancellationCounter(String version) {
        String key = COMMCARE_UPDATE_CANCELLATION_COUNTER + "_" + version;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance());
        int count = sharedPreferences.getInt(key, 0);
        sharedPreferences.edit().putInt(key, count + 1).apply();
    }

    public static int getCommCareUpdateCancellationCounter(String version) {
        String key = COMMCARE_UPDATE_CANCELLATION_COUNTER + "_" + version;
        return PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance()).getInt(key, 0);
    }

    public static boolean isRateLimitPopupDisabled() {
        return CommCareApplication.instance().getCurrentApp().getAppPreferences().getBoolean(DISABLE_RATE_LIMIT_POPUP, false);
    }

    public static void disableRateLimitPopup(boolean disable) {
        CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .edit()
                .putBoolean(DISABLE_RATE_LIMIT_POPUP, disable)
                .apply();
    }

    public static void setLazyMediaDownloadComplete(boolean lazyMediaDownloadComplete) {
        CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .edit()
                .putBoolean(LAZY_MEDIA_DOWNLOAD_COMPLETE, lazyMediaDownloadComplete)
                .apply();
    }

    /**
     * @return whether the lazy media download has already completed successfully for this app version
     */
    public static boolean isLazyMediaDownloadComplete() {
        return CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .getBoolean(LAZY_MEDIA_DOWNLOAD_COMPLETE, false);
    }

    public static boolean isFileOversizeWarningDisabled() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return PrefValues.YES.equals(properties.getString(FILE_OVERSIZE_WARNING, PrefValues.NO));
    }

    public static boolean allowRunOnRootedDevice() {
        return DeveloperPreferences.doesPropertyMatch(ALLOW_RUN_ON_ROOTED_DEVICE, PrefValues.YES, PrefValues.YES);
    }

    public static boolean isRawMediaCleanUpPending() {
        return !CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .getBoolean(RAW_MEDIA_CLEANUP_COMPLETE, false);
    }

    public static void markRawMediaCleanUpComplete() {
        CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .edit().putBoolean(RAW_MEDIA_CLEANUP_COMPLETE, true).apply();
    }
}
