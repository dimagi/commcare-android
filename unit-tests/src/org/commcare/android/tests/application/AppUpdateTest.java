package org.commcare.android.tests.application;

import android.util.Log;

import org.commcare.android.CommCareTestRunner;
import org.commcare.android.resource.AppInstallStatus;
import org.commcare.android.tasks.InstallStagedUpdateTask;
import org.commcare.android.tasks.TaskListener;
import org.commcare.android.tasks.TaskListenerRegistrationException;
import org.commcare.android.tasks.UpdateTask;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.models.database.global.models.ApplicationRecord;
import org.commcare.suite.model.Profile;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.ResourceReferenceFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

import java.io.File;

import static org.junit.Assert.fail;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = org.commcare.dalvik.application.CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class AppUpdateTest {
    private final static String TAG = AppUpdateTest.class.getSimpleName();
    private final static String REF_BASE_DIR = "jr://resource/commcare-apps/update_tests/";

    @Before
    public void setup() {
        // needed to resolve "jr://resource" type references
        ReferenceManager._().addReferenceFactory(new ResourceReferenceFactory());

        TestAppInstaller.setupPrototypeFactory();

        TestAppInstaller appTestInstaller =
                new TestAppInstaller(buildResourceRef("base_app", "profile.ccpr"),
                        "test", "123");
        appTestInstaller.installAppAndLogin();

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 6);
    }

    private String buildResourceRef(String app, String resource) {
        return REF_BASE_DIR + app + "/" + resource;
    }

    @Test
    public void testAppUpdate() {
        Log.d(TAG, "Applying a valid app update");

        installUpdate("valid_update",
                taskListenerFactory(AppInstallStatus.UpdateStaged),
                AppInstallStatus.Installed);

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 9);
    }

    @Test
    public void testAppIsUpToDate() {
        Log.d(TAG, "Try updating to the same app.");

        installUpdate("base_app",
                taskListenerFactory(AppInstallStatus.UpToDate),
                AppInstallStatus.UnknownFailure);

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 6);
    }

    @Test
    public void testAppUpdateWithoutLocalStorage() {
        Log.d(TAG, "Try updating after removing local filesystem temp dirs.");

        // nuke local folder that CommCare uses to stage updates.
        File dir = new File(CommCareApplication._().getAndroidFsTemp());
        Assert.assertTrue(dir.delete());

        installUpdate("valid_update",
                taskListenerFactory(AppInstallStatus.NoLocalStorage),
                AppInstallStatus.UnknownFailure);

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 6);
    }

    @Test
    public void testUpdateToBrokenApp() {
        Log.d(TAG, "Applying a broken app update");

        installUpdate("invalid_update",
                taskListenerFactory(AppInstallStatus.MissingResourcesWithMessage),
                AppInstallStatus.UnknownFailure);

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 6);
    }

    @Test
    public void testUpdateWithNoAppInstalled() {
        Log.d(TAG, "Update without installing an app first");

        ApplicationRecord appRecord = CommCareApplication._().getInstalledAppRecords().get(0);
        CommCareApplication._().uninstall(appRecord);
        installUpdate("invalid_update",
                taskListenerFactory(AppInstallStatus.UnknownFailure),
                AppInstallStatus.UnknownFailure);

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 6);
    }

    @Test
    public void testUpdateToAppWithMultimedia() {
        Log.d(TAG, "updating to an app that has multimedia present");

        installUpdate("valid_update_with_multimedia_present",
                taskListenerFactory(AppInstallStatus.UpdateStaged),
                AppInstallStatus.Installed);

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 14);
    }

    @Test
    public void testUpdateToAppMissingMultimedia() {
        Log.d(TAG, "updating to an app that has missing multimedia");

        installUpdate("valid_update_without_multimedia_present",
                taskListenerFactory(AppInstallStatus.MissingResources),
                AppInstallStatus.UnknownFailure);

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 6);
    }

    @Test
    public void testUpdateToAppWithIncompatibleVersion() {
        Log.d(TAG, "updating to an app that requires an newer CommCare version");

        installUpdate("invalid_version",
                taskListenerFactory(AppInstallStatus.IncompatibleReqs),
                AppInstallStatus.UnknownFailure);

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 6);
    }

    private void installUpdate(String appFolder,
                               TaskListener<Integer, AppInstallStatus> listener,
                               AppInstallStatus expectedInstallStatus) {
        UpdateTask updateTask = UpdateTask.getNewInstance();
        try {
            updateTask.registerTaskListener(listener);
        } catch (TaskListenerRegistrationException e) {
            fail("failed to register listener for update task");
        }
        updateTask.execute(buildResourceRef(appFolder, "profile.ccpr"));

        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();

        Assert.assertEquals(expectedInstallStatus,
                InstallStagedUpdateTask.installStagedUpdate());
        updateTask.clearTaskInstance();
    }

    private TaskListener<Integer, AppInstallStatus> taskListenerFactory(final AppInstallStatus expectedResult) {
        return new TaskListener<Integer, AppInstallStatus>() {
            @Override
            public void handleTaskUpdate(Integer... updateVals) {
            }

            @Override
            public void handleTaskCompletion(AppInstallStatus result) {
                Assert.assertTrue(result == expectedResult);
            }

            @Override
            public void handleTaskCancellation(AppInstallStatus result) {
            }
        };
    }
}
