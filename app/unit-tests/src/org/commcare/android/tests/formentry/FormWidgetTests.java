package org.commcare.android.tests.formentry;

import android.content.Intent;
import android.view.View;
import android.widget.ImageButton;

import org.commcare.CommCareTestApplication;
import org.commcare.activities.FormEntryActivity;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.R;
import org.commcare.utils.CompoundIntentList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import java.util.ArrayList;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

/**
 * @author Clayton Sims
 */
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class FormWidgetTests {

    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/form_entry_widgets/profile.ccpr",
                "test", "123");
    }

    /**
     * Test different behaviors for possibly grouped intent callout views
     *
     * The "JavaRosa" XForms standard has some peculiar behaviors. Onscreen non-interactive
     * labels are currently implemented through <trigger> widgets for unfortunate historical
     * reasons. Due to incremental changes to the code, this means that the widget has for many
     * years returned a value, and that value is used regularly to control behavior.
     *
     * These tests reflect the common ways that "label" (trigger) question values are used
     * to ensure that future updates don't change these behaviors. If these tests fail,
     * please seek guidance before changing system behavior.
     */
    @Test
    public void testLabelQuestionWidgetBehavior() {
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
                Robolectric.buildActivity(FormEntryActivity.class, formEntryIntent)
                        .create().start().resume().get();

        //Screen 1
        ImageButton nextButton = formEntryActivity.findViewById(R.id.nav_btn_next);

        assertTrue(screenContainsText(formEntryActivity, "QUESTION1"));

        nextButton.performClick();

        assertTrue("Form failed to proceed with required <trigger> question>",
                screenContainsText(formEntryActivity, "QUESTION2"));

        nextButton.performClick();

        assertTrue("Form failed to halt at violated <trigger> constraint",
        screenContainsText(formEntryActivity, "QUESTION2"));

        assertTrue("Form failed to display <trigger> constraint",
        screenContainsText(formEntryActivity, "TESTPASS"));
    }

    private boolean screenContainsText(FormEntryActivity formEntryActivity, String textToCheck) {
        ArrayList list = new ArrayList<View>();
        formEntryActivity.getODKView().findViewsWithText(list, textToCheck, View.FIND_VIEWS_WITH_TEXT);

        return list.size() > 0;
    }
}
