package org.commcare.android.util;

import android.app.Application;

import org.acra.ACRA;
import org.acra.ACRAConfiguration;
import org.acra.ErrorReporter;
import org.commcare.android.javarosa.AndroidLogger;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.util.Properties;

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
     * @param key
     * @param value
     */
    public static void addCustomData(String key, String value){
        ErrorReporter mReporter = ACRA.getErrorReporter();
        mReporter.putCustomData(key, value);
    }

    public static void initACRA(Application app){

        ACRA.init(app);

        try {
            Properties properties = FileUtil.loadProperties(app);
            ACRAConfiguration acraConfig = ACRA.getConfig();
            acraConfig.setFormUriBasicAuthLogin(properties.getProperty("ACRA_USER"));
            acraConfig.setFormUriBasicAuthPassword(properties.getProperty("ACRA_PASSWORD"));
            acraConfig.setFormUri(properties.getProperty("ACRA_URL"));
            ACRA.setConfig(acraConfig);
        } catch (IOException e){
            Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, "Couldn't load ACRA credentials.");
        }
    }

}
