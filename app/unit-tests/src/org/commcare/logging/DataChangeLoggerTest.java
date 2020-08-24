package org.commcare.logging;

import android.net.Uri;

import org.commcare.CommCareTestApplication;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class DataChangeLoggerTest {

    @Before
    public void setupTests() {
        DataChangeLogger.init(CommCareTestApplication.instance());
    }

    @Test
    public void getLogs_shouldContainLoggedMessages() {
        assertTrue(DataChangeLogger.getLogs().contains(new DataChangeLog.CommCareInstall().getMessage()));

        DataChangeLog wipeUserSandboxLog = new DataChangeLog.WipeUserSandbox();
        DataChangeLogger.log(wipeUserSandboxLog);
        assertTrue(DataChangeLogger.getLogs().contains(wipeUserSandboxLog.getMessage()));

        DataChangeLog dbUpdate = new DataChangeLog.DbUpgradeComplete("User", 3, 4);
        DataChangeLogger.log(dbUpdate);
        assertTrue(DataChangeLogger.getLogs().contains(dbUpdate.getMessage()));
    }

    @Test
    public void getLogFilesUri_shouldReturnAtLeastOneFileUri() {
        ArrayList<Uri> uris = DataChangeLogger.getLogFilesUri();
        assertTrue(uris.size() > 0);
        Uri fileUri = uris.get(0);
        assertEquals(fileUri.getScheme(), "file");
    }
}
