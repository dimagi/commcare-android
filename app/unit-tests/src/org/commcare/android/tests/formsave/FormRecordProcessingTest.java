package org.commcare.android.tests.formsave;

import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.FormEntryActivity;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.commcare.android.tests.queries.CaseDbQueryTest;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.R;
import org.commcare.models.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.RobolectricUtil;
import org.commcare.views.QuestionsView;
import org.commcare.views.widgets.IntegerWidget;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowLooper;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

/**
 * Form Record processing / form save related tests
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class FormRecordProcessingTest {
    private static final String TAG = FormRecordProcessingTest.class.getSimpleName();

    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/form_save_regressions/profile.ccpr",
                "test", "123");
    }

    @Test
    public void testCaseIndexUpdateDuringFormSave() {
        TestUtils.processResourceTransactionIntoAppDb("/inputs/case_create_for_index_table_test.xml");

        EvaluationContext ec = TestUtils.getEvaluationContextWithAndroidIIF();

        CaseDbQueryTest.evaluate("instance('casedb')/casedb/case[@case_type = 'followup'][index/parent = 'worker_one']/@case_id",
                "constant_id", ec);

        fillOutFormWithCaseUpdate();

        // TODO PLM: I would expect the following evaluation to not result in 'constant_id'
        CaseDbQueryTest.evaluate("instance('casedb')/casedb/case[@case_type = 'followup'][index/parent = 'worker_one']/@case_id",
                "constant_id", ec);

        // check to see that the case index was actually updated
        CaseDbQueryTest.evaluate("instance('casedb')/casedb/case[@case_type = 'followup'][index/parent = 'worker_two']/@case_id",
                "constant_id", ec);
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
        fillOutFormWithCaseUpdate();
    }

    private void fillOutFormWithCaseUpdate() {
        ShadowActivity shadowActivity = ActivityLaunchUtils.buildHomeActivityForFormEntryLaunch("m0-f0");
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

    private ShadowActivity navigateFormEntry(Intent formEntryIntent) {
        // launch form entry
        FormEntryActivity formEntryActivity =
                Robolectric.buildActivity(FormEntryActivity.class, formEntryIntent)
                        .create().start().resume().get();
        ShadowLooper.idleMainLooper();
        // enter an answer for the question
        QuestionsView questionsView = formEntryActivity.getODKView();
        IntegerWidget cohort = (IntegerWidget)questionsView.getWidgets().get(0);
        cohort.setAnswer("2");

        ImageButton nextButton = formEntryActivity.findViewById(R.id.nav_btn_next);
        nextButton.performClick();
        View finishButton = formEntryActivity.findViewById(R.id.nav_btn_finish);
        finishButton.performClick();
        RobolectricUtil.flushBackgroundThread(formEntryActivity);
        ShadowActivity shadowFormEntryActivity = Shadows.shadowOf(formEntryActivity);

        long waitStartTime = new Date().getTime();
        while (!formEntryActivity.isFinishing()) {
            Log.d(TAG, "Waiting for the form to save and the form entry activity to finish");
            if ((new Date().getTime()) - waitStartTime > 5000) {
                Assert.fail("form entry activity took too long to finish");
            }
        }

        return shadowFormEntryActivity;
    }

    private void assertStoredFroms() {
        SqlStorage<FormRecord> formsStorage =
                CommCareApplication.instance().getUserStorage(FormRecord.class);

        int unsentForms = formsStorage.getIDsForValue(FormRecord.META_STATUS,
                FormRecord.STATUS_UNSENT).size();
        int incompleteForms = formsStorage.getIDsForValue(FormRecord.META_STATUS,
                FormRecord.STATUS_INCOMPLETE).size();
        assertEquals("There should be a single form waiting to be sent", 1, unsentForms);
        assertEquals("There shouldn't be any forms saved as incomplete", 0, incompleteForms);
    }

    private static final String reasonForFailure = "SOME REASON FOR FAILURE";
    private static final String mockRestoreResponseWithProcessingFailure =
            "<OpenRosaResponse xmlns=\"http://openrosa.org/http/response\"><message nature=" +
                    "\"processing_failure\">" + reasonForFailure + "</message></OpenRosaResponse>";

    @Test
    public void testParsingProcessingFailure() {
        InputStream mockResponseStream =
                new ByteArrayInputStream(mockRestoreResponseWithProcessingFailure.getBytes());
        try {
            String parsedReasonForFailure = FormUploadUtil.parseProcessingFailureResponse(mockResponseStream);
            Assert.assertEquals(reasonForFailure, parsedReasonForFailure);
        } catch (IOException | InvalidStructureException | XmlPullParserException |
                UnfullfilledRequirementsException e) {
            fail("Encountered exception processing test response: " + e.getMessage());
        }
    }
}
