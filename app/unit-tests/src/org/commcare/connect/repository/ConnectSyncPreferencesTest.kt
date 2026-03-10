package org.commcare.connect.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.connect.repository.RefreshPolicy
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(AndroidJUnit4::class)
@Config(application = CommCareTestApplication::class)
class ConnectSyncPreferencesTest {
    private lateinit var context: Context
    private lateinit var syncPrefs: ConnectSyncPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        syncPrefs = ConnectSyncPreferences.getInstance(context)
        syncPrefs.clearAll()
    }

    @After
    fun tearDown() {
        syncPrefs.clearAll()
    }

    @Test
    fun testSessionStartTime_initializedOnFirstAccess() {
        val sessionStart = syncPrefs.getSessionStartTime()
        assertNotNull(sessionStart)

        // Should be recent (within last minute)
        val ageMs = Date().time - sessionStart.time
        assertTrue(ageMs < 60_000)
    }

    @Test
    fun testStoreAndRetrieveLastSyncTime() {
        val endpoint = "/opportunities"

        // Initially no sync time
        assertNull(syncPrefs.getLastSyncTime(endpoint))

        // Store sync time
        syncPrefs.storeLastSyncTime(endpoint)

        // Retrieve sync time
        val lastSync = syncPrefs.getLastSyncTime(endpoint)
        assertNotNull(lastSync)

        // Should be recent
        val ageMs = Date().time - lastSync!!.time
        assertTrue(ageMs < 1_000)
    }

    @Test
    fun testShouldRefresh_alwaysPolicy() {
        val endpoint = "/learning_progress"

        // Store a recent sync
        syncPrefs.storeLastSyncTime(endpoint)

        // ALWAYS policy should always return true
        assertTrue(syncPrefs.shouldRefresh(endpoint, RefreshPolicy.ALWAYS))
    }

    @Test
    fun testShouldRefresh_hybridPolicy_freshCache_sameSession() {
        val endpoint = "/opportunities"

        // Mark session start
        syncPrefs.markSessionStart()

        // Wait a bit
        Thread.sleep(100)

        // Store sync time (after session start, cache is fresh)
        syncPrefs.storeLastSyncTime(endpoint)

        // Should NOT refresh - same session AND cache is fresh
        val policy = RefreshPolicy.SESSION_AND_TIME_BASED(60_000) // 1 minute
        assertFalse(syncPrefs.shouldRefresh(endpoint, policy))
    }

    @Test
    fun testShouldRefresh_hybridPolicy_staleCache_sameSession() {
        val endpoint = "/opportunities"

        // Mark session start
        syncPrefs.markSessionStart()

        // Wait a bit
        Thread.sleep(100)

        // Store sync time (after session start)
        syncPrefs.storeLastSyncTime(endpoint)

        // Should refresh - cache is stale (time threshold = 0)
        val policy = RefreshPolicy.SESSION_AND_TIME_BASED(0)
        assertTrue(syncPrefs.shouldRefresh(endpoint, policy))
    }

    @Test
    fun testShouldRefresh_hybridPolicy_freshCache_newSession() {
        val endpoint = "/opportunities"

        // Store sync time first
        syncPrefs.storeLastSyncTime(endpoint)

        // Wait a bit
        Thread.sleep(100)

        // Start new session (after sync)
        syncPrefs.markSessionStart()

        // Should refresh - new session (even though cache is fresh)
        val policy = RefreshPolicy.SESSION_AND_TIME_BASED(60_000) // 1 minute
        assertTrue(syncPrefs.shouldRefresh(endpoint, policy))
    }

    @Test
    fun testShouldRefresh_hybridPolicy_bothConditionsTrue() {
        val endpoint = "/opportunities"

        // Store sync time first
        syncPrefs.storeLastSyncTime(endpoint)

        // Wait a bit
        Thread.sleep(100)

        // Start new session
        syncPrefs.markSessionStart()

        // Should refresh - both new session AND stale cache
        val policy = RefreshPolicy.SESSION_AND_TIME_BASED(0)
        assertTrue(syncPrefs.shouldRefresh(endpoint, policy))
    }

    @Test
    fun testShouldRefresh_neverSynced() {
        val endpoint = "/opportunities"

        // Should always refresh if never synced
        assertTrue(syncPrefs.shouldRefresh(endpoint, RefreshPolicy.ALWAYS))
        assertTrue(syncPrefs.shouldRefresh(endpoint, RefreshPolicy.SESSION_AND_TIME_BASED(60_000)))
    }
}
