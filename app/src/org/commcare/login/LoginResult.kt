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
        val linkPassword: String,
        val postLoginOutcome: PostLoginOutcome,
    ) : LoginResult() {
        override fun toString(): String =
            "Success(appId=$appId, username=$username, loginMode=$loginMode, " +
                "restoreSession=$restoreSession, personalIdManagedLogin=$personalIdManagedLogin, " +
                "connectManagedLogin=$connectManagedLogin, linkPassword=***, " +
                "postLoginOutcome=$postLoginOutcome)"
    }

    data class Failed(
        val error: LoginError,
    ) : LoginResult()
}

data class PostLoginOutcome(
    val redirectToConnectOpportunityInfo: Boolean,
    val needsPersonalIdLinkCheck: Boolean = false,
)
