package org.commcare.android.util;

import org.acra.ACRA;
import org.acra.ErrorReporter;

/**
 * Created by wpride1 on 3/3/15.
 */
public class ACRAUtil {

    public static final String POST_URL = "post_url";
    public static final String VERSION = "version";
    public static final String DOMAIN = "domain";
    public static final String USERNAME = "username";

    public static void addCustomData(String key, String value){
        ErrorReporter mReporter = ACRA.getErrorReporter();
        mReporter.putCustomData(key, value);
    }

}
