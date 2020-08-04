package org.commcare.androidTests;

import android.content.Intent;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import org.commcare.AsyncRestoreHelperMock;
import org.commcare.utils.ProgressIdlingResource;
import org.commcare.utils.HQApi;
import org.commcare.utils.InstrumentationUtility;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
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

    private final String CCZ_NAME = "integration_test_app.ccz";
    private final String APP_NAME = "Integration Tests";

    @After
    public void logout() {
        InstrumentationUtility.logout();
    }

    @Test
    public void testRestoreOnLogin() {
        String userId = "13a0910ea963acbf9f4b59dcc9a0f9aa";
        String groupId = "78185f2132bd8ba3af30b488f9974b41";
        AsyncRestoreHelperMock.clear();

        // Make sure user is present in the group.
        HQApi.addUserInGroup(userId, groupId);

        installAppAndClearCache();

        assertFalse(AsyncRestoreHelperMock.isRetryCalled());
        assertFalse(AsyncRestoreHelperMock.isServerProgressReportingStarted());

        // Register commcareidling resource before login
        ProgressIdlingResource idlingResource = new ProgressIdlingResource();
        IdlingRegistry.getInstance().register(idlingResource);

        // Login into the app
        InstrumentationUtility.login("many.cases1", "123");

        // Confirm Async Restore is done.
        assertTrue(AsyncRestoreHelperMock.isRetryCalled());
        assertTrue(AsyncRestoreHelperMock.isServerProgressReportingStarted());

        // Unregister idling resource
        IdlingRegistry.getInstance().unregister(idlingResource);
    }

    @Test
    public void testRestoreOnSync() {
        String userId = "81f1645b41d85b539a7e407b035bfbf1";
        String groupId = "78185f2132bd8ba3af30b488f9974b41";
        AsyncRestoreHelperMock.clear();

        // Make sure user is not present in the group.
        HQApi.removeUserFromGroup(userId, groupId);

        installAppAndClearCache();

        // Register commcareidling resource before login
        ProgressIdlingResource idlingResource = new ProgressIdlingResource();
        IdlingRegistry.getInstance().register(idlingResource);

        // Login into the app
        InstrumentationUtility.login("many.cases2", "123");

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

        // Unregister idling resource
        IdlingRegistry.getInstance().unregister(idlingResource);
    }

    private void installAppAndClearCache() {
        installApp(APP_NAME, CCZ_NAME);

        Intent intent = new Intent();
        intent.setAction("org.commcare.dalvik.api.action.ClearCacheOnRestore");
        getInstrumentation()
                .getTargetContext()
                .sendBroadcast(intent);
    }

}