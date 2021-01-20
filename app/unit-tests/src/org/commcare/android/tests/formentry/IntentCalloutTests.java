package org.commcare.android.tests.formentry;

import android.app.Activity;
import android.content.Intent;
import android.widget.ImageButton;

import junit.framework.Assert;

import org.commcare.CommCareTestApplication;
import org.commcare.activities.FormEntryActivity;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.commcare.android.resource.installers.XFormAndroidInstaller;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.R;
import org.commcare.views.widgets.IntentWidget;
import org.commcare.views.widgets.StringNumberWidget;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/**
 * @author wpride
 */
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class IntentCalloutTests {

    @Before
    public void setup() {
        XFormAndroidInstaller.registerAndroidLevelFormParsers();
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/phone_call/profile.ccpr",
                "test", "123");
    }

    /**
     * Test different behaviors for possibly grouped intent callout views
     */
    @Test
    public void testIntentCalloutWithDataXPath() {
        FormEntryActivity formEntryActivity = ActivityLaunchUtils.launchFormEntry("m0-f0");
        navigateFormStructure(formEntryActivity);

        IntentWidget phoneCallWidget = (IntentWidget) formEntryActivity.getODKView().getWidgets().get(0);
        Intent intent = phoneCallWidget.getIntentCallout().generate(FormEntryActivity.mFormController.getFormEntryController().getModel().getForm().getEvaluationContext());
        Assert.assertEquals(intent.getData().toString(), "tel:1234567890");
        Assert.assertEquals(intent.getAction(), "android.intent.action.CALL");
    }

    private FormEntryActivity navigateFormStructure(FormEntryActivity formEntryActivity) {
        StringNumberWidget favoriteNumber = (StringNumberWidget)formEntryActivity.getODKView().getWidgets().get(0);
        favoriteNumber.setAnswer("1234567890");
        ImageButton nextButton = formEntryActivity.findViewById(R.id.nav_btn_next);
        nextButton.performClick();
        return formEntryActivity;
    }

    @Test
    public void testIntentCalloutEmptyData() {
        FormEntryActivity formEntryActivity = ActivityLaunchUtils.launchFormEntry("m0-f1");
        IntentWidget phoneCallWidget = (IntentWidget) formEntryActivity.getODKView().getWidgets().get(0);
        Intent intent = phoneCallWidget.getIntentCallout().generate(FormEntryActivity.mFormController.getFormEntryController().getModel().getForm().getEvaluationContext());
        Assert.assertEquals(intent.getData(), null);
        Assert.assertEquals(intent.getAction(), "android.intent.action.CALL");
    }

    @Test
    public void testIntentCalloutNoData() {
        FormEntryActivity formEntryActivity = ActivityLaunchUtils.launchFormEntry("m0-f2");
        IntentWidget phoneCallWidget = (IntentWidget) formEntryActivity.getODKView().getWidgets().get(0);
        Intent intent = phoneCallWidget.getIntentCallout().generate(FormEntryActivity.mFormController.getFormEntryController().getModel().getForm().getEvaluationContext());
        Assert.assertEquals(intent.getData(), null);
        Assert.assertEquals(intent.getAction(), "android.intent.action.CALL");
    }

    @Test
    public void testIntentCalloutHardCodedData() {
        FormEntryActivity formEntryActivity = ActivityLaunchUtils.launchFormEntry("m0-f3");
        IntentWidget phoneCallWidget = (IntentWidget) formEntryActivity.getODKView().getWidgets().get(0);
        Intent intent = phoneCallWidget.getIntentCallout().generate(FormEntryActivity.mFormController.getFormEntryController().getModel().getForm().getEvaluationContext());
        Assert.assertEquals(intent.getData().toString(), "tel:3333333333");
        Assert.assertEquals(intent.getAction(), "android.intent.action.CALL");
    }

    @Test
    public void testIntentCalloutStringResponse() {
        FormEntryActivity formEntryActivity = ActivityLaunchUtils.launchFormEntry("m0-f4");
        IntentWidget calloutWidget = (IntentWidget) formEntryActivity.getODKView().getWidgets().get(0);
        Intent requestIntent = calloutWidget.getIntentCallout().generate(FormEntryActivity.mFormController.getFormEntryController().getModel().getForm().getEvaluationContext());
        Assert.assertEquals(requestIntent.getAction(), "callout.commcare.org.dummy");

        // with no response
        calloutWidget.performCallout();
        Shadows.shadowOf(formEntryActivity).receiveResult(requestIntent, Activity.RESULT_OK, null);
        TestUtils.assertFormValue("/data/display_data", "");

        // with valid response
        calloutWidget.performCallout();
        Shadows.shadowOf(formEntryActivity).receiveResult(requestIntent, Activity.RESULT_OK, new Intent().putExtra("result_data", "test"));
        TestUtils.assertFormValue("/data/display_data", "test");
    }
}
