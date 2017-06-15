package org.commcare.android.tests.processing;

import android.os.Environment;

import org.commcare.android.shadows.ProcessAndSendTaskShadow;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.utils.FormUploadUtil;
import org.junit.Assert;
import org.junit.Test;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowEnvironment;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Created by amstone326 on 6/14/17.
 */

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
    }

}
