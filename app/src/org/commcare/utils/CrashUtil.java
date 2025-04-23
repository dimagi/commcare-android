package org.commcare.utils;

import org.commcare.CommCareApplication;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.connect.ConnectIDManager;
import org.commcare.dalvik.BuildConfig;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

/**
 * Contains constants and methods used in Crashlytics reporting.
 * <p>
 * Created by shubham on 8/09/17.
 */
public class CrashUtil {

    private static final String APP_VERSION = "app_version";
    private static final String APP_NAME = "app_name";
    private static final String DOMAIN = "domain";
    private static final String DEVICE_ID = "device_id";
    private static final String CCC_USER = "ccc_user_id";

    private static boolean crashlyticsEnabled = BuildConfig.USE_CRASHLYTICS;

    public static void reportException(Throwable e) {
        if (crashlyticsEnabled) {
            FirebaseCrashlytics.getInstance().recordException(e);
        }
    }

    public static void init() {
        if (crashlyticsEnabled) {
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.setCrashlyticsCollectionEnabled(true);
            crashlytics.setCustomKey(DEVICE_ID, ReportingUtils.getDeviceId());
        }
    }

    public static void registerAppData() {
        if (crashlyticsEnabled) {
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.setCustomKey(DOMAIN, ReportingUtils.getDomain());
            crashlytics.setCustomKey(APP_VERSION, ReportingUtils.getAppVersion());
            crashlytics.setCustomKey(APP_NAME, ReportingUtils.getAppName());
        }
    }

    public static void registerUserData() {
        if (crashlyticsEnabled) {
            FirebaseCrashlytics.getInstance().setUserId(ReportingUtils.getUser());
        }
    }

    public static void log(String message) {
        if (crashlyticsEnabled) {
            FirebaseCrashlytics.getInstance().log(message);
        }
    }

    /**
     * Registers the current Connect user with Firebase Crashlytics for error tracking.
     * <p>
     * If Crashlytics is enabled and a Connect ID is configured, this method retrieves
     * the user ID from ConnectManager and sets it as a custom key in Crashlytics.
     * <p>
     * In case of any exceptions during this process, the exception is recorded in
     * Crashlytics to aid debugging.
     */
    public static void registerConnectUser() {
        if (crashlyticsEnabled && ConnectIDManager.getInstance().isloggedIn()) {
            try {
                String userId = ConnectIDManager.getInstance().getUser(CommCareApplication.instance()).getUserId();
                FirebaseCrashlytics.getInstance().setCustomKey(CCC_USER, userId);
            } catch (Exception e) {
                FirebaseCrashlytics.getInstance().recordException(e);
            }
        }
    }
}
