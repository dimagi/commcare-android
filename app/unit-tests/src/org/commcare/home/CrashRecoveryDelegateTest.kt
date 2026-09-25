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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class CrashRecoveryDelegateTest {
    @Test
    fun `does not register app data before on-create`() {
        var registrations = 0
        val host = FakeHomeActivityHost()

        host.lifecycle.addObserver(CrashRecoveryDelegate { registrations++ })

        assertEquals(0, registrations)
    }

    @Test
    fun `registers app data once on-create is dispatched`() {
        var registrations = 0
        val host = FakeHomeActivityHost()
        host.lifecycle.addObserver(CrashRecoveryDelegate { registrations++ })

        host.performRestore()
        host.dispatchOnCreate()

        assertEquals(1, registrations)
    }

    /**
     * The registration used to live in `HomeScreenBaseActivity.onCreateSessionSafe`, so what matters
     * is that every host owning a coordinator still gets it - not just that the delegate works alone.
     */
    @Test
    fun `the coordinator registers app data when its host is created`() {
        mockkStatic(CrashUtil::class)
        try {
            every { CrashUtil.registerAppData() } just Runs
            val host = FakeHomeActivityHost()
            HomeActivityCoordinator(host)

            host.performRestore()
            host.dispatchOnCreate()

            verify(exactly = 1) { CrashUtil.registerAppData() }
        } finally {
            unmockkStatic(CrashUtil::class)
        }
    }

    /** An observer added after the host is already created still gets its one registration. */
    @Test
    fun `registers app data when added to an already-created host`() {
        var registrations = 0
        val host = FakeHomeActivityHost()
        host.performRestore()
        host.dispatchOnCreate()

        host.lifecycle.addObserver(CrashRecoveryDelegate { registrations++ })

        assertEquals(1, registrations)
    }
}
