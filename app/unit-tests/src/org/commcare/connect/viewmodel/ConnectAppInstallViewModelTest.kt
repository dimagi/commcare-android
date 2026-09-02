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
        every { ConnectAppUtils.downloadApp(any(), any()) } returns true
        viewModel = ConnectAppInstallViewModel(application)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `install starts a download and reports zero progress`() {
        assertTrue(viewModel.install(target(), INSTALL_URL))

        verify { ConnectAppUtils.downloadApp(INSTALL_URL, viewModel) }
        assertEquals(InstallState.Downloading(0), viewModel.installState.value)
        assertEquals(target(), viewModel.target)
        assertTrue(viewModel.isInstalling)
    }

    @Test
    fun `install is refused while one is already running`() {
        viewModel.install(target(), INSTALL_URL)
        viewModel.updateResourceProgress(5, 10, 0)

        assertFalse(viewModel.install(target(appId = "other-app"), "https://example.com/other"))

        verify(exactly = 1) { ConnectAppUtils.downloadApp(any(), any()) }
        assertEquals(InstallState.Downloading(50), viewModel.installState.value)
    }

    @Test
    fun `a refused install leaves the running install's target in place`() {
        viewModel.install(target(), INSTALL_URL)

        viewModel.install(target(appId = "other-app"), "https://example.com/other")

        assertEquals(APP_ID, viewModel.target?.appId)
    }

    /**
     * The download layer keeps its own process-wide guard; entering a downloading state it never
     * backed would leave the screen waiting on progress that can never arrive.
     */
    @Test
    fun `a download the download layer refuses to start leaves no install in flight`() {
        every { ConnectAppUtils.downloadApp(any(), any()) } returns false

        assertFalse(viewModel.install(target(), INSTALL_URL))

        assertNull(viewModel.installState.value)
        assertNull(viewModel.target)
        assertFalse(viewModel.isInstalling)
    }

    @Test
    fun `install runs again once the previous one has been cleared`() {
        viewModel.install(target(), INSTALL_URL)
        viewModel.reportSuccess(true)
        viewModel.clear()

        assertTrue(viewModel.install(target(), INSTALL_URL))

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

        assertFalse(viewModel.install(target(), INSTALL_URL))

        verify(exactly = 0) { ConnectAppUtils.downloadApp(any(), any()) }
    }

    @Test
    fun `a failure is only handed out once, so it is not re-reported on every recreation`() {
        viewModel.failUnknown(AppInstallStatus.UnknownFailure)

        assertTrue(viewModel.consumeFailure())
        assertFalse(viewModel.consumeFailure())
    }

    @Test
    fun `a fresh install hands out its own failure again`() {
        viewModel.failUnknown(AppInstallStatus.UnknownFailure)
        viewModel.consumeFailure()
        viewModel.clear()

        viewModel.install(target(), INSTALL_URL)
        viewModel.failUnknown(AppInstallStatus.UnknownFailure)

        assertTrue(viewModel.consumeFailure())
    }

    @Test
    fun `a failed verification fails the install`() {
        viewModel.verificationFailed()

        val state = viewModel.installState.value as InstallState.Failed
        assertEquals(InstallFailureRecovery.None, state.recovery)
        assertFalse(viewModel.isInstalling)
    }

    @Test
    fun `clearing drops the state and its target so neither is replayed to the next observer`() {
        viewModel.install(target(), INSTALL_URL)
        viewModel.reportSuccess(true)

        viewModel.clear()

        assertNull(viewModel.installState.value)
        assertNull(viewModel.target)
        assertFalse(viewModel.isInstalling)
    }

    private fun target(
        appId: String = APP_ID,
        isLearning: Boolean = true,
        ownerKey: String = OWNER_KEY,
    ) = InstallTarget(appId, isLearning, popSelfOnLaunch = true, ownerKey = ownerKey)

    companion object {
        private const val INSTALL_URL = "https://example.com/install"
        private const val APP_ID = "learn-app-001"
        private const val OWNER_KEY = "org.commcare.fragments.connect.ConnectJobIntroFragment"
    }
}
