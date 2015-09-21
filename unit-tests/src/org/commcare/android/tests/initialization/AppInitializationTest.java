package org.commcare.android.tests.initialization;

import android.app.Application;

import org.commcare.android.CommCareTestRunner;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.mocks.CommCareTaskConnectorFake;
import org.commcare.android.tasks.ResourceEngineTask;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.application.CommCareApp;
import org.javarosa.core.util.PropertyUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = org.commcare.dalvik.application.CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class AppInitializationTest {
    @Before
    public void setup() {
        Robolectric.getBackgroundThreadScheduler().pause();
        Robolectric.getForegroundThreadScheduler().pause();
    }

    @Test
    public void installApp() {
        // Robolectric.getBackgroundScheduler().pause();
        // Robolectric.getUiThreadScheduler().pause();

        String filepath = "resources/commcare-apps/flipper/profile.ccpr";
        String cczFilePath =
            ("jr://resource/" + filepath);
        ApplicationRecord newRecord =
                new ApplicationRecord(PropertyUtils.genUUID().replace("-", ""),
                        ApplicationRecord.STATUS_UNINITIALIZED);

        CommCareApp app = new CommCareApp(newRecord);
        ResourceEngineTask<Object> task =
                new ResourceEngineTask<Object>(false, app, false, -1, false) {

                    @Override
                    protected void deliverResult(Object receiver,
                                                 ResourceEngineOutcomes result) {
                        System.out.print("result");
                    }

                    @Override
                    protected void deliverUpdate(Object receiver,
                                                 int[]... update) {
                        System.out.print("update");
                    }

                    @Override
                    protected void deliverError(Object receiver,
                                                Exception e) {
                        System.out.print("error");
                    }
                };
        task.connect(new CommCareTaskConnectorFake<>());
        task.execute(cczFilePath);
        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();
        ShadowApplication.runBackgroundTasks();
        ShadowLooper.runUiThreadTasks();
        System.out.print("bar");
    }

    @Test
    public void testAppInit() {
        Application a = RuntimeEnvironment.application;
        System.out.print(a.getPackageName());
        //initFirstUsableAppRecord();
    }
}
