package org.commcare.android.tests.caselist;

import org.commcare.CommCareApplication;
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
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class EntityListCalloutDataTest {
    private final static String TAG = EntityListCalloutDataTest.class.getSimpleName();

    @Before
    public void setup() {
        TestAppInstaller.initInstallAndLogin("jr://resource/commcare-apps/case_list_lookup/profile.ccpr", "test", "123");

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 6);
    }

    private String buildResourceRef(String app, String resource) {
        return REF_BASE_DIR + app + "/" + resource;
    }

    @Test
    public void testAppUpdate() {
    }
}
