package org.commcare.android.tests.formentry;

import android.content.Intent;
import android.os.Environment;
import android.widget.ImageButton;

import junit.framework.Assert;

import org.commcare.CommCareTestApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.resource.installers.XFormAndroidInstaller;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.R;
import org.commcare.views.widgets.IntegerWidget;
import org.commcare.views.widgets.IntentWidget;
import org.commcare.views.widgets.StringNumberWidget;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowEnvironment;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

/**
 * @author wpride
 */
@Config(application = CommCareTestApplication.class)
@RunWith(CommCareTestRunner.class)
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
        ShadowActivity shadowActivity =
                ActivityLaunchUtils.buildHomeActivityForFormEntryLaunch("m0-f0");
        Intent formEntryIntent = shadowActivity.getNextStartedActivity();

        FormEntryActivity formEntryActivity = navigateFormStructure(formEntryIntent);

        IntentWidget phoneCallWidget = (IntentWidget) formEntryActivity.getODKView().getWidgets().get(0);
        Intent intent = phoneCallWidget.getIntentCallout().generate(FormEntryActivity.mFormController.getFormEntryController().getModel().getForm().getEvaluationContext());
        Assert.assertEquals(intent.getData().toString(), "tel:1234567890");
        Assert.assertEquals(intent.getAction(), "android.intent.action.CALL");
    }

    private FormEntryActivity navigateFormStructure(Intent formEntryIntent) {
        // launch form entry
        FormEntryActivity formEntryActivity =
                Robolectric.buildActivity(FormEntryActivity.class, formEntryIntent)
                        .create().start().resume().get();
        StringNumberWidget favoriteNumber = (StringNumberWidget)formEntryActivity.getODKView().getWidgets().get(0);
        favoriteNumber.setAnswer("1234567890");
        ImageButton nextButton = formEntryActivity.findViewById(R.id.nav_btn_next);
        nextButton.performClick();
        return formEntryActivity;
    }

    @Test
    public void testIntentCalloutEmptyData() {
        ShadowActivity shadowActivity =
                ActivityLaunchUtils.buildHomeActivityForFormEntryLaunch("m0-f1");
        Intent formEntryIntent = shadowActivity.getNextStartedActivity();
        FormEntryActivity formEntryActivity =
                Robolectric.buildActivity(FormEntryActivity.class, formEntryIntent)
                        .create().start().resume().get();
        IntentWidget phoneCallWidget = (IntentWidget) formEntryActivity.getODKView().getWidgets().get(0);
        Intent intent = phoneCallWidget.getIntentCallout().generate(FormEntryActivity.mFormController.getFormEntryController().getModel().getForm().getEvaluationContext());
        Assert.assertEquals(intent.getData(), null);
        Assert.assertEquals(intent.getAction(), "android.intent.action.CALL");
    }

    @Test
    public void testIntentCalloutNoData() {
        ShadowActivity shadowActivity =
                ActivityLaunchUtils.buildHomeActivityForFormEntryLaunch("m0-f2");
        Intent formEntryIntent = shadowActivity.getNextStartedActivity();
        FormEntryActivity formEntryActivity =
                Robolectric.buildActivity(FormEntryActivity.class, formEntryIntent)
                        .create().start().resume().get();
        IntentWidget phoneCallWidget = (IntentWidget) formEntryActivity.getODKView().getWidgets().get(0);
        Intent intent = phoneCallWidget.getIntentCallout().generate(FormEntryActivity.mFormController.getFormEntryController().getModel().getForm().getEvaluationContext());
        Assert.assertEquals(intent.getData(), null);
        Assert.assertEquals(intent.getAction(), "android.intent.action.CALL");
    }

    @Test
    public void testIntentCalloutHardCodedData() {
        ShadowActivity shadowActivity =
                ActivityLaunchUtils.buildHomeActivityForFormEntryLaunch("m0-f3");
        Intent formEntryIntent = shadowActivity.getNextStartedActivity();
        FormEntryActivity formEntryActivity =
                Robolectric.buildActivity(FormEntryActivity.class, formEntryIntent)
                        .create().start().resume().get();
        IntentWidget phoneCallWidget = (IntentWidget) formEntryActivity.getODKView().getWidgets().get(0);
        Intent intent = phoneCallWidget.getIntentCallout().generate(FormEntryActivity.mFormController.getFormEntryController().getModel().getForm().getEvaluationContext());
        Assert.assertEquals(intent.getData().toString(), "tel:3333333333");
        Assert.assertEquals(intent.getAction(), "android.intent.action.CALL");
    }
}
