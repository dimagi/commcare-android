package org.commcare.android.tests;

import android.app.Activity;
import android.content.Intent;

import org.commcare.CommCareTestApplication;
import org.commcare.activities.QueryRequestActivity;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.UpdateUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.engine.resource.AppInstallStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class DemoUserRestoreTest {
    private final static String REF_BASE_DIR = "jr://resource/commcare-apps/demo_user_restore/";

    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                REF_BASE_DIR + "base_app/profile.ccpr",
                "test", "123");
    }

    @Test
    public void launchQueryActivityAtWrongTimeTest() {
        Intent queryActivityIntent =
                new Intent(RuntimeEnvironment.application, QueryRequestActivity.class);
        QueryRequestActivity queryRequestActivity =
                Robolectric.buildActivity(QueryRequestActivity.class)
                        .withIntent(queryActivityIntent).setup().get();

        assertEquals(Activity.RESULT_CANCELED,
                Shadows.shadowOf(queryRequestActivity).getResultCode());
        assertTrue(queryRequestActivity.isFinishing());
    }

    @Test
    public void testDemoUserWipeOnUpdate() {
        String profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR, "update_user_restore", "profile.ccpr");
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.UpdateStaged,
                AppInstallStatus.Installed);

        // TODO
    }
}
