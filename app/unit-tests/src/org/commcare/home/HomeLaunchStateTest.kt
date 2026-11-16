package org.commcare.home

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Save and restore of the launch flags through the host's `SavedStateRegistry`. Robolectric is
 * needed only for a real `Bundle`.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class HomeLaunchStateTest {
    @Test
    fun `every flag defaults to false with nothing saved`() {
        val host = FakeHomeActivityHost()
        val state = HomeLaunchState(host)
        host.performRestore()

        assertFalse(state.wasExternal)
        assertFalse(state.loginExtraWasConsumed)
        assertFalse(state.pendingEndpointNavigationAfterSync)
    }

    @Test
    fun `all three flags survive a save and restore round trip`() {
        val saved =
            saveFrom {
                wasExternal = true
                loginExtraWasConsumed = true
                pendingEndpointNavigationAfterSync = true
            }

        val restored = restoreFrom(saved)

        assertTrue(restored.wasExternal)
        assertTrue(restored.loginExtraWasConsumed)
        assertTrue(restored.pendingEndpointNavigationAfterSync)
    }

    @Test
    fun `each flag round trips independently`() {
        val saved = saveFrom { loginExtraWasConsumed = true }

        val restored = restoreFrom(saved)

        assertFalse(restored.wasExternal)
        assertTrue(restored.loginExtraWasConsumed)
        assertFalse(restored.pendingEndpointNavigationAfterSync)
    }

    @Test
    fun `restoring twice keeps a write made after the first restore`() {
        val host = FakeHomeActivityHost()
        val state = HomeLaunchState(host)
        host.performRestore()
        state.wasExternal = true

        state.ensureRestored()

        assertTrue(state.wasExternal)
    }

    private fun saveFrom(write: HomeLaunchState.() -> Unit): Bundle {
        val host = FakeHomeActivityHost()
        val state = HomeLaunchState(host)
        host.performRestore()
        state.write()
        return host.performSave()
    }

    private fun restoreFrom(saved: Bundle): HomeLaunchState {
        val host = FakeHomeActivityHost()
        val state = HomeLaunchState(host)
        host.performRestore(saved)
        return state
    }
}
