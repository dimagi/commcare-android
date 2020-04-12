package org.commcare.android.tests;

import junit.framework.Assert;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.StandardHomeActivity;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.mocks.FormAndDataSyncerFake;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.heartbeat.ApkVersion;
import org.commcare.heartbeat.TestHeartbeatRequester;
import org.commcare.heartbeat.UpdatePromptHelper;
import org.commcare.heartbeat.UpdatePromptShowHistory;
import org.commcare.heartbeat.UpdateToPrompt;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertTrue;

/**
 * Created by amstone326 on 5/10/17.
 */
@Config(application = CommCareTestApplication.class)
@RunWith(CommCareTestRunner.class)
public class HeartbeatAndPromptedUpdateTests {

    private static final String RESPONSE_CorrectApp_CczUpdateNeeded =
            "{\"app_id\":\"36c0bdd028d14a52cbff95bb1bfd0962\"," +
                    "\"latest_apk_version\":{\"value\":\"2.25.0\"}," +
                    "\"latest_ccz_version\":{\"value\":\"97\"}}";

    private static final String RESPONSE_CorrectApp_NoUpdateNeeded =
            "{\"app_id\":\"36c0bdd028d14a52cbff95bb1bfd0962\"," +
                    "\"latest_apk_version\":{\"value\":\"2.25.0\"}," +
                    "\"latest_ccz_version\":{\"value\":\"75\"}}";

    private static final String RESPONSE_WrongApp_CczUpdateNeeded =
            "{\"app_id\":\"6a03772aedd992c9f2c9c2198a248184\"," +
                    "\"latest_apk_version\":{\"value\":\"2.25.0\"}," +
                    "\"latest_ccz_version\":{\"value\":\"97\"}}";

    private static final String RESPONSE_CorrectApp_CczUpdateNeeded_WithForce =
            "{\"app_id\":\"36c0bdd028d14a52cbff95bb1bfd0962\"," +
                    "\"latest_apk_version\":{\"value\":\"2.25.0\"}," +
                    "\"latest_ccz_version\":{\"value\":\"97\", \"force\":\"true\"}}";

    private static final String EMPTY_RESPONSE = "{}";

    @Before
    public void setup() {
        // The app version for this app is 95
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/form_entry_tests/profile.ccpr", "test", "123");
    }

    @Test
    public void testApkComparator() {
        ApkVersion version2x35x1 = new ApkVersion("2.35.1");
        ApkVersion version2x35x3 = new ApkVersion("2.35.3");
        ApkVersion version2x36 = new ApkVersion("2.36");
        ApkVersion version2x36x0 = new ApkVersion("2.36.0");

        assertTrue(version2x36.compareTo(version2x36x0) == 0);
        assertTrue(version2x35x3.compareTo(version2x36x0) < 0);
        assertTrue(version2x35x3.compareTo(version2x35x1) > 0);
    }

//    @Test
//    public void testHeartbeatForCorrectApp_needsCczUpdate() {
//        requestAndParseHeartbeat(RESPONSE_CorrectApp_CczUpdateNeeded);
//
//        UpdateToPrompt cczUpdate = UpdatePromptHelper.getCurrentUpdateToPrompt(UpdateToPrompt.Type.CCZ_UPDATE);
//        Assert.assertNotNull(cczUpdate);
//        Assert.assertTrue(cczUpdate.isNewerThanCurrentVersion());
//        Assert.assertEquals(97, cczUpdate.getCczVersion());
//        Assert.assertFalse(cczUpdate.isForced());
//
//        UpdateToPrompt apkUpdate = UpdatePromptHelper.getCurrentUpdateToPrompt(UpdateToPrompt.Type.APK_UPDATE);
//        Assert.assertNull(apkUpdate);
//    }
//
//    @Test
//    public void testHeartbeatForCorrectApp_needsCczUpdateWithForce() {
//        requestAndParseHeartbeat(RESPONSE_CorrectApp_CczUpdateNeeded_WithForce);
//
//        UpdateToPrompt cczUpdate = UpdatePromptHelper.getCurrentUpdateToPrompt(UpdateToPrompt.Type.CCZ_UPDATE);
//        Assert.assertNotNull(cczUpdate);
//        Assert.assertTrue(cczUpdate.isNewerThanCurrentVersion());
//        Assert.assertEquals(97, cczUpdate.getCczVersion());
//        Assert.assertTrue(cczUpdate.isForced());
//
//        UpdateToPrompt apkUpdate = UpdatePromptHelper.getCurrentUpdateToPrompt(UpdateToPrompt.Type.APK_UPDATE);
//        Assert.assertNull(apkUpdate);
//    }
//
//    @Test
//    public void testHeartbeatForCorrectApp_updateNotNeeded() {
//        requestAndParseHeartbeat(RESPONSE_CorrectApp_NoUpdateNeeded);
//
//        UpdateToPrompt cczUpdate = UpdatePromptHelper.getCurrentUpdateToPrompt(UpdateToPrompt.Type.CCZ_UPDATE);
//        Assert.assertNull(cczUpdate);
//
//        UpdateToPrompt apkUpdate = UpdatePromptHelper.getCurrentUpdateToPrompt(UpdateToPrompt.Type.APK_UPDATE);
//        Assert.assertNull(apkUpdate);
//    }

    @Test
    public void testHeartbeatForWrongApp() {
        requestAndParseHeartbeat(RESPONSE_WrongApp_CczUpdateNeeded);

        UpdateToPrompt cczUpdate = UpdatePromptHelper.getCurrentUpdateToPrompt(UpdateToPrompt.Type.CCZ_UPDATE);
        Assert.assertNull(cczUpdate);

        UpdateToPrompt apkUpdate = UpdatePromptHelper.getCurrentUpdateToPrompt(UpdateToPrompt.Type.APK_UPDATE);
        Assert.assertNull(apkUpdate);
    }

    @Test
    public void testTriggerAfterFormEntry() {
        requestAndParseHeartbeat(
                new String[]{EMPTY_RESPONSE, RESPONSE_CorrectApp_CczUpdateNeeded});

        UpdateToPrompt cczUpdate = UpdatePromptHelper.getCurrentUpdateToPrompt(UpdateToPrompt.Type.CCZ_UPDATE);
        Assert.assertNull(cczUpdate);

        fakeSuccessfulFormSendToTriggerHeartbeatRequest();
        waitForHeartbeatParsing();

        cczUpdate = UpdatePromptHelper.getCurrentUpdateToPrompt(UpdateToPrompt.Type.CCZ_UPDATE);
        Assert.assertNotNull(cczUpdate);
        Assert.assertTrue(cczUpdate.isNewerThanCurrentVersion());
        Assert.assertEquals(97, cczUpdate.getCczVersion());
    }

    private static void fakeSuccessfulFormSendToTriggerHeartbeatRequest() {
        StandardHomeActivity homeActivity =
                Robolectric.buildActivity(StandardHomeActivity.class).create().get();
        homeActivity.setFormAndDataSyncer(new FormAndDataSyncerFake());
        homeActivity.handleFormSendResult("Fake message", true);
    }

    private static void requestAndParseHeartbeat(String responseStringToUse) {
        requestAndParseHeartbeat(new String[]{responseStringToUse});
    }

    private static void requestAndParseHeartbeat(String[] responseStringsToUse) {
        TestHeartbeatRequester.setNextResponseStrings(responseStringsToUse);
        CommCareApplication.instance().getSession().initHeartbeatLifecycle();
        waitForHeartbeatParsing();
    }

    private static void waitForHeartbeatParsing() {
        TestHeartbeatRequester.responseWasParsed = false;
        long waitTimeStart = System.currentTimeMillis();
        while (!TestHeartbeatRequester.responseWasParsed) {
            if (System.currentTimeMillis() - waitTimeStart > 5000) {
                Assert.fail("Taking too long to parse the test heartbeat response");
            } else {
                // do not delete this print statement; for some reason that I haven't figured out,
                // the while loop never exits if this isn't here...
                System.out.println("Waiting for the test heartbeat response to be parsed");
            }
        }
    }

    @Test
    public void testShowHistoryLogic() {
        requestAndParseHeartbeat(
                new String[]{RESPONSE_CorrectApp_CczUpdateNeeded});
        UpdateToPrompt cczUpdate = UpdatePromptHelper.getCurrentUpdateToPrompt(UpdateToPrompt.Type.CCZ_UPDATE);
        Assert.assertNotNull(cczUpdate);

        for (int i = 0; i < UpdateToPrompt.NUM_VIEWS_BEFORE_REDUCING_FREQ_DEFAULT_VALUE; i++) {
            Assert.assertTrue(cczUpdate.shouldShowOnThisLogin());
            cczUpdate.incrementTimesSeen();
        }

        for (int i = 0; i < UpdateToPrompt.REDUCED_SHOW_FREQ_DEFAULT_VALUE-1; i++) {
            Assert.assertFalse(cczUpdate.shouldShowOnThisLogin());
            cczUpdate.incrementTimesSeen();
        }
        Assert.assertTrue(cczUpdate.shouldShowOnThisLogin());
        for (int i = 0; i < UpdateToPrompt.REDUCED_SHOW_FREQ_DEFAULT_VALUE-1; i++) {
            Assert.assertFalse(cczUpdate.shouldShowOnThisLogin());
            cczUpdate.incrementTimesSeen();
        }
        Assert.assertTrue(cczUpdate.shouldShowOnThisLogin());
    }

}
