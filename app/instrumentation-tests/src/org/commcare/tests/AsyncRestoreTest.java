package org.commcare.tests;

import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import org.commcare.AsyncRestoreHelperMock;
import org.commcare.CommCareApplication;
import org.commcare.tasks.DataPullTask;
import org.commcare.utils.HQApi;
import org.commcare.utils.Utility;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBackUnconditionally;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author $|-|!˅@M
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class AsyncRestoreTest extends BaseTest {

    private void installApp() {
        if (CommCareApplication.instance().getCurrentApp() == null) {
            Utility.installApp("integration_test_app.ccz");
        } else {
            // We already have an installed app. But need to make sure it's the one that we're expecting.
            if (!"Integration Tests".equals(CommCareApplication.instance().getCurrentApp().getAppRecord().getDisplayName())) {
                Utility.uninstallCurrentApp();
                Utility.installApp("integration_test_app.ccz");
                // App installation doesn't take back to login screen. Is this an issue?
                pressBackUnconditionally();
            }
        }
    }

    @After
    public void logout() {
        Utility.logout();
    }

    @Test
    public void testRestoreOnLogin() {
        String userId = "13a0910ea963acbf9f4b59dcc9a0f9aa";
        String groupId = "78185f2132bd8ba3af30b488f9974b41";
        // Make sure user is present in the group.
        HQApi.addUserInGroup(userId, groupId);

        // Install the app.
        installApp();

        // Clear cache
        Intent intent = new Intent();
        intent.setAction("org.commcare.dalvik.api.action.ClearCacheOnRestore");
        getInstrumentation()
                .getTargetContext()
                .sendBroadcast(intent);

        // Mock AsyncRestoreHelper
        DataPullTask.setAsyncRestoreHelperClass(AsyncRestoreHelperMock.class);

        assertFalse(AsyncRestoreHelperMock.isRetryCalled());
        assertFalse(AsyncRestoreHelperMock.isServerProgressReportingStarted());

        // Login into the app
        Utility.login("many.cases1", "123");

        // Confirm Async Restore is done.
        assertTrue(AsyncRestoreHelperMock.isRetryCalled());
        assertTrue(AsyncRestoreHelperMock.isServerProgressReportingStarted());
    }

    @Test
    public void testRestoreOnSync() {
        String userId = "81f1645b41d85b539a7e407b035bfbf1";
        String groupId = "78185f2132bd8ba3af30b488f9974b41";

        // Make sure user is not present in the group.
        HQApi.removeUserFromGroup(userId, groupId);

        // Install the app.
        installApp();

        // Clear cache
        Intent intent = new Intent();
        intent.setAction("org.commcare.dalvik.api.action.ClearCacheOnRestore");
        getInstrumentation()
                .getTargetContext()
                .sendBroadcast(intent);

        // Mock AsyncRestoreHelper
        DataPullTask.setAsyncRestoreHelperClass(AsyncRestoreHelperMock.class);

        // Login into the app
        Utility.login("many.cases2", "123");

        // Confirm No Restore happened during login.
        assertFalse(AsyncRestoreHelperMock.isRetryCalled());
        assertFalse(AsyncRestoreHelperMock.isServerProgressReportingStarted());

        // Add user to the group.
        HQApi.addUserInGroup(userId, groupId);

        // Sync with server.
        onView(withText("Sync with Server"))
                .perform(click());

        // Confirm AsyncRestore happened after sync
        assertTrue(AsyncRestoreHelperMock.isRetryCalled());
        assertTrue(AsyncRestoreHelperMock.isServerProgressReportingStarted());
    }

}