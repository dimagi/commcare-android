package org.commcare.android.tests.application;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.suite.model.Profile;
import org.commcare.update.UpdateHelper;
import org.commcare.utils.PendingCalcs;
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
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
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
        Assert.assertFalse(UpdateHelper.isAutoUpdateOn());

        Profile p = CommCareApplication.instance().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 8);
    }
}
