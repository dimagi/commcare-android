package org.commcare.android.tests.activities;

import android.content.Intent;

import org.commcare.CommCareTestApplication;
import org.commcare.activities.MultimediaInflaterActivity;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class MultimediaInflaterActivityTest {

    @Before
    public void setup() {
        TestAppInstaller.initInstallAndLogin(
                "jr://resource/commcare-apps/archive_form_tests/profile.ccpr",
                "test",
                "123");
    }

    @Test
    public void emptyFileSelectionTest() {
        MultimediaInflaterActivity multimediaInflaterActivity =
                Robolectric.buildActivity(MultimediaInflaterActivity.class).create().start().resume().get();
        ShadowActivity shadowActivity = Shadows.shadowOf(multimediaInflaterActivity);

        Intent fileSelectIntent = new Intent(Intent.ACTION_GET_CONTENT);
        // only allow look for zip files
        fileSelectIntent.setType("application/zip");

        shadowActivity.receiveResult(fileSelectIntent,
                MultimediaInflaterActivity.REQUEST_FILE_LOCATION,
                new Intent());
    }
}
