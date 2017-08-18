package org.commcare.utils;

import android.app.Application;
import android.content.Context;
import android.webkit.URLUtil;

import com.crashlytics.android.Crashlytics;

import org.commcare.CommCareApplication;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.dalvik.BuildConfig;

import io.fabric.sdk.android.Fabric;

/**
 * Contains constants and methods used in Crashlytics reporting.
 *
 * Created by wpride1 on 3/3/15.
 */
public class CrashUtil {

    private static final String APP_VERSION = "app_version";
    private static final String APP_NAME = "app_name";
    private static final String DOMAIN = "domain";
    private static final String DEVICE_ID = "device_id";

    private static boolean crashlyticsEnabled = BuildConfig.USE_CRASHLYTICS;

    public static void reportException(Exception e) {
        if (crashlyticsEnabled) {
            Crashlytics.logException(e);
        }
    }

    public static void init(Context context) {
        if(crashlyticsEnabled) {
            Fabric.with(context, new Crashlytics());
            Crashlytics.setString(DEVICE_ID,ReportingUtils.getDeviceId());
        }
    }

    public static void registerAppData() {
        if (crashlyticsEnabled) {
            Crashlytics.setString(DOMAIN, ReportingUtils.getDomain());
            Crashlytics.setInt(APP_VERSION, ReportingUtils.getAppVersion());
            Crashlytics.setString(APP_NAME, ReportingUtils.getAppName());
        }
    }

    public static void registerUserData() {
        if (crashlyticsEnabled) {
            Crashlytics.setUserName(ReportingUtils.getUser());
            Crashlytics.setUserIdentifier(CommCareApplication.instance().getCurrentUserId());
        }
    }
}
