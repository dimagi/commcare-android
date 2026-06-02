package org.commcare.login

import org.commcare.activities.LoginMode

sealed class LoginResult {
    data class Success(
        val appId: String,
        val username: String,
        val loginMode: LoginMode,
        val restoreSession: Boolean,
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
    val needsPersonalIdLinkCheck: Boolean = false,
)
