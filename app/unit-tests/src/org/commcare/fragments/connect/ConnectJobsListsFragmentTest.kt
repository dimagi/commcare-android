package org.commcare.fragments.connect

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectActivity
import org.commcare.connect.repository.DataState
import org.commcare.connect.viewmodel.ConnectJobsListViewModel
import org.commcare.rules.MainCoroutineRule
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectJobsListsFragmentTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @Before
    fun setUp() {
        mockkConstructor(ConnectJobsListViewModel::class)
        every { anyConstructed<ConnectJobsListViewModel>().loadOpportunities(any()) } returns Unit
        every { anyConstructed<ConnectJobsListViewModel>().opportunities } returns
            androidx.lifecycle.MutableLiveData(DataState.Loading)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testFragment_isAddedToActivity() {
        val activityController = Robolectric.buildActivity(ConnectActivity::class.java)
        val activity =
            activityController
                .create()
                .start()
                .resume()
                .get()

        val fragment = ConnectJobsListsFragment()
        activity.supportFragmentManager
            .beginTransaction()
            .add(android.R.id.content, fragment)
            .commitNow()

        assertNotNull(fragment.view)
        assertTrue(fragment.isAdded)
    }
}
