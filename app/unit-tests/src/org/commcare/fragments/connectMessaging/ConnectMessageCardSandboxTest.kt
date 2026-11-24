package org.commcare.fragments.connectMessaging

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectMessageCardSandboxTest {
    @Test
    fun `stale card profiles are every card profile but the current one`() {
        val current = "connect-message-cards-2b6f0c1a-0000-4000-8000-000000000002"
        val names =
            listOf(
                "Default",
                "connect-message-cards",
                "connect-message-cards-9e1d4f7b-0000-4000-8000-000000000001",
                current,
                "some-other-feature",
            )

        assertEquals(
            listOf("connect-message-cards", "connect-message-cards-9e1d4f7b-0000-4000-8000-000000000001"),
            ConnectMessageCardSandbox.staleCardProfiles(names, current),
        )
    }

    @Test
    fun `with only the current profile there is nothing to delete`() {
        val current = "connect-message-cards-2b6f0c1a-0000-4000-8000-000000000002"

        assertEquals(emptyList<String>(), ConnectMessageCardSandbox.staleCardProfiles(listOf("Default", current), current))
    }
}
