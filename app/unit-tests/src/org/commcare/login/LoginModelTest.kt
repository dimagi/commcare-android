package org.commcare.login

import org.commcare.activities.DataPullController.DataPullMode
import org.commcare.activities.LoginMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LoginModelTest {
    @Test
    fun `LoginRequest equals respects all fields`() {
        val a = sampleRequest()
        val b = sampleRequest()
        assertEquals(a, b)
        assertNotEquals(a, b.copy(passwordOrPin = "other"))
    }

    @Test
    fun `LoginProgress defaults percent and message to null`() {
        val p = LoginProgress(phase = LoginPhase.SigningIn)
        assertEquals(null, p.percent)
        assertEquals(null, p.message)
    }

    @Test
    fun `LoginResult Success carries all routing fields`() {
        val r =
            LoginResult.Success(
                loginMode = LoginMode.PASSWORD,
                restoreSession = true,
                manualSwitchToPwMode = false,
                personalIdManagedLogin = true,
                connectManagedLogin = true,
                postLoginOutcome = PostLoginOutcome(redirectToConnectOpportunityInfo = true),
            )
        assertEquals(true, r.connectManagedLogin)
        assertEquals(true, r.postLoginOutcome.redirectToConnectOpportunityInfo)
    }

    @Test
    fun `LoginError SyncFailed carries reason and optional message`() {
        val e = LoginError.SyncFailed(reason = "BAD_DATA", message = "parse error")
        assertEquals("BAD_DATA", e.reason)
        assertEquals("parse error", e.message)
    }

    private fun sampleRequest() =
        LoginRequest(
            appId = "app-1",
            username = "alice",
            passwordOrPin = "secret",
            credentialType = LoginMode.PASSWORD,
            authSource = AuthSource.Manual,
            restoreSession = false,
            pullMode = DataPullMode.NORMAL,
            triggerMultipleUsersWarning = false,
            blockRemoteKeyManagement = false,
        )
}
