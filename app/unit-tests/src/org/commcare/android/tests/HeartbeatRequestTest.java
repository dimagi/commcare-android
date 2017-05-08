package org.commcare.android.tests;

import org.commcare.CommCareApplication;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.heartbeat.HeartbeatLifecycleManager;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by amstone326 on 5/8/17.
 */

public class HeartbeatRequestTest {

    private final static String APP_BASE = "jr://resource/commcare-apps/form_nav_tests/";

    private static final String TEST_RESPONSE_1 =
            "{\"app_id\":\"73d5f08b9d55fe48602906a89672c214\"," +
                    "\"latest_apk_version\":{\"value\":\"2.36.1\"}," +
                    "\"latest_ccz_version\":{\"value\":\"75\", \"force_by_date\":\"2017-05-01\"}}";

    private HeartbeatLifecycleManager heartbeatManager;


    @Test
    public void test1() {
        TestAppInstaller.installApp(APP_BASE + "profile.ccpr");
        CommCareApplication.instance().getCurrentApp().setMMResourcesValidated();
        login();
        launchHomeActivity();
        heartbeatManager = new HeartbeatLifecycleManager(CommCareApplication.instance().getSession()) {

            @Override
            protected void makeRequest() {

            }

        };
    }

    private void login() {
        // Look at DemoUserRestoreTest
    }

    private void launchHomeActivity() {
        // Look at DemoUserRestoreTest
    }

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
