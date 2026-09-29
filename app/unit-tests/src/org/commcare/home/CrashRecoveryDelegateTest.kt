package org.commcare.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.commcare.CommCareTestApplication
import org.commcare.utils.CrashUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class CrashRecoveryDelegateTest {
    @Before
    fun stubCrashUtil() {
        mockkStatic(CrashUtil::class)
        every { CrashUtil.registerAppData() } just Runs
    }

    @After
    fun unstubCrashUtil() {
        unmockkStatic(CrashUtil::class)
    }

    @Test
    fun `register registers app data`() {
        var registrations = 0

        CrashRecoveryDelegate { registrations++ }.register()

        assertEquals(1, registrations)
    }

    /** Registration has to happen inside `Activity.onCreate`, before `ON_CREATE` is dispatched. */
    @Test
    fun `the coordinator registers app data on session-safe create, before on-create`() {
        val host = FakeHomeActivityHost()
        val coordinator = HomeActivityCoordinator(host)
        host.performRestore()

        coordinator.onSessionSafeCreate()

        verify(exactly = 1) { CrashUtil.registerAppData() }
    }

    @Test
    fun `on-create alone does not register app data`() {
        val host = FakeHomeActivityHost()
        HomeActivityCoordinator(host)

        host.performRestore()
        host.dispatchOnCreate()

        verify(exactly = 0) { CrashUtil.registerAppData() }
    }

    @Test
    fun `on-create after session-safe create does not register again`() {
        val host = FakeHomeActivityHost()
        val coordinator = HomeActivityCoordinator(host)
        host.performRestore()

        coordinator.onSessionSafeCreate()
        host.dispatchOnCreate()

        verify(exactly = 1) { CrashUtil.registerAppData() }
    }
}
