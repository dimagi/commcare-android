package org.commcare.android.util;

import android.app.Application;
import android.webkit.URLUtil;

import org.acra.ACRA;
import org.acra.ErrorReporter;
import org.acra.config.ACRAConfiguration;
import org.acra.config.ACRAConfigurationFactory;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.dalvik.BuildConfig;

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
            ACRAConfiguration acraConfig = new ACRAConfigurationFactory().create(app);
            acraConfig.setFormUriBasicAuthLogin(BuildConfig.ACRA_USER);
            acraConfig.setFormUriBasicAuthPassword(BuildConfig.ACRA_PASSWORD);
            acraConfig.setFormUri(url);
            ACRA.init(app, acraConfig);
            isAcraConfigured = true;
        }
    }

    public static void registerAppData() {
        if (isAcraConfigured) {
            addCustomData(ACRAUtil.POST_URL, ReportingUtils.getPostURL());
            addCustomData(ACRAUtil.VERSION, ReportingUtils.getVersion());
            addCustomData(ACRAUtil.DOMAIN, ReportingUtils.getDomain());
        }
    }

    public static void registerUserData() {
        if (isAcraConfigured) {
            ACRAUtil.addCustomData(ACRAUtil.USERNAME, ReportingUtils.getUser());
        }
    }
}
