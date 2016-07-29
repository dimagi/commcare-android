package org.commcare.android.tests.formentry;

import android.content.Intent;
import android.os.Environment;
import android.view.View;
import android.widget.ImageButton;

import org.commcare.CommCareTestApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.utils.CompoundIntentList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowEnvironment;

import java.util.ArrayList;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.assertNull;

/**
 * @author Clayton Sims
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class FormIntentTests {

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

        testMultipleIntent(formEntryActivity);

        nextButton.performClick();

        testMixedIntents(formEntryActivity);
    }

    private void testStandaloneIntent(FormEntryActivity formEntryActivity) {
        CompoundIntentList callout = formEntryActivity.getODKView().getAggregateIntentCallout();
        assertNull("incorrectly aggregated intent callout", callout);
        assertEquals("Dispatch button visibility", View.GONE, formEntryActivity.findViewById(R.id.multiple_intent_dispatch_button).getVisibility());
    }

    private void testMultipleIntent(FormEntryActivity formEntryActivity) {
        CompoundIntentList callout = formEntryActivity.getODKView().getAggregateIntentCallout();

        assertEquals("Incorrect number of callout aggregations", 3, callout.getNumberOfCallouts());

        Intent compoundIntentObject = callout.getCompoundedIntent();
        String action = compoundIntentObject.getAction();
        ArrayList<String> indices =
                compoundIntentObject.getStringArrayListExtra(CompoundIntentList.EXTRA_COMPOUND_DATA_INDICES);

        assertEquals("Incorreclty aggregated callout action", "org.commcare.dalvik.action.PRINT", action);

        String testIndex = "1,1_0,0";

        assertTrue("Compound index set missing element: " + testIndex, indices.contains(testIndex));

        String contextualizedBundleValue = compoundIntentObject.getBundleExtra(testIndex).getString("contextualized_value");

        assertEquals("Contextualized bundle value reference", "1", contextualizedBundleValue);
        assertEquals("Dispatch button visibility", View.VISIBLE, formEntryActivity.findViewById(R.id.multiple_intent_dispatch_button).getVisibility());
    }

    private void testMixedIntents(FormEntryActivity formEntryActivity) {
        CompoundIntentList callout = formEntryActivity.getODKView().getAggregateIntentCallout();
        assertNull("Should not have aggregated mixed intents", callout);
        assertEquals("Dispatch button visibility", View.GONE, formEntryActivity.findViewById(R.id.multiple_intent_dispatch_button).getVisibility());
    }
}
