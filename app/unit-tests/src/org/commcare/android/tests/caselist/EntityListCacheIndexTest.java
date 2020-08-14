package org.commcare.android.tests.caselist;

import org.commcare.CommCareTestApplication;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.adapters.EntityListAdapter;
import androidx.test.ext.junit.runners.AndroidJUnit4;
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
@RunWith(AndroidJUnit4.class)
public class EntityListCacheIndexTest {
    private EntitySelectActivity entitySelectActivity;
    private EntityListAdapter adapter;
    @Before
    public void setup() {
        String appProfileResource =
                "jr://resource/commcare-apps/index_and_cache_test/profile.ccpr";
        TestAppInstaller.installAppAndLogin(appProfileResource, "test", "123");
        TestUtils.processResourceTransactionIntoAppDb("/commcare-apps/case_list_lookup/restore.xml");
    }

    @Test
    public void testCacheAndIndex() {
        entitySelectActivity = CaseLoadUtils.launchEntitySelectActivity("m1-f0");
        adapter = CaseLoadUtils.loadList(entitySelectActivity);
        assertEquals(8, adapter.getCount());
        // Because the crash happens off the main thread, wait for backgrounds threads to terminate
    }
}