package org.commcare.android.util;

import android.app.Application;

import org.acra.ACRA;
import org.acra.ACRAConfiguration;
import org.acra.ErrorReporter;
import org.commcare.dalvik.R;

/**
 * Contains constants and methods used in ACRA reporting.
 *
 * Created by wpride1 on 3/3/15.
 */
public class ACRAUtil {

    public static final String POST_URL = "post_url";
    public static final String VERSION = "version";
    public static final String DOMAIN = "domain";
    public static final String USERNAME = "username";

    /**
     * Add debugging value to the ACRA report bundle. Only most recent value stored for each key.
     */
    public static void addCustomData(String key, String value){
        ErrorReporter mReporter = ACRA.getErrorReporter();
        mReporter.putCustomData(key, value);
    }

    public static void initACRA(Application app){

        ACRA.init(app);
        ACRAConfiguration acraConfig = ACRA.getConfig();
        acraConfig.setFormUriBasicAuthLogin(app.getString(R.string.acra_user));
        acraConfig.setFormUriBasicAuthPassword(app.getString(R.string.acra_password));
        acraConfig.setFormUri(app.getString(R.string.acra_url));
        ACRA.setConfig(acraConfig);

    }

}
