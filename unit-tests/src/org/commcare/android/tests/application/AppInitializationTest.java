package org.commcare.android.tests.application;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.suite.model.Profile;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Tests that use the ability to install a CommCare app and login as a test
 * user.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class AppInitializationTest {

    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/archive_form_tests/profile.ccpr",
                "test",
                "123");
    }

    @Test
    public void testAppInit() {
        Assert.assertFalse(CommCareApplication._().isUpdatePending());

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 8);
    }
}
