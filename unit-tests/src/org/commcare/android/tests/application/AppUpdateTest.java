package org.commcare.android.tests.application;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */

import android.util.Log;

import org.commcare.android.CommCareTestRunner;
import org.commcare.android.tasks.InstallStagedUpdateTask;
import org.commcare.android.tasks.UpdateTask;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.Profile;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.ResourceReferenceFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

/**
 *
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

        installUpdate("valid_update");

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 9);
    }

    private void installUpdate(String app_folder) {
        UpdateTask updateTask = UpdateTask.getNewInstance();
        updateTask.execute(buildResourceRef(app_folder, "profile.ccpr"));

        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();

        InstallStagedUpdateTask.installStagedUpdate();
    }

    @Test
    public void testUpdateToBrokenApp() {
        Log.d(TAG, "Applying a broken app update");

        installUpdate("invalid_update");

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 7);
    }

    @Test
    public void testUpdateToAppWithMultimedia() {
        Log.d(TAG, "updating to an app that has multimedia present");

        installUpdate("valid_update_with_multimedia_present");

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 14);
    }

    @Test
    public void testUpdateToAppMissingMultimedia() {
        Log.d(TAG, "updating to an app that has missing multimedia");

        installUpdate("valid_update_without_multimedia_present");

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 14);
    }
}
