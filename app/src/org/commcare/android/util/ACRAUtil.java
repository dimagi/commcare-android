package org.commcare.android.util;

import android.app.Application;
import android.webkit.URLUtil;

import org.acra.ACRA;
import org.acra.ACRAConfiguration;
import org.acra.ErrorReporter;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.activities.ReportProblemActivity;

/**
 * Contains constants and methods used in ACRA reporting.
 *
 * Created by wpride1 on 3/3/15.
 */
public class ACRAUtil {

    private static final String POST_URL = "post_url";
    private static final String VERSION = "version";
    private static final String DOMAIN = "domain";
    private static final String USERNAME = "username";

    private static boolean isAcraConfigured = false;

    /**
     * Add debugging value to the ACRA report bundle. Only most recent value
     * stored for each key.
     */
    private static void addCustomData(String key, String value) {
        ErrorReporter mReporter = ACRA.getErrorReporter();
        mReporter.putCustomData(key, value);
    }

    public static void initACRA(Application app) {
        String url = BuildConfig.ACRA_URL;
        if (URLUtil.isValidUrl(url)) {
            ACRA.init(app);
            ACRAConfiguration acraConfig = ACRA.getConfig();
            acraConfig.setFormUriBasicAuthLogin(BuildConfig.ACRA_USER);
            acraConfig.setFormUriBasicAuthPassword(BuildConfig.ACRA_PASSWORD);
            acraConfig.setFormUri(url);
            ACRA.setConfig(acraConfig);
            isAcraConfigured = true;
        }
    }

    public static void registerAppData() {
        if (isAcraConfigured) {
            addCustomData(ACRAUtil.POST_URL, ReportProblemActivity.getPostURL());
            addCustomData(ACRAUtil.VERSION, ReportProblemActivity.getVersion());
            addCustomData(ACRAUtil.DOMAIN, ReportProblemActivity.getDomain());
        }
    }

    public static void registerUserData() {
        if (isAcraConfigured) {
            ACRAUtil.addCustomData(ACRAUtil.USERNAME, ReportProblemActivity.getUser());
        }
    }
}
