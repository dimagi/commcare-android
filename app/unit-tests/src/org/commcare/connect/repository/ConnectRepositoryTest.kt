package org.commcare.connect.repository

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.connect.ConnectNetworkClient
import org.commcare.connect.network.connect.models.ConnectOpportunitiesResponseModel
import org.commcare.connect.network.connect.models.LearningAppProgressResponseModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<CommCareTestApplication>()
    private lateinit var mockSyncPrefs: ConnectSyncPreferences
    private lateinit var mockNetworkClient: ConnectNetworkClient
    private lateinit var mockUser: ConnectUserRecord
    private lateinit var repository: ConnectRepository

    @Before
    fun setUp() {
        mockSyncPrefs = mockk(relaxed = true)
        mockNetworkClient = mockk()
        mockUser = mockk()

        // Static mocks for database utilities
        mockkStatic(ConnectJobUtils::class)
        mockkStatic(ConnectUserDatabaseUtil::class)
        every { ConnectUserDatabaseUtil.getUser(any()) } returns mockUser

        repository = ConnectRepository(context, mockSyncPrefs, mockNetworkClient)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGetOpportunities_noCache_emitsLoading() =
        runBlocking {
            every { ConnectJobUtils.getCompositeJobs(any(), any(), any()) } returns emptyList()
            every { mockSyncPrefs.getLastSyncTime(any()) } returns null
            every { mockSyncPrefs.shouldRefresh(any(), any()) } returns false

            val emissions = repository.getOpportunities().toList()

            assertEquals(1, emissions.size)
            assertTrue(emissions.first() is DataState.Loading)
        }

    @Test
    fun testGetOpportunities_withCache_shouldRefreshFalse_emitsCachedOnly() =
        runBlocking {
            val cachedJobs = listOf(mockk<ConnectJobRecord>())
            every { ConnectJobUtils.getCompositeJobs(any(), any(), any()) } returns cachedJobs
            every { mockSyncPrefs.getLastSyncTime(any()) } returns Date()
            every { mockSyncPrefs.shouldRefresh(any(), any()) } returns false

            val emissions = repository.getOpportunities().toList()

            assertEquals(1, emissions.size)
            assertTrue(emissions[0] is DataState.Cached)
            assertEquals(cachedJobs, (emissions[0] as DataState.Cached).data)
        }

    @Test
    fun testGetOpportunities_withCache_networkSuccess_emitsCachedThenSuccess() =
        runBlocking {
            val cachedJobs = listOf(mockk<ConnectJobRecord>())
            val freshJobs = listOf(mockk<ConnectJobRecord>(), mockk())
            val mockModel = mockk<ConnectOpportunitiesResponseModel>()
            every { mockModel.validJobs } returns freshJobs

            every { ConnectJobUtils.getCompositeJobs(any(), any(), any()) } returns cachedJobs
            every { mockSyncPrefs.getLastSyncTime(any()) } returns Date()
            every { mockSyncPrefs.shouldRefresh(any(), any()) } returns true
            coEvery { mockNetworkClient.getConnectOpportunities(any()) } returns Result.success(mockModel)

            val emissions = repository.getOpportunities().toList()

            assertEquals(2, emissions.size)
            assertTrue(emissions[0] is DataState.Cached)
            assertTrue(emissions[1] is DataState.Success)
            assertEquals(freshJobs, (emissions[1] as DataState.Success).data)
        }

    @Test
    fun testGetOpportunities_networkFailure_withCache_emitsError_withCachedData() =
        runBlocking {
            val cachedJobs = listOf(mockk<ConnectJobRecord>())
            every { ConnectJobUtils.getCompositeJobs(any(), any(), any()) } returns cachedJobs
            every { mockSyncPrefs.getLastSyncTime(any()) } returns Date()
            every { mockSyncPrefs.shouldRefresh(any(), any()) } returns true
            coEvery { mockNetworkClient.getConnectOpportunities(any()) } returns
                Result.failure(Exception("Network error"))

            val emissions = repository.getOpportunities().toList()

            assertEquals(2, emissions.size)
            assertTrue(emissions[0] is DataState.Cached)
            assertTrue(emissions[1] is DataState.Error)
            assertNotNull((emissions[1] as DataState.Error).cachedData)
        }

    @Test
    fun testGetOpportunities_networkFailure_neverSynced_emitsError_withEmptyCachedData() =
        runBlocking {
            every { ConnectJobUtils.getCompositeJobs(any(), any(), any()) } returns emptyList()
            every { mockSyncPrefs.getLastSyncTime(any()) } returns null
            every { mockSyncPrefs.shouldRefresh(any(), any()) } returns true
            coEvery { mockNetworkClient.getConnectOpportunities(any()) } returns
                Result.failure(Exception("Network error"))

            val emissions = repository.getOpportunities().toList()

            assertEquals(2, emissions.size)
            assertTrue(emissions[0] is DataState.Loading)
            assertTrue(emissions[1] is DataState.Error)
            // cachedData is emptyList (not null) because loadCache always returns the list
            assertEquals(emptyList<ConnectJobRecord>(), (emissions[1] as DataState.Error).cachedData)
        }

    @Test
    fun testGetOpportunities_syncedEmptyList_emitsCached() =
        runBlocking {
            val syncTime = Date()
            every { ConnectJobUtils.getCompositeJobs(any(), any(), any()) } returns emptyList()
            every { mockSyncPrefs.getLastSyncTime(any()) } returns syncTime
            every { mockSyncPrefs.shouldRefresh(any(), any()) } returns false

            val emissions = repository.getOpportunities().toList()

            assertEquals(1, emissions.size)
            assertTrue(emissions[0] is DataState.Cached)
            assertEquals(emptyList<ConnectJobRecord>(), (emissions[0] as DataState.Cached).data)
            assertEquals(syncTime, (emissions[0] as DataState.Cached).timestamp)
        }

    @Test
    fun testGetOpportunities_forceRefresh_bypassesShouldRefreshCheck() =
        runBlocking {
            val cachedJobs = listOf(mockk<ConnectJobRecord>())
            val mockModel = mockk<ConnectOpportunitiesResponseModel>()
            every { mockModel.validJobs } returns emptyList()

            every { ConnectJobUtils.getCompositeJobs(any(), any(), any()) } returns cachedJobs
            every { mockSyncPrefs.getLastSyncTime(any()) } returns Date()
            every { mockSyncPrefs.shouldRefresh(any(), any()) } returns false // would skip network
            coEvery { mockNetworkClient.getConnectOpportunities(any()) } returns Result.success(mockModel)

            val emissions = repository.getOpportunities(forceRefresh = true).toList()

            // Should have network call (Success emission) despite shouldRefresh=false
            assertTrue(emissions.any { it is DataState.Success })
        }

    @Test
    fun testGetOpportunities_networkSuccess_storesLastSyncTime() =
        runBlocking {
            val mockModel = mockk<ConnectOpportunitiesResponseModel>()
            every { mockModel.validJobs } returns emptyList()
            every { ConnectJobUtils.getCompositeJobs(any(), any(), any()) } returns emptyList()
            every { mockSyncPrefs.getLastSyncTime(any()) } returns null
            every { mockSyncPrefs.shouldRefresh(any(), any()) } returns true
            coEvery { mockNetworkClient.getConnectOpportunities(any()) } returns Result.success(mockModel)

            repository.getOpportunities().toList()

            verify { mockSyncPrefs.storeLastSyncTime(any()) }
        }

    @Test
    fun testGetLearningProgress_alwaysPolicy_networkCalledEachTime() =
        runBlocking {
            val mockJob = mockk<ConnectJobRecord>(relaxed = true)
            val mockModel = mockk<LearningAppProgressResponseModel>(relaxed = true)
            every { mockModel.connectJobLearningRecords } returns emptyList()
            every { mockModel.connectJobAssessmentRecords } returns emptyList()
            every { ConnectJobUtils.getCompositeJob(any(), any()) } returns mockJob
            every { mockSyncPrefs.getLastSyncTime(any()) } returns Date()
            every { mockSyncPrefs.shouldRefresh(any(), any()) } returns true
            coEvery { mockNetworkClient.getLearningProgress(any(), any()) } returns Result.success(mockModel)
            every { ConnectJobUtils.updateJobLearnProgress(any(), any()) } returns Unit

            // First call
            repository.getLearningProgress(mockJob).toList()
            // Second call — ALWAYS policy means shouldRefresh always true
            repository.getLearningProgress(mockJob).toList()

            coVerify(exactly = 2) { mockNetworkClient.getLearningProgress(any(), any()) }
        }
}
