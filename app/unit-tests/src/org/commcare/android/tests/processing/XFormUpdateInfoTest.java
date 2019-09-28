package org.commcare.android.tests.processing;

import android.content.Intent;
import android.os.Environment;
import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.DispatchActivity;
import org.commcare.activities.FormEntryActivity;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.R;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.utils.AndroidCommCarePlatform;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowEnvironment;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

@Config(application = CommCareTestApplication.class)
@RunWith(CommCareTestRunner.class)
public class XFormUpdateInfoTest {

    private static final String TAG = XFormUpdateInfoTest.class.getSimpleName();

    // Corresponding Form - app/unit-tests/resources/commcare-apps/update_info_form/modules-0/forms-0.xml
    private static final String UPDATE_FORM_INFO_XMLNS = "http://openrosa.org/formdesigner/9193AB00-6A29-4AF2-A14A-1D400E30E866";

    @Before
    public void setup() {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
    }

    @Test
    public void loginShouldLaunchUpdateInfoForm() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/update_info_form/profile.ccpr",
                "test", "123");

        // Verify that Update Info Form is set
        AndroidCommCarePlatform platform = CommCareApplication.instance().getCommCarePlatform();
        String updateInfoFormXmlns = platform.getUpdateInfoFormXmlns();
        assertEquals(updateInfoFormXmlns, UPDATE_FORM_INFO_XMLNS);

        HiddenPreferences.setShowXformUpdateInfo(true);
        // Start HomeActivity from Login
        Intent i = new Intent();
        i.putExtra(DispatchActivity.START_FROM_LOGIN, true);
        ShadowActivity shadowActivity = ActivityLaunchUtils.buildHomeActivity(false, i);

        // make sure that form entry activity is going to be launched
        Intent formEntryIntent = shadowActivity.getNextStartedActivity();
        String intentActivityName = formEntryIntent.getComponent().getClassName();
        assertTrue(intentActivityName.equals(FormEntryActivity.class.getName()));

        // make sure that update info form id is set in Intent Extras
        assertEquals(formEntryIntent.getIntExtra(FormEntryActivity.KEY_FORM_DEF_ID, -1), platform.getFormDefId(UPDATE_FORM_INFO_XMLNS));

        ShadowActivity shadowFormEntryActivity = navigateFormEntry(formEntryIntent);

        // trigger CommCareHomeActivity.onActivityResult for the completion of
        // FormEntryActivity
        shadowActivity.receiveResult(formEntryIntent,
                shadowFormEntryActivity.getResultCode(),
                shadowFormEntryActivity.getResultIntent());

        // Check if our preference has been reset
        assertFalse(HiddenPreferences.shouldShowXformUpdateInfo());
    }

    private ShadowActivity navigateFormEntry(Intent formEntryIntent) {
        // launch form entry
        FormEntryActivity formEntryActivity =
                Robolectric.buildActivity(FormEntryActivity.class, formEntryIntent)
                        .create().start().resume().get();
        formEntryActivity.findViewById(R.id.nav_btn_finish).performClick();

        ShadowActivity shadowFormEntryActivity = Shadows.shadowOf(formEntryActivity);
        while (!shadowFormEntryActivity.isFinishing()) {
            Log.d(TAG, "Waiting for the form to save and the form entry activity to finish");
        }

        return shadowFormEntryActivity;
    }
}
