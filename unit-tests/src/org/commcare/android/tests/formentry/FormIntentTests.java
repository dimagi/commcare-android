package org.commcare.android.tests.formentry;

import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import android.widget.ImageButton;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.models.database.SqlStorage;
import org.commcare.views.QuestionsView;
import org.commcare.views.widgets.IntegerWidget;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowEnvironment;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.assertNull;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class FormIntentTests {

    private static final String TAG = FormIntentTests.class.getSimpleName();

    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/form_entry_tests/profile.ccpr",
                "test", "123");
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
    }

    /**
     * Test different behaviors for possibly grouped intent callout views
     */
    @Test
    public void testIntentCalloutAggregation() {
        ShadowActivity shadowActivity =
                ActivityLaunchUtils.buildHomeActivityForFormEntryLaunch("m0-f0");

        Intent formEntryIntent = shadowActivity.getNextStartedActivity();

        // make sure the form entry activity should be launched
        String intentActivityName = formEntryIntent.getComponent().getClassName();
        assertTrue(intentActivityName.equals(FormEntryActivity.class.getName()));

        navigateFormStructure(formEntryIntent);
    }

    private void navigateFormStructure(Intent formEntryIntent) {
        // launch form entry
        FormEntryActivity formEntryActivity =
                Robolectric.buildActivity(FormEntryActivity.class).withIntent(formEntryIntent)
                        .create().start().resume().get();

        ImageButton nextButton = (ImageButton)formEntryActivity.findViewById(R.id.nav_btn_next);

        testStandaloneIntent(formEntryActivity);

        nextButton.performClick();

        testMultipleIntent();
    }

    private void testStandaloneIntent(FormEntryActivity formEntryActivity) {
        assertEquals("Didn't display correct widget", formEntryActivity.getODKView().getWidgets().get(0).getPrompt().getQuestionText(), "callout_single");
        Intent callout = formEntryActivity.getODKView().getAggregateIntentCallout();
        assertNull("incorrectly aggregated intent callout", callout);
    }

    private void testMultipleIntent() {

    }

}
