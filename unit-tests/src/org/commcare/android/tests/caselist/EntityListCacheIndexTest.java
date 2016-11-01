package org.commcare.android.tests.caselist;

import org.commcare.CommCareTestApplication;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.adapters.EntityListAdapter;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.CaseLoadUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test the cache and index sort property
 *
 * @author willpride
 */
@Config(application = CommCareTestApplication.class)
@RunWith(CommCareTestRunner.class)
public class EntityListCacheIndexTest {
    private EntitySelectActivity entitySelectActivity;
    private EntityListAdapter adapter;

    public static boolean passed = true;

    @Before
    public void setup() {
        String appProfileResource =
                "jr://resource/commcare-apps/index_and_cache_test/profile.ccpr";
        TestAppInstaller.installAppAndLogin(appProfileResource, "test", "123");
        TestUtils.processResourceTransactionIntoAppDb("/commcare-apps/case_list_lookup/restore.xml");
        setupAsyncFailCatch();
    }

    /**
     *     Because the crash happens off the main thread and apparently Roboelectric doesn't
     *     handle those properly we need a way to signal that the test has failed.
     */
    private void setupAsyncFailCatch() {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                EntityListCacheIndexTest.passed = false;
            }
        });
    }

    @Test
    public void testCacheAndIndex() {
        entitySelectActivity = CaseLoadUtils.launchEntitySelectActivity("m1-f0");
        adapter = CaseLoadUtils.loadList(entitySelectActivity);
        assertEquals(8, adapter.getCount());
        // Because the crash happens off the main thread, wait for backgrounds threads to terminate
        Robolectric.flushBackgroundThreadScheduler();
        assertTrue(passed);
    }
}