package org.commcare.android.tests;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Created by amstone326 on 5/10/17.
 */

public class HeartbeatTest {

    private final static String APP_BASE = "jr://resource/commcare-apps/form_entry_tests/";

    private static final String RESPONSE_CorrectApp_BothUpdatesNeeded =
            "{\"app_id\":\"36c0bdd028d14a52cbff95bb1bfd0962\"," +
                    "\"latest_apk_version\":{\"value\":\"2.36.1\"}," +
                    "\"latest_ccz_version\":{\"value\":\"75\", \"force_by_date\":\"2017-05-01\"}}";

    /*protected static void parseTestHeartbeatResponse() {
        System.out.println("NOTE: Testing heartbeat response processing");
        try {
            parseHeartbeatResponse(new JSONObject(TEST_RESPONSE));
        } catch (JSONException e) {
            System.out.println("Test response was not properly formed JSON");
        }
    }*/

    protected static void simulateRequestGettingStuck() {
        System.out.println("Before sleeping");
        try {
            Thread.sleep(5*1000);
        } catch (InterruptedException e) {
            System.out.println("TEST ERROR: sleep was interrupted");
        }
        System.out.println("After sleeping");
    }


}
