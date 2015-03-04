package org.commcare.android.util;

import org.acra.ACRA;
import org.acra.ErrorReporter;

/**
 * Created by wpride1 on 3/3/15.
 */
public class ACRAUtil {

    public static final String CURRENT_CASE  = "case_id";
    public static final String POST_URL = "post_url";
    public static final String VERSION = "version";
    public static final String DOMAIN = "domain";
    public static final String USERNAME = "username";
    public static final String LAST_ANSWER_DATA = "last_answer_data";
    public static final String LAST_INDEX = "last_form_index";
    public static final String LAST_QUESTION_PROMPT = "last_form_prompt";

    public static void addCustomData(String key, String value){
        ErrorReporter mReporter = ACRA.getErrorReporter();
        mReporter.putCustomData(key, value);
    }

}
