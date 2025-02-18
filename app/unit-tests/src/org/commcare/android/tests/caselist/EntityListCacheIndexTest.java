package org.commcare.android.tests.caselist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mockStatic;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.commcare.CommCareTestApplication;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.adapters.EntityListAdapter;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.CaseLoadUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.models.database.user.models.CommCareEntityStorageCache;
import org.commcare.preferences.DeveloperPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.MockedStatic;
import org.robolectric.annotation.Config;
import org.commcare.cases.entity.EntityStorageCache.ValueType;

import java.util.Arrays;
import java.util.Collection;

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


    @Parameterized.Parameter
    public boolean useBulkProcessing;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {true},
                {false}
        });
    }

    @Before
    public void setup() {
        String appProfileResource =
                "jr://resource/commcare-apps/index_and_cache_test/profile.ccpr";
        TestAppInstaller.installAppAndLogin(appProfileResource, "test", "123");
    }

    @Test
    public void testCaseCacheExpiration() {
        try (MockedStatic<DeveloperPreferences> mockedStatic = mockStatic(DeveloperPreferences.class)) {
            mockedStatic.when(DeveloperPreferences::isBulkPerformanceEnabled).thenReturn(useBulkProcessing);

            // Restore
            TestUtils.processResourceTransactionIntoAppDb("/commcare-apps/case_list_lookup/restore.xml");

            // Load Case List
            entitySelectActivity = ActivityLaunchUtils.launchEntitySelectActivity("m1-f0");
            adapter = CaseLoadUtils.loadList(entitySelectActivity);
            assertEquals(8, adapter.getCount());

            // verify cases have been cached
            CommCareEntityStorageCache entityStorageCache = new CommCareEntityStorageCache("case");
            String cacheKey = entityStorageCache.getCacheKey("m1_case_short", "0", ValueType.TYPE_NORMAL_FIELD);
            assertEquals("stan", entityStorageCache.retrieveCacheValue("1", cacheKey));
            assertEquals("ellen", entityStorageCache.retrieveCacheValue("2", cacheKey));
            assertEquals("pat", entityStorageCache.retrieveCacheValue("3", cacheKey));

            // verify changing a case expires the related cases in cache
            TestUtils.processResourceTransactionIntoAppDb(
                    "/commcare-apps/index_and_cache_test/incremental_restore.xml");
            entityStorageCache.processShallowRecords();
            assertNull(entityStorageCache.retrieveCacheValue("1", cacheKey));
            assertNull(entityStorageCache.retrieveCacheValue("2", cacheKey));
            assertNull(entityStorageCache.retrieveCacheValue("3", cacheKey));
        }
    }
}
