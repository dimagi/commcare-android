package org.commcare.android.tests.formsave;

import android.view.View;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.cases.util.InvalidCaseGraphException;
import org.commcare.dalvik.R;
import org.commcare.engine.cases.CaseUtils;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.CommCareSession;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.commcare.android.database.user.models.FormRecord.STATUS_QUARANTINED;

/**
 * Test Scenarios related to cyclic case relationships encountered during the case purge process.
 * These tests are only relevant when the case purge process is enabled with property `cc-auto-purge`
 */
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class CyclicCasesPurgeTest {

    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/cyclic_case_purge/profile.ccpr",
                "test", "123");
    }

    @Test
    public void testFormSaveResultingIntoCaseCycles_ShouldFail() {
        TestUtils.processResourceTransactionIntoAppDb("/inputs/case_create_for_cyclic_case_purge.xml");
        AndroidSessionWrapper sessionWrapper =
                CommCareApplication.instance().getCurrentSessionWrapper();
        CommCareSession session = sessionWrapper.getSession();
        session.setDatum("case_id", "fe9adf5c-7661-4f8e-a48e-4059fcf1f9dd");
        session.setCommand("m2-f0");

        // Completes a form that create a cycle in case indices
        FormEntryActivity formEntryActivity = ActivityLaunchUtils.launchFormEntry();
        View finishButton = formEntryActivity.findViewById(R.id.nav_btn_finish);
        finishButton.performClick();
        ShadowLooper.idleMainLooper();
        // verify that form save results into an error
        assert ShadowDialog.getLatestDialog().findViewById(R.id.dialog_message) != null;
        assert formEntryActivity.getCurrentAlertDialog() != null;
        String message = ((TextView)formEntryActivity.getCurrentAlertDialog().getUnderlyingDialog().getDialog().findViewById(R.id.dialog_message)).getText().toString();
        assert message.contentEquals(formEntryActivity.getString(R.string.invalid_case_graph_error));

        // Verify that the form record was quarantined
        CommCareApplication.instance().getCurrentSessionWrapper().getFormRecord().getStatus().contentEquals(STATUS_QUARANTINED);

        // Verify that the normal purge succeeds without error
        try {
            CaseUtils.purgeCases();
        } catch (InvalidCaseGraphException e) {
            throw new RuntimeException("CommCare didn't rollback transactions from a form that created an invalid state", e);
        }
    }
}
