package org.commcare.android.tests.processing;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.ManageKeyRecordTaskFake;
import org.commcare.activities.DataPullControllerMock;
import org.commcare.activities.LoginMode;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.models.database.SqlStorage;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

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

    @Before
    public void setupTests() {
        TestUtils.initializeStaticTestStorage();
    }

    @Test
    public void keyRecordProcessesTest() {
        TestAppInstaller.installApp("jr://resource/commcare-apps/form_nav_tests/profile.ccpr");
        CommCareApp app = CommCareApplication._().getCurrentApp();
        ManageKeyRecordTaskFake keyRecordTast = new ManageKeyRecordTaskFake(RuntimeEnvironment.application, 1, "test", "123", LoginMode.PASSWORD, app, false, false);
        keyRecordTast.connect((CommCareTaskConnector) new DataPullControllerMock());
        keyRecordTast.execute();
        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();
        SqlStorage<UserKeyRecord> recordStorage = app.getStorage(UserKeyRecord.class);
        assertEquals(recordStorage.getNumRecords(), 1);
    }
}
