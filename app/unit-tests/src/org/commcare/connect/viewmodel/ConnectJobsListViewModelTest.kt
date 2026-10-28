package org.commcare.connect.viewmodel

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.rules.MainCoroutineRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectJobsListViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val application = ApplicationProvider.getApplicationContext<CommCareTestApplication>()
    private lateinit var mockRepository: ConnectRepository
    private lateinit var viewModel: ConnectJobsListViewModel

    @Before
    fun setUp() {
        mockRepository = mockk()
        viewModel = ConnectJobsListViewModel(application)
        viewModel.repository = mockRepository
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testLoadOpportunities_postsLoadingThenSuccess() {
        val jobs = listOf(mockk<ConnectJobRecord>())
        every { mockRepository.getOpportunities(any(), any()) } returns
            flowOf(
                DataState.Loading,
                DataState.Success(jobs),
            )

        val results = mutableListOf<DataState<List<ConnectJobRecord>>>()
        viewModel.opportunities.observeForever { results.add(it) }

        mainCoroutineRule.runBlockingTest {
            viewModel.loadOpportunities()
        }

        assertEquals(2, results.size)
        assertEquals(DataState.Loading, results[0])
        assertTrue(results[1] is DataState.Success)
        assertEquals(jobs, (results[1] as DataState.Success).data)
    }

    @Test
    fun testLoadOpportunities_postsError_onFailure() {
        every { mockRepository.getOpportunities(any(), any()) } returns
            flowOf(
                DataState.Loading,
                DataState.Error(),
            )

        val results = mutableListOf<DataState<List<ConnectJobRecord>>>()
        viewModel.opportunities.observeForever { results.add(it) }

        mainCoroutineRule.runBlockingTest {
            viewModel.loadOpportunities()
        }

        assertEquals(2, results.size)
        assertTrue(results[1] is DataState.Error)
    }

    @Test
    fun testLoadOpportunities_secondCall_cancelsFirstCollection() {
        val firstJobs = listOf(mockk<ConnectJobRecord>())
        val secondJobs = listOf(mockk<ConnectJobRecord>(), mockk())

        // First flow suspends after Loading so it never posts Success
        every { mockRepository.getOpportunities(false, any()) } returns
            flow {
                emit(DataState.Loading)
                kotlinx.coroutines.awaitCancellation()
            }
        every { mockRepository.getOpportunities(true, any()) } returns
            flowOf(DataState.Success(secondJobs))

        val results = mutableListOf<DataState<List<ConnectJobRecord>>>()
        viewModel.opportunities.observeForever { results.add(it) }

        mainCoroutineRule.runBlockingTest {
            viewModel.loadOpportunities(forceRefresh = false)
            viewModel.loadOpportunities(forceRefresh = true)
        }

        // Loading from first call, then Success from second — no stale Success from first
        assertTrue(results.any { it is DataState.Loading })
        assertTrue(results.last() is DataState.Success)
        assertEquals(secondJobs, (results.last() as DataState.Success).data)
        assertTrue(results.none { it is DataState.Success && it.data == firstJobs })
    }

    @Test
    fun testLoadOpportunities_forceRefresh_passedToRepository() {
        every { mockRepository.getOpportunities(any(), any()) } returns flowOf(DataState.Loading)
        viewModel.opportunities.observeForever { }

        mainCoroutineRule.runBlockingTest {
            viewModel.loadOpportunities(forceRefresh = true)
        }

        verify { mockRepository.getOpportunities(forceRefresh = true, any()) }
    }
}
