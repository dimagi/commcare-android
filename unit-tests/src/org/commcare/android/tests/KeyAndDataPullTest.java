package org.commcare.android.tests;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.mocks.HttpRequestEndpointsMock;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.network.DebugDataPullResponseFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class KeyAndDataPullTest {
    private final static String APP_BASE = "jr://resource/commcare-apps/form_nav_tests/";

    @Before
    public void setup() {
        TestAppInstaller.initAndInstall(APP_BASE + "profile.ccpr");
    }

    @Test
    public void initialDataPullTest() {
        //runDataPull();
    }

    private static void runDataPull() {
        HttpRequestEndpointsMock.caseFetchResponseCode = 200;

        DebugDataPullResponseFactory dataPullRequestor =
                new DebugDataPullResponseFactory(APP_BASE + "simple_data_restore.xml");
        DataPullTask<Object> task =
                new DataPullTask<Object>("test", "123", "fake.server.com", RuntimeEnvironment.application, dataPullRequestor) {
                    @Override
                    protected void deliverResult(Object o, ResultAndError<PullTaskResult> pullTaskResultResultAndError) {

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

        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();
    }

    @Test
    public void dataPullWithLocalKeys() {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        app.getAppPreferences().edit().putString("key_server", null).commit();
        runDataPull();
    }
}
