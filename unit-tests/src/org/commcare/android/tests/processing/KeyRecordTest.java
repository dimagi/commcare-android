package org.commcare.android.tests.processing;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.ManageKeyRecordTaskFake;
import org.commcare.activities.DataPullControllerMock;
import org.commcare.activities.LoginMode;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.util.SavedFormLoader;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.models.database.SqlStorage;
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
 * Tests for the processing of Key Record files coming from the server.
 *
 * @author ctsims
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

    @Test
    public void invalidXMLKeyRecordResponseTest() {
        runKeyRecordTask("old_pass", "/inputs/empty_key_record.xml");

        SqlStorage<UserKeyRecord> recordStorage = app.getStorage(UserKeyRecord.class);
        assertEquals(recordStorage.getNumRecords(), 0);
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

    @Test
    public void keyRecordWithDifferentSandboxIdTest() {
        runKeyRecordTask("old_pass", "/inputs/key_record_create.xml");

        SqlStorage<UserKeyRecord> recordStorage = app.getStorage(UserKeyRecord.class);
        assertEquals(recordStorage.getNumRecords(), 1);

        runKeyRecordTask("new_pass", "/inputs/key_record_create_different_uuid.xml");

        int activeCount = 0;
        for (UserKeyRecord record : recordStorage) {
            if (record.isActive()) {
                activeCount++;
            }
        }
        assertEquals(activeCount, 1);
    }

    @Test
    public void keyRecordMigration() {
        runKeyRecordTask("old_pass", "/inputs/key_record_create.xml");

        SqlStorage<UserKeyRecord> recordStorage = app.getStorage(UserKeyRecord.class);
        assertEquals(recordStorage.getNumRecords(), 1);

        TestAppInstaller.login("test", "old_pass");
        SavedFormLoader.loadFormsFromPayload("/commcare-apps/form_nav_tests/form_instances_restore.xml");

        markOutOfDate(recordStorage);

        runKeyRecordTask("old_pass", "/inputs/key_record_create_different_uuid.xml");

        int activeCount = 0;
        for (UserKeyRecord record : recordStorage) {
            if (record.isActive()) {
                activeCount++;
            }
        }
        //assertEquals(activeCount, 1);
    }

    private void runKeyRecordTask(String password, String keyXmlFile) {
        ManageKeyRecordTaskFake keyRecordTast = new ManageKeyRecordTaskFake(RuntimeEnvironment.application, 1, "test", password, LoginMode.PASSWORD, app, false, false, keyXmlFile);
        keyRecordTast.connect((CommCareTaskConnector)new DataPullControllerMock());
        keyRecordTast.execute();
        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();
    }
}
