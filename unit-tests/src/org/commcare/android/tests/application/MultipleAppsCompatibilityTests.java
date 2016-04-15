package org.commcare.android.tests.application;

import org.commcare.CommCareApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestResourceEngineTaskListener;
import org.commcare.dalvik.BuildConfig;
import org.commcare.engine.resource.AppInstallStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Tests for when installing multiple apps is and is not allowed
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
@Config(application = CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class MultipleAppsCompatibilityTests {

    String disabledAppPath;
    String ignoreAppPath;
    String enabledApp1Path;
    String enabledApp2Path;

    private final static String REF_BASE_DIR = "jr://resource/commcare-apps/multiple_apps_tests/";

    @Before
    public void setup() {
        disabledAppPath = buildResourceRef("app_with_disabled_value", "profile.ccpr");
        ignoreAppPath = buildResourceRef("app_with_ignore_value", "profile.ccpr");
        enabledApp1Path = buildResourceRef("app_with_enabled_value", "profile.ccpr");
        enabledApp2Path = buildResourceRef("app_with_enabled_value_2", "profile.ccpr");
    }

    private static String buildResourceRef(String app, String resource) {
        return REF_BASE_DIR + app + "/" + resource;
    }

    private static AppInstallStatus installAndWaitForResult(String appPath) {
        TestResourceEngineTaskListener listener = new TestResourceEngineTaskListener();
        TestAppInstaller.initInstallAndLogin(appPath, "t1", "123", listener);
        while (!listener.taskCompleted()) {
        }
        return listener.getResult();
    }

    @Test
    public void testInstallEnabledWithEnabledPresent() {
        CommCareApplication._().disableSuperUserMode();

        AppInstallStatus installResult = installAndWaitForResult(enabledApp1Path);
        Assert.assertEquals(AppInstallStatus.Installed, installResult);

        installResult = installAndWaitForResult(enabledApp2Path);
        Assert.assertEquals(AppInstallStatus.Installed, installResult);
    }

    @Test
    public void testInstallEnabledWithIgnorePresent() {
        CommCareApplication._().disableSuperUserMode();

        AppInstallStatus installResult = installAndWaitForResult(ignoreAppPath);
        Assert.assertEquals(AppInstallStatus.Installed, installResult);

        installResult = installAndWaitForResult(enabledApp1Path);
        Assert.assertEquals(AppInstallStatus.Installed, installResult);
    }

    @Test
    public void testInstallDisabledWithIgnorePresent() {
        CommCareApplication._().disableSuperUserMode();

        AppInstallStatus installResult = installAndWaitForResult(ignoreAppPath);
        Assert.assertEquals(AppInstallStatus.Installed, installResult);

        installResult = installAndWaitForResult(disabledAppPath);
        Assert.assertEquals(AppInstallStatus.Installed, installResult);
    }

    @Test
    public void testInstallEnabledWithDisabledAndIgnorePresent() {
        CommCareApplication._().disableSuperUserMode();

        AppInstallStatus installResult = installAndWaitForResult(disabledAppPath);
        Assert.assertEquals(AppInstallStatus.Installed, installResult);

        installResult = installAndWaitForResult(ignoreAppPath);
        Assert.assertEquals(AppInstallStatus.Installed, installResult);

        installResult = installAndWaitForResult(enabledApp1Path);
        Assert.assertEquals(AppInstallStatus.MultipleAppsViolation_Existing, installResult);
    }

    @Test
    public void testInstallEnabledWithDisabledPresent() {
        CommCareApplication._().disableSuperUserMode();

        AppInstallStatus installResult = installAndWaitForResult(disabledAppPath);
        Assert.assertEquals(AppInstallStatus.Installed, installResult);

        installResult = installAndWaitForResult(enabledApp1Path);
        Assert.assertEquals(AppInstallStatus.MultipleAppsViolation_Existing, installResult);
    }

    @Test
    public void testInstallDisabledWithEnabledPresent() {
        CommCareApplication._().disableSuperUserMode();

        AppInstallStatus installResult = installAndWaitForResult(enabledApp1Path);
        Assert.assertEquals(AppInstallStatus.Installed, installResult);

        installResult = installAndWaitForResult(disabledAppPath);
        Assert.assertEquals(AppInstallStatus.MultipleAppsViolation_New, installResult);
    }

    @Test
    public void testInstallEnabledWithDisabledPresent_superuserMode() {
        CommCareApplication._().enableSuperUserMode("dummy_superuser");

        AppInstallStatus installResult = installAndWaitForResult(disabledAppPath);
        Assert.assertEquals(AppInstallStatus.Installed, installResult);

        installResult = installAndWaitForResult(enabledApp1Path);
        Assert.assertEquals(AppInstallStatus.Installed, installResult);

        installResult = installAndWaitForResult(enabledApp2Path);
        Assert.assertEquals(AppInstallStatus.Installed, installResult);
    }

    @Test
    public void testInstallDisabledWithEnabledPresent_superuserMode() {
        CommCareApplication._().enableSuperUserMode("dummy_superuser");

        AppInstallStatus installResult = installAndWaitForResult(enabledApp1Path);
        Assert.assertEquals(AppInstallStatus.Installed, installResult);

        installResult = installAndWaitForResult(disabledAppPath);
        Assert.assertEquals(AppInstallStatus.Installed, installResult);
    }

}
