package org.commcare.connect.viewmodel

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import org.commcare.CommCareTestApplication
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.rules.MainCoroutineRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectJobIntroViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val application = ApplicationProvider.getApplicationContext<CommCareTestApplication>()
    private lateinit var mockRepository: ConnectRepository
    private lateinit var viewModel: ConnectJobIntroViewModel

    @Before
    fun setUp() {
        mockRepository = mockk()
        viewModel = ConnectJobIntroViewModel(application, mockRepository)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testStartLearning_postsLoadingThenSuccess() {
        every { mockRepository.startLearning(any()) } returns
            flowOf(DataState.Loading, DataState.Success(Unit))

        val results = mutableListOf<DataState<Unit>>()
        viewModel.startLearning.observeForever { results.add(it) }

        mainCoroutineRule.runBlockingTest {
            viewModel.startLearning("test-uuid")
        }

        assertEquals(2, results.size)
        assertEquals(DataState.Loading, results[0])
        assertEquals(DataState.Success(Unit), results[1])
    }

    @Test
    fun testStartLearning_postsError_onFailure() {
        every { mockRepository.startLearning(any()) } returns
            flowOf(DataState.Loading, DataState.Error())

        val results = mutableListOf<DataState<Unit>>()
        viewModel.startLearning.observeForever { results.add(it) }

        mainCoroutineRule.runBlockingTest {
            viewModel.startLearning("test-uuid")
        }

        assertEquals(2, results.size)
        assertEquals(DataState.Error<Unit>(), results[1])
    }
}
