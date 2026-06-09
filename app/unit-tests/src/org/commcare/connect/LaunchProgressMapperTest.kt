package org.commcare.connect

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
        assertEquals("seating.app", state.titleKey)
        assertEquals("seating.app", state.messageKey)
        assertNull(state.percent)
    }

    @Test
    fun `signing in maps to the key-management strings`() {
        val state = LaunchProgressMapper.map(LoginProgress(LoginPhase.SigningIn))

        assertFalse(state.showSyncDialog)
        assertEquals("key.manage.title", state.titleKey)
        assertEquals("key.manage.start", state.messageKey)
    }

    @Test
    fun `syncing selects the sync dialog with its own title and message`() {
        val state = LaunchProgressMapper.map(LoginProgress(LoginPhase.Syncing, percent = 42))

        assertTrue(state.showSyncDialog)
        assertEquals("sync.communicating.title", state.titleKey)
        assertEquals("sync.progress.starting", state.messageKey)
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
