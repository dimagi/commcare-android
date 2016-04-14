package org.commcare.android.tests.application;

import org.commcare.CommCareApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.TestResourceEngineTaskListener;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.engine.resource.AppInstallStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Created by amstone326 on 4/14/16.
 */
@Config(application = CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class MultipleAppsCompatibilityTests {

    TestResourceEngineTaskListener listener;
    TestAppInstaller disabledAppInstaller;
    TestAppInstaller ignoreAppInstaller;
    TestAppInstaller enabledApp1Installer;
    TestAppInstaller enabledApp2Installer;

    private final static String REF_BASE_DIR = "jr://resource/commcare-apps/multiple_apps_tests/";


    @Before
    public void setup() {
        listener = new TestResourceEngineTaskListener();

        disabledAppInstaller =
                new TestAppInstaller(buildResourceRef("app_with_disabled_value", "profile.ccpr"),
                        "test", "123");

        ignoreAppInstaller =
                new TestAppInstaller(buildResourceRef("app_with_ignore_value", "profile.ccpr"),
                        "test", "123");

        enabledApp1Installer =
                new TestAppInstaller(buildResourceRef("app_with_enabled_value", "profile.ccpr"),
                        "test", "123");

        enabledApp2Installer =
                new TestAppInstaller(buildResourceRef("app_with_enabled_value_2", "profile.ccpr"),
                        "test", "123");

    }

    private static String buildResourceRef(String app, String resource) {
        return REF_BASE_DIR + app + "/" + resource;
    }

    private static AppInstallStatus installAndWaitForResult(TestAppInstaller installer,
                                                            TestResourceEngineTaskListener listener) {
        installer.installAppWithReceiver(listener);
        while (!listener.taskCompleted()) {

        }
        return listener.getResult();
    }

    @Test
    public void testInstallWithOneDisabledPresent() {
        AppInstallStatus installResult = installAndWaitForResult(disabledAppInstaller, listener);
        Assert.assertEquals(AppInstallStatus.Installed, installResult);
    }


}
