package org.commcare.android.util;

import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.user.DemoUserBuilder;
import org.commcare.android.mocks.CommCareTaskConnectorFake;
import org.commcare.android.resource.AppInstallStatus;
import org.commcare.android.tasks.ResourceEngineTask;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.application.CommCareTestApp;
import org.commcare.dalvik.services.CommCareSessionService;
import org.javarosa.core.model.User;
import org.javarosa.core.util.PropertyUtils;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;

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

    private final CommCareTaskConnectorFake<Object> fakeConnector =
            new CommCareTaskConnectorFake<>();

    public TestAppInstaller(String resourceFilepath,
                            String username,
                            String password) {
        this.resourceFilepath = resourceFilepath;
        this.username = username;
        this.password = password;
    }

    public void installAppAndLogin() {
        installApp();

        buildTestUser();

        login(username, password);
    }

    private void installApp() {
        ApplicationRecord newRecord =
                new ApplicationRecord(PropertyUtils.genUUID().replace("-", ""),
                        ApplicationRecord.STATUS_UNINITIALIZED);

        CommCareApp app = new CommCareTestApp(new CommCareApp(newRecord));
        ResourceEngineTask<Object> task =
                new ResourceEngineTask<Object>(app, -1, false) {
                    @Override
                    protected void deliverResult(Object receiver,
                                                 AppInstallStatus result) {
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
        task.execute(resourceFilepath);

        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();
    }

    private void buildTestUser() {
        CommCareApp ccApp = CommCareApplication._().getCurrentApp();
        DemoUserBuilder.buildTestUser(RuntimeEnvironment.application,
                ccApp,
                username, password);
    }

    public static void login(String username, String password) {
        CommCareApp ccApp = CommCareApplication._().getCurrentApp();
        UserKeyRecord keyRecord =
                UserKeyRecord.getCurrentValidRecordByPassword(ccApp, username, password, true);
        startSessionService(keyRecord, password);
    }

    private static void startSessionService(UserKeyRecord keyRecord, String password) {
        // manually create/setup session service because robolectric doesn't
        // really support services
        CommCareSessionService ccService = new CommCareSessionService();
        ccService.createCipherPool();
        ccService.prepareStorage(keyRecord.unWrapKey(password), keyRecord);
        ccService.startSession(getUserFromDb(ccService, keyRecord), keyRecord);

        CommCareApplication._().setTestingService(ccService);
    }

    private static User getUserFromDb(CommCareSessionService ccService, UserKeyRecord keyRecord) {
        for (User u : CommCareApplication._().getRawStorage("USER", User.class, ccService.getUserDbHandle())) {
            if (keyRecord.getUsername().equals(u.getUsername())) {
                return u;
            }
        }
        return null;
    }

    public static void setupPrototypeFactory() {
        // Sets DB to use an in-memory store for class serialization tagging.
        // This avoids the need to use apk reflection to perform read/writes
        TestUtils.initializeStaticTestStorage();
    }
}
