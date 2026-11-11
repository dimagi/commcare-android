package org.commcare.connect

import org.commcare.dalvik.R
import org.commcare.login.LoginPhase
import org.commcare.login.LoginProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchProgressMapperTest {
    @Test
    fun `seating maps to the seating dialog title and message`() {
        val state = LaunchProgressMapper.map(LoginProgress(LoginPhase.Seating))

        assertFalse(state.showSyncDialog)
        assertEquals(R.string.connect_app_launch_dialog_title, state.titleRes)
        assertEquals(R.string.connect_app_launch_dialog_seating_message, state.messageRes)
        assertNull(state.percent)
    }

    @Test
    fun `signing in maps to the key-management strings`() {
        val state = LaunchProgressMapper.map(LoginProgress(LoginPhase.SigningIn))

        assertFalse(state.showSyncDialog)
        assertEquals(R.string.connect_app_launch_dialog_title, state.titleRes)
        assertEquals(R.string.connect_app_launch_dialog_signing_in_message, state.messageRes)
    }

    @Test
    fun `syncing selects the sync dialog with its own title and message`() {
        val state = LaunchProgressMapper.map(LoginProgress(LoginPhase.Syncing, percent = 42))

        assertTrue(state.showSyncDialog)
        assertEquals(R.string.connect_app_launch_dialog_title, state.titleRes)
        assertEquals(R.string.connect_app_launch_dialog_syncing_message, state.messageRes)
        assertEquals(42, state.percent)
    }

    @Test
    fun `percent is ignored outside the syncing phase`() {
        val state = LaunchProgressMapper.map(LoginProgress(LoginPhase.SigningIn, percent = 99))

        assertNull(state.percent)
    }

    @Test
    fun `message on the progress overrides the phase message`() {
        val state = LaunchProgressMapper.map(LoginProgress(LoginPhase.SigningIn, message = "Almost there"))

        assertEquals("Almost there", state.overrideMessage)
    }
}
