package org.commcare.android.tests.processing;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.tests.queries.CaseDbQueryTest;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.models.database.SqlStorage;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.javarosa.core.model.condition.EvaluationContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowEnvironment;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class PurgeFormThatUpdatesClosedCase {
    private static final String TAG = PurgeFormThatUpdatesClosedCase.class.getSimpleName();
    private ShadowActivity shadowHomeActivity;
    private Intent formEntryIntent;

    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/form_save_regressions/profile.ccpr",
                "test", "123");
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
    }

    @Test
    public void testCaseIndexUpdateDuringFormSave() {
        TestUtils.processResourceTransactionIntoAppDb("/inputs/case_create_for_index_table_test.xml");

        EvaluationContext ec = TestUtils.getEvaluationContextWithAndroidIIF();

        CaseDbQueryTest.evaluate("instance('casedb')/casedb/case[@case_type = 'followup'][index/parent = 'worker_one']/@case_id",
                "constant_id", ec);

        partiallyFillOutUpdateForm();
        closeCaseForm();

        finishIncompleteForm();
    }

    private void partiallyFillOutUpdateForm() {
        ShadowActivity shadowFormEntryActivity = startFormEntryAndSaveIncomplete();

        // trigger CommCareHomeActivity.onActivityResult for the completion of
        // FormEntryActivity
        shadowHomeActivity.receiveResult(formEntryIntent,
                shadowFormEntryActivity.getResultCode(),
                shadowFormEntryActivity.getResultIntent());
        SqlStorage<FormRecord> formsStorage =
                CommCareApplication._().getUserStorage(FormRecord.class);

        int incompleteForms = formsStorage.getIDsForValue(FormRecord.META_STATUS,
                FormRecord.STATUS_INCOMPLETE).size();
        assertEquals("There should be 1 form saved as incomplete", 1, incompleteForms);
    }

    private void closeCaseForm() {
        FormEntryActivity formEntryActivity = launchFormEntry("m0-f1");

        // complete the form
        ImageButton nextButton = (ImageButton)formEntryActivity.findViewById(R.id.nav_btn_next);
        nextButton.performClick();

        ShadowActivity shadowFormEntryActivity = Shadows.shadowOf(formEntryActivity);
        ActivityLaunchUtils.waitForActivityFinish(shadowFormEntryActivity);

        // trigger CommCareHomeActivity.onActivityResult for the completion of
        // FormEntryActivity
        shadowHomeActivity.receiveResult(formEntryIntent,
                shadowFormEntryActivity.getResultCode(),
                shadowFormEntryActivity.getResultIntent());
    }

    private void finishIncompleteForm() {
        FormEntryActivity formEntryActivity = ActivityLaunchUtils.openAnIncompleteForm(1, 0);
        // complete the form
        View nextButton = formEntryActivity.findViewById(R.id.nav_btn_finish);
        nextButton.performClick();

        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();

        ShadowActivity shadowFormEntryActivity = Shadows.shadowOf(formEntryActivity);
        ActivityLaunchUtils.waitForActivityFinish(shadowFormEntryActivity);

        // trigger CommCareHomeActivity.onActivityResult for the completion of
        // FormEntryActivity
        shadowHomeActivity.receiveResult(formEntryIntent,
                shadowFormEntryActivity.getResultCode(),
                shadowFormEntryActivity.getResultIntent());
    }

    private ShadowActivity startFormEntryAndSaveIncomplete() {
        FormEntryActivity formEntryActivity = launchFormEntry("m0-f2");

        formEntryActivity.onKeyDown(KeyEvent.KEYCODE_BACK, null);
        AlertDialog alert = ShadowAlertDialog.getLatestAlertDialog();

        // hackily press the 'save form as incomplete' button
        ((DialogChoiceItem)((ListView)alert.findViewById(R.id.choices_list_view)).getAdapter().getItem(2)).listener.onClick(null);

        ShadowActivity shadowFormEntryActivity = Shadows.shadowOf(formEntryActivity);
        ActivityLaunchUtils.waitForActivityFinish(shadowFormEntryActivity);

        return shadowFormEntryActivity;
    }

    private FormEntryActivity launchFormEntry(String command) {
        shadowHomeActivity =
                ActivityLaunchUtils.buildHomeActivityForFormEntryLaunch(command, "constant_id");

        formEntryIntent = shadowHomeActivity.getNextStartedActivity();

        // make sure the form entry activity should be launched
        String intentActivityName = formEntryIntent.getComponent().getClassName();
        assertTrue(intentActivityName.equals(FormEntryActivity.class.getName()));
        FormEntryActivity formEntryActivity =
                Robolectric.buildActivity(FormEntryActivity.class).withIntent(formEntryIntent)
                        .create().start().resume().get();

        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();
        return formEntryActivity;
    }
}
