package org.commcare.connect.viewmodel

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
class ConnectLearningProgressViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val application = ApplicationProvider.getApplicationContext<CommCareTestApplication>()
    private lateinit var mockRepository: ConnectRepository
    private lateinit var mockJob: ConnectJobRecord
    private lateinit var viewModel: ConnectLearningProgressViewModel

    @Before
    fun setUp() {
        mockRepository = mockk()
        mockJob = mockk(relaxed = true)
        viewModel = ConnectLearningProgressViewModel(application)
        viewModel.repository = mockRepository
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testLoadLearningProgress_postsLoadingThenSuccess() {
        every { mockRepository.getLearningProgress(any(), any(), any()) } returns
            flowOf(
                DataState.Loading,
                DataState.Success(mockJob),
            )

        val results = mutableListOf<DataState<ConnectJobRecord>>()
        viewModel.learningProgress.observeForever { results.add(it) }

        mainCoroutineRule.runBlockingTest {
            viewModel.loadLearningProgress(mockJob)
        }

        assertEquals(2, results.size)
        assertEquals(DataState.Loading, results[0])
        assertTrue(results[1] is DataState.Success)
        assertEquals(mockJob, (results[1] as DataState.Success).data)
    }

    @Test
    fun testLoadLearningProgress_secondCall_cancelsFirstCollection() {
        val secondJob = mockk<ConnectJobRecord>(relaxed = true)

        // First flow suspends after Loading so it never posts Success
        every { mockRepository.getLearningProgress(mockJob, any(), any()) } returns
            flow {
                emit(DataState.Loading)
                awaitCancellation()
            }
        every { mockRepository.getLearningProgress(secondJob, any(), any()) } returns
            flowOf(DataState.Success(secondJob))

        val results = mutableListOf<DataState<ConnectJobRecord>>()
        viewModel.learningProgress.observeForever { results.add(it) }

        mainCoroutineRule.runBlockingTest {
            viewModel.loadLearningProgress(mockJob)
            viewModel.loadLearningProgress(secondJob)
        }

        assertTrue(results.any { it is DataState.Loading })
        assertTrue(results.last() is DataState.Success)
        assertEquals(secondJob, (results.last() as DataState.Success).data)
    }

    @Test
    fun testLoadLearningProgress_postsError_onFailure() {
        every { mockRepository.getLearningProgress(any(), any(), any()) } returns
            flowOf(
                DataState.Loading,
                DataState.Error(cachedData = mockJob),
            )

        val results = mutableListOf<DataState<ConnectJobRecord>>()
        viewModel.learningProgress.observeForever { results.add(it) }

        mainCoroutineRule.runBlockingTest {
            viewModel.loadLearningProgress(mockJob)
        }

        assertEquals(2, results.size)
        assertTrue(results[1] is DataState.Error)
        assertEquals(mockJob, (results[1] as DataState.Error).cachedData)
    }
}
