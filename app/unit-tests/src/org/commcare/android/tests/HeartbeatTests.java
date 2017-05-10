package org.commcare.android.tests;

import junit.framework.Assert;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.heartbeat.TestHeartbeatRequester;
import org.commcare.heartbeat.UpdatePromptHelper;
import org.commcare.heartbeat.UpdateToPrompt;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Created by amstone326 on 5/10/17.
 */
@Config(application = CommCareTestApplication.class)
@RunWith(CommCareTestRunner.class)
public class HeartbeatTests {

    private static final String RESPONSE_CorrectApp_CczUpdateNeeded =
            "{\"app_id\":\"36c0bdd028d14a52cbff95bb1bfd0962\"," +
                    "\"latest_apk_version\":{\"value\":\"2.35.0\"}," +
                    "\"latest_ccz_version\":{\"value\":\"97\"}}";

    private static final String RESPONSE_CorrectApp_NoUpdateNeeded =
            "{\"app_id\":\"36c0bdd028d14a52cbff95bb1bfd0962\"," +
                    "\"latest_apk_version\":{\"value\":\"2.35.0\"}," +
                    "\"latest_ccz_version\":{\"value\":\"75\", \"force_by_date\":\"2017-05-01\"}}";

    @Before
    public void setup() {
        // The app version for this app is 95
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/form_entry_tests/profile.ccpr", "test", "123");
    }

    @Test
    public void testHeartbeatForCorrectApp() {
        TestHeartbeatRequester.setNextResponseString(RESPONSE_CorrectApp_CczUpdateNeeded);
        CommCareApplication.instance().getSession().initHeartbeatLifecycle();
        UpdateToPrompt cczUpdate = UpdatePromptHelper.getCurrentUpdateToPrompt(false);
        Assert.assertNotNull(cczUpdate);
        Assert.assertTrue(cczUpdate.isNewerThanCurrentVersion(CommCareApplication.instance().getCurrentApp()));
        Assert.assertEquals(97, cczUpdate.getCczVersion());
    }

    @Test
    public void testHeartbeatForWrongApp() {

    }

}
