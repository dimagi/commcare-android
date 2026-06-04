package org.commcare.login

import org.commcare.activities.LoginMode

/** Terminal outcome of [LoginController.performLogin]. */
sealed class LoginResult {
    data class Success(
        val appId: String,
        val username: String,
        val loginMode: LoginMode,
        val restoreSession: Boolean,
        val personalIdManagedLogin: Boolean,
        val linkPassword: String,
        val postLoginOutcome: PostLoginOutcome,
    ) : LoginResult() {
        override fun toString(): String =
            "Success(appId=$appId, username=$username, loginMode=$loginMode, " +
                "restoreSession=$restoreSession, personalIdManagedLogin=$personalIdManagedLogin, " +
                "linkPassword=***, postLoginOutcome=$postLoginOutcome)"
    }

    data class Failed(
        val error: LoginError,
    ) : LoginResult()
}

/** Post-success routing signals the caller acts on once login completes. */
data class PostLoginOutcome(
    val redirectToConnectOpportunityInfo: Boolean,
    val needsPersonalIdLinkCheck: Boolean = false,
)
