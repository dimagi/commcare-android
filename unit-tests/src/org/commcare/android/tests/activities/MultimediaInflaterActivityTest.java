package org.commcare.android.tests.activities;

import android.app.Activity;
import android.content.Intent;
import android.widget.ImageButton;

import org.commcare.CommCareTestApplication;
import org.commcare.activities.MultimediaInflaterActivity;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowToast;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class MultimediaInflaterActivityTest {

    @Before
    public void setup() {
        TestAppInstaller.installApp("jr://resource/commcare-apps/archive_form_tests/profile.ccpr");
    }

    /**
     * Ensure user sees invalid path toast when the file browser doesn't return a path uri
     */
    @Test
    public void emptyFileSelectionTest() {
        MultimediaInflaterActivity multimediaInflaterActivity =
                Robolectric.buildActivity(MultimediaInflaterActivity.class)
                        .create().start().resume().get();

        ImageButton selectFileButton =
                (ImageButton)multimediaInflaterActivity.findViewById(
                        R.id.screen_multimedia_inflater_filefetch);
        selectFileButton.performClick();

        Intent fileSelectIntent = new Intent(Intent.ACTION_GET_CONTENT);
        fileSelectIntent.setType("application/zip");

        Intent emptyFileSelectResult = new Intent();

        ShadowActivity shadowActivity =
                Shadows.shadowOf(multimediaInflaterActivity);
        shadowActivity.receiveResult(fileSelectIntent,
                Activity.RESULT_OK,
                emptyFileSelectResult);

        Assert.assertEquals(Localization.get("mult.install.state.invalid.path"),
                ShadowToast.getTextOfLatestToast());
    }
}
