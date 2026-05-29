package org.commcare.login

import org.commcare.activities.LoginMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PostLoginRouterTest {
    private fun success(
        loginMode: LoginMode = LoginMode.PASSWORD,
        personalIdManagedLogin: Boolean = false,
        connectManagedLogin: Boolean = false,
        redirectToConnectOpportunityInfo: Boolean = false,
    ) = LoginResult.Success(
        loginMode = loginMode,
        restoreSession = false,
        personalIdManagedLogin = personalIdManagedLogin,
        connectManagedLogin = connectManagedLogin,
        postLoginOutcome = PostLoginOutcome(redirectToConnectOpportunityInfo),
    )

    private fun anyContext() = LaunchContext(startFromLogin = true, manualSwitchToPwMode = false)

    @Test
    fun `success routes to Home carrying result and context fields`() {
        val destination =
            PostLoginRouter.route(
                success(loginMode = LoginMode.PIN, personalIdManagedLogin = true),
                LaunchContext(startFromLogin = true, manualSwitchToPwMode = true),
            )

        assertEquals(
            PostLoginDestination.Home(
                loginMode = LoginMode.PIN,
                startFromLogin = true,
                manualSwitchToPwMode = true,
                personalIdManagedLogin = true,
            ),
            destination,
        )
    }

    @Test
    fun `connectManagedLogin does not affect Home destination`() {
        val destination =
            PostLoginRouter.route(
                success(connectManagedLogin = true),
                LaunchContext(startFromLogin = false, manualSwitchToPwMode = false),
            )

        assertEquals(
            PostLoginDestination.Home(
                loginMode = LoginMode.PASSWORD,
                startFromLogin = false,
                manualSwitchToPwMode = false,
                personalIdManagedLogin = false,
            ),
            destination,
        )
    }

    @Test
    fun `redirectToConnectOpportunityInfo does not affect Home destination`() {
        val destination =
            PostLoginRouter.route(
                success(redirectToConnectOpportunityInfo = true),
                LaunchContext(startFromLogin = true, manualSwitchToPwMode = false),
            )

        assertEquals(
            PostLoginDestination.Home(
                loginMode = LoginMode.PASSWORD,
                startFromLogin = true,
                manualSwitchToPwMode = false,
                personalIdManagedLogin = false,
            ),
            destination,
        )
    }

    @Test
    fun `bad credentials routes to TerminalFailure`() {
        assertEquals(
            PostLoginDestination.TerminalFailure(LoginError.BadCredentials),
            PostLoginRouter.route(LoginResult.Failed(LoginError.BadCredentials), anyContext()),
        )
    }

    @Test
    fun `token denied routes to TerminalFailure`() {
        assertEquals(
            PostLoginDestination.TerminalFailure(LoginError.TokenDenied),
            PostLoginRouter.route(LoginResult.Failed(LoginError.TokenDenied), anyContext()),
        )
    }

    @Test
    fun `network unavailable routes to TerminalFailure`() {
        assertEquals(
            PostLoginDestination.TerminalFailure(LoginError.NetworkUnavailable),
            PostLoginRouter.route(LoginResult.Failed(LoginError.NetworkUnavailable), anyContext()),
        )
    }

    @Test
    fun `auth over http blocked routes to TerminalFailure`() {
        assertEquals(
            PostLoginDestination.TerminalFailure(LoginError.AuthOverHttpBlocked),
            PostLoginRouter.route(LoginResult.Failed(LoginError.AuthOverHttpBlocked), anyContext()),
        )
    }

    @Test
    fun `sync failed preserves reason and message`() {
        val error = LoginError.SyncFailed(SyncFailureReason.SERVER_ERROR, "boom")
        assertEquals(
            PostLoginDestination.TerminalFailure(error),
            PostLoginRouter.route(LoginResult.Failed(error), anyContext()),
        )
    }
}
