package org.commcare.android.util;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApp;
import org.commcare.CommCareTestApplication;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.mocks.CommCareTaskConnectorFake;
import org.commcare.core.parse.ParseUtils;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.models.database.user.DemoUserBuilder;
import org.commcare.tasks.ResourceEngineTask;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.ResourceReferenceFactory;
import org.javarosa.core.util.PropertyUtils;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.ExecutionException;

import androidx.test.core.app.ApplicationProvider;

import static android.os.Looper.getMainLooper;

/**
 * Functionality to install an app from local storage, create a test user, log
 * into a user session
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class TestAppInstaller {
    private final String username;
    private final String password;
    private final String resourceFilepath;

    public final static CommCareTaskConnectorFake<Object> fakeConnector =
            new CommCareTaskConnectorFake<>();

    private TestAppInstaller(String resourceFilepath,
                             String username,
                             String password) {
        this.resourceFilepath = resourceFilepath;
        this.username = username;
        this.password = password;
    }

    /**
     * Install an app and create / login with a (test) user
     */
    public static void installAppAndLogin(String appPath,
                                          String username,
                                          String password) {
        installAppAndUser(appPath, username, password);
        login(username, password);
    }

    /**
     * Install an app without creating a user
     */
    public static void installApp(String appPath) {
        storageSetup();
        TestAppInstaller appTestInstaller =
                new TestAppInstaller(appPath, null, null);
        appTestInstaller.installApp();
    }

    /**
     * Install an app with creating a user
     */
    public static void installAppAndUser(String appPath,
                                         String username,
                                         String password) {
        storageSetup();
        TestAppInstaller appTestInstaller =
                new TestAppInstaller(
                        appPath, username, password);
        appTestInstaller.installApp();
        appTestInstaller.buildTestUser();
    }

    private static void storageSetup() {
        // needed to resolve "jr://resource" type references
        ReferenceManager.instance().addReferenceFactory(new ResourceReferenceFactory());
    }

    private void installApp() {
        ApplicationRecord newRecord =
                new ApplicationRecord(PropertyUtils.genUUID().replace("-", ""),
                        ApplicationRecord.STATUS_UNINITIALIZED);


        CommCareApp app = new CommCareTestApp(new CommCareApp(newRecord));

        ResourceEngineTask<Object> task =
                new ResourceEngineTask<Object>(app, -1, false, false) {
                    @Override
                    protected void deliverResult(Object receiver,
                                                 AppInstallStatus result) {
                        app.setMMResourcesValidated();
                    }

                    @Override
                    protected void deliverUpdate(Object receiver,
                                                 int[]... update) {
                    }

                    @Override
                    protected void deliverError(Object receiver,
                                                Exception e) {
                        throw new RuntimeException("App failed to install during test");
                    }
                };
        task.connect(fakeConnector);
        try {
            task.execute(resourceFilepath).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException("Install failed due to " + e.getMessage(), e);
        }
        Shadows.shadowOf(getMainLooper()).idle();
    }

    private void buildTestUser() {
        CommCareApp ccApp = CommCareApplication.instance().getCurrentApp();
        DemoUserBuilder.buildTestUser(ApplicationProvider.getApplicationContext(),
                ccApp,
                username, password);
    }

    public static void login(String username, String password) {
        CommCareApp ccApp = CommCareApplication.instance().getCurrentApp();
        ((CommCareTestApplication)CommCareApplication.instance()).setCachedUserPassword(password);
        UserKeyRecord keyRecord =
                UserKeyRecord.getCurrentValidRecordByPassword(ccApp, username, password, true);
        CommCareApplication.instance().startUserSession(keyRecord.unWrapKey(password), keyRecord, false);
    }
}
