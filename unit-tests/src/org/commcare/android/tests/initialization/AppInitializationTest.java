package org.commcare.android.tests.initialization;

import android.app.Application;

import org.commcare.android.CommCareTestRunner;
import org.commcare.android.database.DbUtil;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.mocks.CommCareTaskConnectorFake;
import org.commcare.android.tasks.DataPullTask;
import org.commcare.android.tasks.ResourceEngineTask;
import org.commcare.android.util.LivePrototypeFactory;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.application.CommCareApp;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.ResourceReferenceFactory;
import org.javarosa.core.util.PropertyUtils;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

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

        ReferenceManager._().addReferenceFactory(new ResourceReferenceFactory());

        // Sets DB to use an in-memory store for class serialization tagging.
        // This avoids the need to use apk reflection to perform read/writes
        LivePrototypeFactory prototypeFactory = new LivePrototypeFactory();
        PrototypeFactory.setStaticHasher(prototypeFactory);
        DbUtil.setDBUtilsPrototypeFactory(prototypeFactory);

        installApp();

        restoreUser();
    }

    private void installApp() {
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

        String filepath = "/commcare-apps/flipper/profile.ccpr";
        String resourceFilepath = "jr://resource" + filepath;
        task.execute(resourceFilepath);

        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();
    }

    private void restoreUser() {
        String username = "";
        String password = "";
        String restoreServer = "";
        Application appContext = RuntimeEnvironment.application;
        DataPullTask<Object> dataPuller =
                new DataPullTask<Object>(username, password, restoreServer, appContext) {
                    @Override
                    protected void deliverResult(Object receiver, Integer result) {
                    }

                    @Override
                    protected void deliverUpdate(Object receiver, Integer... update) {
                    }

                    @Override
                    protected void deliverError(Object receiver, Exception e) {
                    }
                };

        dataPuller.connect(new CommCareTaskConnectorFake<>());
        dataPuller.execute();
    }

    @Test
    public void verifyApp() {
    }
}
