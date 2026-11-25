package org.commcare.connect.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.commcare.CommCareTestApplication
import org.commcare.rules.MainCoroutineRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectAppSyncStatusViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val pending = AppSyncStatus(lastSyncTime = 1_000L, unsentForms = 3, syncNeeded = false)
    private var reads = 0

    private fun newViewModel(read: () -> AppSyncStatus = { pending }) =
        ConnectAppSyncStatusViewModel(
            readStatus = {
                reads++
                read()
            },
            ioDispatcher = mainCoroutineRule.testDispatcher,
        )

    @Test
    fun `reload reads the status while the app session is available`() {
        val viewModel = newViewModel()

        viewModel.reload(appSessionAvailable = true)

        assertEquals(pending, viewModel.status.value)
    }

    @Test
    fun `reload clears the status without reading once the session is gone`() {
        val viewModel = newViewModel()
        viewModel.reload(appSessionAvailable = true)

        viewModel.reload(appSessionAvailable = false)

        assertNull(viewModel.status.value)
        assertEquals(1, reads)
    }

    @Test
    fun `a failed read keeps the last status`() {
        var fail = false
        val viewModel = newViewModel { if (fail) throw IllegalStateException("no session") else pending }
        viewModel.reload(appSessionAvailable = true)

        fail = true
        viewModel.reload(appSessionAvailable = true)

        assertEquals(pending, viewModel.status.value)
    }
}
