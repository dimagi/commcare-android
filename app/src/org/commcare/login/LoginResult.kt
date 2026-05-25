package org.commcare.login

import org.commcare.activities.LoginMode

sealed class LoginResult {
    data class Success(
        val loginMode: LoginMode,
        val restoreSession: Boolean,
        val manualSwitchToPwMode: Boolean,
        val personalIdManagedLogin: Boolean,
        val connectManagedLogin: Boolean,
        val postLoginOutcome: PostLoginOutcome,
    ) : LoginResult()

    data class Failed(
        val error: LoginError,
    ) : LoginResult()
}

data class PostLoginOutcome(
    val redirectToConnectOpportunityInfo: Boolean,
)
