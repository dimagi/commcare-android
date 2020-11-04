package org.commcare.androidTests;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.commcare.AsyncRestoreHelperMock;
import org.commcare.annotations.BrowserstackTests;
import org.commcare.provider.DebugControlsReceiver;
import org.commcare.utils.ProgressIdlingResource;
import org.commcare.utils.HQApi;
import org.commcare.utils.InstrumentationUtility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author $|-|!˅@M
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
@BrowserstackTests
public class AsyncRestoreTest extends BaseTest {

    private final String CCZ_NAME = "integration_test_app.ccz";
    private final String APP_NAME = "Integration Tests";
    private final String CLEAR_CACHE_ACTION = "org.commcare.dalvik.api.action.ClearCacheOnRestore";
    private Context mContext;
    private DebugControlsReceiver mReceiver = new DebugControlsReceiver();

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mReceiver, new IntentFilter(CLEAR_CACHE_ACTION));
    }

    @After
    public void logout() {
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mReceiver);
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
        installApp(APP_NAME, CCZ_NAME, false);

        Intent intent = new Intent(CLEAR_CACHE_ACTION);
        LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(intent);
    }

}
