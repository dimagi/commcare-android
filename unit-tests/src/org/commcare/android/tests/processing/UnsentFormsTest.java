package org.commcare.android.tests.processing;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.util.SavedFormLoader;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.models.database.SqlStorage;
import org.commcare.utils.StorageUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class UnsentFormsTest {

    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/archive_form_tests/profile.ccpr",
                "test", "123");

        SavedFormLoader.loadFormsFromPayload(
                "/commcare-apps/archive_form_tests/unsent_form_payload.xml",
                FormRecord.STATUS_UNSENT);
        // load some extra forms to make sure only unsent ones are processed
        SavedFormLoader.loadFormsFromPayload(
                "/commcare-apps/archive_form_tests/saved_form_payload.xml",
                FormRecord.STATUS_SAVED);
    }

    /**
     * Verify that unsent forms are correctly ordered when submitted
     */
    @Test
    public void testUnsentFormLookup() {
        String[] instanceOrder = {
                "8dd4031e-ee5a-4423-93b3-ad45c3ced47e",
                "1dd3031e-ee5a-4423-93b3-ad45c3ced47a",
                "2dd2031e-ee5a-4423-93b3-ad45c3ced47b",
                "3dd1031e-ee5a-4423-93b3-ad45c3ced47c",
                "4dd0031e-ee5a-4423-93b3-ad45c3ced47d"};
        SqlStorage<FormRecord> storage = CommCareApplication._().getUserStorage(FormRecord.class);
        FormRecord[] records = StorageUtils.getUnsentRecords(storage);

        int count = 0;
        for (FormRecord record : records) {
            assertEquals(instanceOrder[count], record.getInstanceID());
            count++;
        }

        assertEquals(5, records.length);
        assertEquals(10, storage.getNumRecords());
    }
}
