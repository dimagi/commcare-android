package org.commcare.android.tests.formsave;

import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import android.widget.ImageButton;

import org.commcare.android.CommCareTestRunner;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.mocks.FormAndDataSyncerFake;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareHomeActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionNavigator;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.ResourceReferenceFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.views.ODKView;
import org.odk.collect.android.widgets.IntegerWidget;
import org.odk.collect.android.widgets.StringWidget;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowEnvironment;

import java.util.Date;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * Form Record processing / form save related tests
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class FormRecordProcessingTest {
    private static final String TAG = FormRecordProcessingTest.class.getSimpleName();

    @Before
    public void setup() {
        // needed to resolve "jr://resource" type references
        ReferenceManager._().addReferenceFactory(new ResourceReferenceFactory());

        TestUtils.initializeStaticTestStorage();
        TestAppInstaller.setupPrototypeFactory();

        TestAppInstaller appTestInstaller =
                new TestAppInstaller("jr://resource/commcare-apps/form_save_regressions/profile.ccpr",
                        "test", "123");
        appTestInstaller.installAppAndLogin();
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
    }

    /**
     * Regression test for 2.25.1 hotfix where the form record processor
     * parser used during form save was using the wrong parser.
     *
     * Test steps through form and saves it, passing if upon returning to the
     * home screen w/ the form sucessfully saved.
     */
    @Test
    public void testFormRecordProcessingDuringFormSave() {
        CommCareHomeActivity homeActivity = buildHomeActivityForFormEntryLaunch();

        ShadowActivity shadowActivity = Shadows.shadowOf(homeActivity);
        Intent formEntryIntent = shadowActivity.getNextStartedActivity();

        // make sure the form entry activity should be launched
        String intentActivityName = formEntryIntent.getComponent().getClassName();
        assertTrue(intentActivityName.equals(FormEntryActivity.class.getName()));

        ShadowActivity shadowFormEntryActivity = navigateFormEntry(formEntryIntent);

        // trigger CommCareHomeActivity.onActivityResult for the completion of
        // FormEntryActivity
        shadowActivity.receiveResult(formEntryIntent,
                shadowFormEntryActivity.getResultCode(),
                shadowFormEntryActivity.getResultIntent());
        assertStoredFroms();
    }

    private CommCareHomeActivity buildHomeActivityForFormEntryLaunch() {
        AndroidSessionWrapper sessionWrapper =
                CommCareApplication._().getCurrentSessionWrapper();
        CommCareSession session = sessionWrapper.getSession();
        session.setCommand("m0-f0");

        CommCareHomeActivity homeActivity =
                Robolectric.buildActivity(CommCareHomeActivity.class).create().get();
        // make sure we don't actually submit forms by using a fake form submitter
        homeActivity.setFormAndDataSyncer(new FormAndDataSyncerFake(homeActivity));
        SessionNavigator sessionNavigator = homeActivity.getSessionNavigator();
        sessionNavigator.startNextSessionStep();
        return homeActivity;
    }

    private ShadowActivity navigateFormEntry(Intent formEntryIntent) {
        // launch form entry
        FormEntryActivity formEntryActivity =
                Robolectric.buildActivity(FormEntryActivity.class).withIntent(formEntryIntent)
                        .create().start().resume().get();

        ImageButton nextButton = (ImageButton)formEntryActivity.findViewById(R.id.nav_btn_next);

        // enter an answer for the question
        ODKView odkView = formEntryActivity.getODKView();
        IntegerWidget cohort = (IntegerWidget)odkView.getWidgets().get(0);
        cohort.setAnswer("2");

        nextButton.performClick();
        nextButton.performClick();

        ShadowActivity shadowFormEntryActivity = Shadows.shadowOf(formEntryActivity);

        long waitStartTime = new Date().getTime();
        while (!shadowFormEntryActivity.isFinishing()) {
            Log.d(TAG, "Waiting for the form to save and the form entry activity to finish");
            if ((new Date().getTime()) - waitStartTime > 5000) {
                Assert.fail("form entry activity took too long to finish");
            }
        }

        return shadowFormEntryActivity;
    }

    private void assertStoredFroms() {
        SqlStorage<FormRecord> formsStorage =
                CommCareApplication._().getUserStorage(FormRecord.class);

        int unsentForms = formsStorage.getIDsForValue(FormRecord.META_STATUS,
                FormRecord.STATUS_UNSENT).size();
        int incompleteForms = formsStorage.getIDsForValue(FormRecord.META_STATUS,
                FormRecord.STATUS_INCOMPLETE).size();
        assertEquals("There should be a single form waiting to be sent", 1, unsentForms);
        assertEquals("There shouldn't be any forms saved as incomplete", 0, incompleteForms);
    }
}
