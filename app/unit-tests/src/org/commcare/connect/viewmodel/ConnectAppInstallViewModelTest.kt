package org.commcare.connect.viewmodel

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.commcare.CommCareTestApplication
import org.commcare.connect.ConnectAppUtils
import org.commcare.engine.resource.AppInstallStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectAppInstallViewModelTest {
    private val application = ApplicationProvider.getApplicationContext<CommCareTestApplication>()
    private lateinit var viewModel: ConnectAppInstallViewModel

    @Before
    fun setUp() {
        mockkObject(ConnectAppUtils)
        every { ConnectAppUtils.downloadApp(any(), any()) } returns Unit
        viewModel = ConnectAppInstallViewModel(application)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `install starts a download and reports zero progress`() {
        viewModel.install(INSTALL_URL)

        verify { ConnectAppUtils.downloadApp(INSTALL_URL, viewModel) }
        assertEquals(InstallState.Downloading(0), viewModel.installState.value)
        assertTrue(viewModel.isInstalling)
    }

    @Test
    fun `install is ignored while one is already running`() {
        viewModel.install(INSTALL_URL)
        viewModel.updateResourceProgress(5, 10, 0)

        viewModel.install(INSTALL_URL)

        verify(exactly = 1) { ConnectAppUtils.downloadApp(any(), any()) }
        assertEquals(InstallState.Downloading(50), viewModel.installState.value)
    }

    @Test
    fun `install runs again once the previous one has been cleared`() {
        viewModel.install(INSTALL_URL)
        viewModel.reportSuccess(true)
        viewModel.clear()

        viewModel.install(INSTALL_URL)

        verify(exactly = 2) { ConnectAppUtils.downloadApp(any(), any()) }
    }

    @Test
    fun `resource progress is reported as a percentage of pending resources`() {
        viewModel.updateResourceProgress(3, 4, 0)

        assertEquals(InstallState.Downloading(75), viewModel.installState.value)
    }

    @Test
    fun `resource progress of zero pending resources reports no progress`() {
        viewModel.updateResourceProgress(0, 0, 0)

        assertEquals(InstallState.Downloading(0), viewModel.installState.value)
    }

    @Test
    fun `resource progress beyond the pending count is capped at full`() {
        viewModel.updateResourceProgress(12, 10, 0)

        assertEquals(InstallState.Downloading(100), viewModel.installState.value)
    }

    @Test
    fun `a successful install reports installed`() {
        viewModel.reportSuccess(true)

        assertEquals(InstallState.Installed, viewModel.installState.value)
    }

    @Test
    fun `an app already on the device is treated as installed`() {
        viewModel.failWithNotification(AppInstallStatus.DuplicateApp)

        assertEquals(InstallState.Installed, viewModel.installState.value)
    }

    @Test
    fun `an unknown failure needs no recovery`() {
        viewModel.failUnknown(AppInstallStatus.UnknownFailure)

        val state = viewModel.installState.value as InstallState.Failed
        assertEquals(InstallFailureRecovery.None, state.recovery)
        assertTrue(state.message.isNotEmpty())
    }

    @Test
    fun `incompatible requirements carry the versions needed to prompt for an apk update`() {
        viewModel.failBadReqs("2.55", "2.50", true)

        val state = viewModel.installState.value as InstallState.Failed
        assertEquals(InstallFailureRecovery.ApkUpdate("2.55", "2.50"), state.recovery)
    }

    @Test
    fun `a target mismatch is reported with its own recovery`() {
        viewModel.failTargetMismatch()

        val state = viewModel.installState.value as InstallState.Failed
        assertEquals(InstallFailureRecovery.TargetMismatch, state.recovery)
    }

    @Test
    fun `a notification failure other than a duplicate app fails the install`() {
        viewModel.failWithNotification(AppInstallStatus.InvalidResource)

        assertTrue(viewModel.installState.value is InstallState.Failed)
    }

    @Test
    fun `verifying counts as installing so a retry cannot start a second download`() {
        viewModel.markVerifying()

        assertTrue(viewModel.isInstalling)

        viewModel.install(INSTALL_URL)

        verify(exactly = 0) { ConnectAppUtils.downloadApp(any(), any()) }
    }

    @Test
    fun `a failed verification fails the install`() {
        viewModel.verificationFailed()

        val state = viewModel.installState.value as InstallState.Failed
        assertEquals(InstallFailureRecovery.None, state.recovery)
        assertFalse(viewModel.isInstalling)
    }

    @Test
    fun `clearing drops the state so it is not replayed to the next observer`() {
        viewModel.reportSuccess(true)

        viewModel.clear()

        assertNull(viewModel.installState.value)
        assertFalse(viewModel.isInstalling)
    }

    companion object {
        private const val INSTALL_URL = "https://example.com/install"
    }
}
