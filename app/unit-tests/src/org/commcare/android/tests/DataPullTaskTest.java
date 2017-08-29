package org.commcare.android.tests;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.network.CommcareRequestEndpointsMock;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.network.LocalReferencePullResponseFactory;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ResultAndError;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Coverage for different DataPullTask codepaths.
 * Doesn't yet check logical correctness of any actions.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class)
@RunWith(CommCareTestRunner.class)
public class DataPullTaskTest {
    private final static String APP_BASE = "jr://resource/commcare-apps/form_nav_tests/";
    private final static String GOOD_RESTORE = APP_BASE + "simple_data_restore.xml";
    private final static String BAD_RESTORE_XML = APP_BASE + "bad_xml_data_restore.xml";
    private final static String SELF_INDEXING_CASE_RESTORE = APP_BASE + "self_indexing_case_data_restore.xml";
    private final static String RETRY_RESPONSE = APP_BASE + "async_restore_response.xml";

    /**
     * Stores the result of the data pull task
     */
    private static ResultAndError<DataPullTask.PullTaskResult> dataPullResult;
    private static DataPullTask pullTask;

    @Test
    public void dataPullWithMissingRemoteKeyRecordTest() {
        TestAppInstaller.installApp(APP_BASE + "profile.ccpr");
        runDataPull(200, GOOD_RESTORE);
        Assert.assertEquals(DataPullTask.PullTaskResult.UNKNOWN_FAILURE, dataPullResult.data);
        Assert.assertEquals("Unable to get or generate encryption key", dataPullResult.errorMessage);
    }

    @Test
    public void dataPullWithLocalKeysTest() {
        installAndUseLocalKeys();
        runDataPull(200, GOOD_RESTORE);
    }

    @Test
    public void dataPullServerErrorTest() {
        installAndUseLocalKeys();
        runDataPull(500, GOOD_RESTORE);
        Assert.assertEquals(DataPullTask.PullTaskResult.SERVER_ERROR, dataPullResult.data);
    }

    @Test
    public void dataPullAuthFailedTest() {
        installAndUseLocalKeys();
        runDataPull(401, GOOD_RESTORE);
        Assert.assertEquals(DataPullTask.PullTaskResult.AUTH_FAILED, dataPullResult.data);
    }

    @Test
    public void dataPullRecoverTest() {
        installLoginAndUseLocalKeys();
        runDataPull(new Integer[]{412, 200}, new String[]{GOOD_RESTORE, GOOD_RESTORE});
        Assert.assertEquals(DataPullTask.PullTaskResult.DOWNLOAD_SUCCESS, dataPullResult.data);
    }

    @Test
    public void dataPullRecoverFailTest() {
        installLoginAndUseLocalKeys();
        runDataPull(new Integer[]{412, 500}, new String[]{GOOD_RESTORE, GOOD_RESTORE});
        Assert.assertEquals(DataPullTask.PullTaskResult.UNKNOWN_FAILURE, dataPullResult.data);
    }

    @Test
    public void dataPullRecoverWithRetryTest() {
        installLoginAndUseLocalKeys();
        runDataPull(new Integer[]{412, 202, 200}, new String[]{GOOD_RESTORE, RETRY_RESPONSE, GOOD_RESTORE});
        Assert.assertEquals(DataPullTask.PullTaskResult.DOWNLOAD_SUCCESS, dataPullResult.data);
    }

    @Test
    public void dataPullFailWithMessage() {
        installLoginAndUseLocalKeys();
        CommcareRequestEndpointsMock.setErrorResponseBody("{\"error\": \"some.fake.locale.key\", \"default_response\": \"hello world\"}");
        runDataPull(406, GOOD_RESTORE);
        Assert.assertEquals(DataPullTask.PullTaskResult.ACTIONABLE_FAILURE, dataPullResult.data);
        Assert.assertEquals("hello world", dataPullResult.errorMessage);
    }

    @Test
    public void dataPullRecoverFailLoginNeededTest() {
        installWithUserAndUseLocalKeys();
        runDataPull(new Integer[]{412, 500}, new String[]{GOOD_RESTORE, GOOD_RESTORE});
        Assert.assertEquals(DataPullTask.PullTaskResult.UNKNOWN_FAILURE, dataPullResult.data);
    }

    @Test
    public void dataPullBadRecoverPayloadTest() {
        installLoginAndUseLocalKeys();
        runDataPull(new Integer[]{412, 200}, new String[]{GOOD_RESTORE, BAD_RESTORE_XML});
        Assert.assertEquals(DataPullTask.PullTaskResult.UNKNOWN_FAILURE, dataPullResult.data);
    }

    @Test
    public void dataPullBadRestoreXMLTest() {
        installAndUseLocalKeys();
        runDataPull(200, BAD_RESTORE_XML);
        Assert.assertEquals(DataPullTask.PullTaskResult.BAD_DATA, dataPullResult.data);
    }

    @Test
    public void dataPullSelfIndexingCaseTest() {
        installAndUseLocalKeys();
        runDataPull(200, SELF_INDEXING_CASE_RESTORE);
        Assert.assertEquals(DataPullTask.PullTaskResult.BAD_DATA_REQUIRES_INTERVENTION, dataPullResult.data);
    }

    @Test
    public void asyncRestoreTest() {
        installAndUseLocalKeys();
        int initialCount = LocalReferencePullResponseFactory.getNumRequestsMade();
        runDataPullWithAsyncRestore();

        Assert.assertEquals(DataPullTask.PullTaskResult.DOWNLOAD_SUCCESS, dataPullResult.data);
        
        // Indicates that the task executed all of the retries we indicated, and then successfully
        // parsed the final success response
        Assert.assertEquals(4, LocalReferencePullResponseFactory.getNumRequestsMade() - initialCount);

        // Indicates that the mock retry result was parsed correctly
        Assert.assertTrue(pullTask.getAsyncRestoreHelper().serverProgressCompletedSoFar == 55);
    }

    private static void runDataPullWithAsyncRestore() {
        runDataPull(new Integer[]{202, 202, 202, 200},
                new String[]{RETRY_RESPONSE, RETRY_RESPONSE, RETRY_RESPONSE, GOOD_RESTORE});
    }

    private static void runDataPull(Integer resultCode, String payloadResource) {
        runDataPull(new Integer[]{resultCode}, new String[]{payloadResource});
    }

    private static void runDataPull(Integer[] resultCodes, String[] payloadResources) {
        CommcareRequestEndpointsMock.setCaseFetchResponseCodes(resultCodes);
        LocalReferencePullResponseFactory.setRequestPayloads(payloadResources);

        DataPullTask<Object> task =
                new DataPullTask<Object>("test", "123", null, "fake.server.com", RuntimeEnvironment.application, LocalReferencePullResponseFactory.INSTANCE, false) {
                    @Override
                    protected void deliverResult(Object o, ResultAndError<PullTaskResult> pullTaskResultResultAndError) {
                        dataPullResult = pullTaskResultResultAndError;
                    }

                    @Override
                    protected void deliverUpdate(Object o, Integer... update) {

                    }

                    @Override
                    protected void deliverError(Object o, Exception e) {

                    }
                };

        task.connect(TestAppInstaller.fakeConnector);
        task.execute();
        pullTask = task;

        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();
    }

    private void installAndUseLocalKeys() {
        TestAppInstaller.installApp(APP_BASE + "profile.ccpr");
        useLocalKeys();
    }

    private void installLoginAndUseLocalKeys() {
        TestAppInstaller.installAppAndLogin(APP_BASE + "profile.ccpr", "test", "123");
        useLocalKeys();
    }

    private void installWithUserAndUseLocalKeys() {
        TestAppInstaller.installAppAndUser(APP_BASE + "profile.ccpr", "test", "123");
        useLocalKeys();
    }

    private static void useLocalKeys() {
        CommCareApp app = CommCareApplication.instance().getCurrentApp();
        app.getAppPreferences().edit().putString("key_server", null).commit();
    }
}
