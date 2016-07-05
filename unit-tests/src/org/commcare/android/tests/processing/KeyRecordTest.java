package org.commcare.android.tests.processing;

import android.database.Cursor;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.ManageKeyRecordTaskFake;
import org.commcare.activities.DataPullControllerMock;
import org.commcare.activities.LoginMode;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.tests.activities.FormRecordListActivityTest;
import org.commcare.android.util.SavedFormLoader;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.models.database.SqlStorage;
import org.commcare.provider.InstanceProviderAPI;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Date;

import static org.junit.Assert.assertEquals;

/**
 * Test various key record setup code paths
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class KeyRecordTest {
    private CommCareApp app;

    @Before
    public void setupTests() {
        TestUtils.initializeStaticTestStorage();
        TestAppInstaller.installApp("jr://resource/commcare-apps/form_nav_tests/profile.ccpr");
        app = CommCareApplication._().getCurrentApp();
    }

    /**
     * Test key record pull attempt where the xml payload doesn't have a key in
     * it.
     */
    @Test
    public void invalidXMLKeyRecordResponseTest() {
        runKeyRecordTask("old_pass", "/inputs/empty_key_record.xml");

        SqlStorage<UserKeyRecord> recordStorage = app.getStorage(UserKeyRecord.class);
        assertEquals(0, recordStorage.getNumRecords());
    }

    /**
     * Check that old sandbox is completely trashed if a new key record, w/ new
     * password and sandbox id, is sent down.
     */
    @Test
    public void keyRecordWithDifferentSandboxIdTest() {
        runKeyRecordTask("old_pass", "/inputs/key_record_create.xml");

        SqlStorage<UserKeyRecord> recordStorage = app.getStorage(UserKeyRecord.class);
        assertEquals(1, recordStorage.getNumRecords());

        runKeyRecordTask("new_pass", "/inputs/key_record_create_different_uuid.xml");

        assertActiveKeyRecordCount(1, recordStorage);
    }

    /**
     * If HQ sends down a key record for the same password, but with a new
     * sandbox ID, data needs to be migrated from the old sandbox to the new
     * one.
     *
     * Not positive, but I suspect this happens when the old key expires on the server.
     */
    @Test
    public void keyRecordMigration() {
        runKeyRecordTask("old_pass", "/inputs/key_record_create.xml");

        SqlStorage<UserKeyRecord> recordStorage = app.getStorage(UserKeyRecord.class);
        assertEquals(1, recordStorage.getNumRecords());

        TestAppInstaller.login("test", "old_pass");
        SavedFormLoader.loadFormsFromPayload(
                "/commcare-apps/form_nav_tests/form_instances_restore.xml",
                FormRecord.STATUS_SAVED);
        SqlStorage<FormRecord> formRecordStorage =
                CommCareApplication._().getUserStorage(FormRecord.class);
        assertEquals(2, formRecordStorage.getNumRecords());
        CommCareApplication._().closeUserSession();

        markOutOfDate(recordStorage);

        runKeyRecordTask("old_pass", "/inputs/key_record_create_different_uuid.xml");
        TestAppInstaller.login("test", "old_pass");
        formRecordStorage = CommCareApplication._().getUserStorage(FormRecord.class);
        assertEquals(2, formRecordStorage.getNumRecords());

        assertActiveKeyRecordCount(1, recordStorage);
        CommCareApplication._().closeUserSession();
        // trigger form record cleanup
        runKeyRecordTask("old_pass", "/inputs/key_record_create_different_uuid.xml");
        TestAppInstaller.login("test", "old_pass");

        testOpeningMigratedForm();
    }

    private static void testOpeningMigratedForm() {
        TestAppInstaller.login("test", "old_pass");
        FormRecordListActivityTest.openASavedForm(2, 1);
    }

    private static void assertActiveKeyRecordCount(int expectedCount,
                                                   SqlStorage<UserKeyRecord> recordStorage) {
        int activeCount = 0;
        for (UserKeyRecord record : recordStorage) {
            if (record.isActive()) {
                activeCount++;
            }
        }
        assertEquals(expectedCount, activeCount);
    }

    private void runKeyRecordTask(String password, String keyXmlFile) {
        ManageKeyRecordTaskFake keyRecordTast =
                new ManageKeyRecordTaskFake(RuntimeEnvironment.application, 1, "test",
                        password, LoginMode.PASSWORD, app, false, false, keyXmlFile);
        keyRecordTast.connect((CommCareTaskConnector)new DataPullControllerMock());
        keyRecordTast.execute();
        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();
    }

    private static void markOutOfDate(SqlStorage<UserKeyRecord> recordStorage) {
        Date lastWeek = (new DateTime()).minusWeeks(1).toDate();
        Date yesterday = (new DateTime()).minusDays(1).toDate();
        UserKeyRecord ukr = recordStorage.getRecordForValue(UserKeyRecord.META_USERNAME, "test");
        UserKeyRecord outOfDateRecord =
                new UserKeyRecord(ukr.getUsername(), ukr.getPasswordHash(),
                        ukr.getEncryptedKey(), ukr.getWrappedPassword(),
                        lastWeek, yesterday, ukr.getUuid(),
                        ukr.getType());
        outOfDateRecord.setID(ukr.getID());
        recordStorage.write(outOfDateRecord);
    }

}
