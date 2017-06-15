package org.commcare.android.tests.processing;

import android.os.Environment;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.mocks.FormAndDataSyncerWithCustomResult;
import org.commcare.android.tests.formsave.FormRecordProcessingTest;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.models.database.SqlStorage;
import org.commcare.utils.FormUploadResult;
import org.commcare.utils.FormUploadUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowEnvironment;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static junit.framework.Assert.assertEquals;

/**
 * Created by amstone326 on 6/14/17.
 */
@Config(application = CommCareTestApplication.class)
@RunWith(CommCareTestRunner.class)
public class QuarantinedFormsTest {

    private static final String reasonForFailure = "SOME REASON FOR FAILURE";
    private static final String mockRestoreResponseWithProcessingFailure =
            "<OpenRosaResponse xmlns=\"http://openrosa.org/http/response\"><message nature=" +
                    "\"processing_failure\">" + reasonForFailure + "</message></OpenRosaResponse>";

    @Test
    public void testParseProcessingFailure() {
        InputStream mockResponseStream =
                new ByteArrayInputStream(mockRestoreResponseWithProcessingFailure.getBytes());
        String parsedReasonForFailure = FormUploadUtil.parseProcessingFailureResponse(mockResponseStream);
        Assert.assertEquals(reasonForFailure, parsedReasonForFailure);
    }

    @Test
    public void testQuarantineDueToProcessingFailure() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/form_save_regressions/profile.ccpr",
                "test", "123");
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);

        // This will fill out and then pretend to submit a form, guaranteeing that the result of
        // the "submission" will be FormUploadResult.PROCESSING_FAILURE (there need to be 3
        // FormUploadResult objects in the list since we attempt to submit a form 3 times)
        FormRecordProcessingTest.fillOutFormWithCaseUpdate(
                new FormAndDataSyncerWithCustomResult(
                        new FormUploadResult[]{ FormUploadResult.PROCESSING_FAILURE,
                                FormUploadResult.PROCESSING_FAILURE, FormUploadResult.PROCESSING_FAILURE }));

        assertQuarantinedForm();
    }

    private void assertQuarantinedForm() {
        SqlStorage<FormRecord> formsStorage =
                CommCareApplication.instance().getUserStorage(FormRecord.class);

        int numQuarantined =
                formsStorage.getIDsForValue(FormRecord.META_STATUS, FormRecord.STATUS_LIMBO).size();

        assertEquals("There should be 1 quarantined form", 1, numQuarantined);
        assertEquals("There should be no other forms besides the quarantined one", 1, formsStorage.getNumRecords());
    }

}
