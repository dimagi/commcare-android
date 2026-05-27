package org.commcare.login

import org.commcare.activities.LoginMode

data class LoginRequest(
    val appId: String,
    val username: String,
    val passwordOrPin: String,
    val credentialType: LoginMode,
    val authSource: AuthSource,
    val restoreSession: Boolean,
    val triggerMultipleUsersWarning: Boolean,
    val blockRemoteKeyManagement: Boolean,
) {
    override fun toString(): String =
        "LoginRequest(appId=$appId, username=$username, passwordOrPin=***, " +
            "credentialType=$credentialType, authSource=$authSource, " +
            "restoreSession=$restoreSession, triggerMultipleUsersWarning=$triggerMultipleUsersWarning, " +
            "blockRemoteKeyManagement=$blockRemoteKeyManagement)"
}

enum class AuthSource {
    Manual,
    AutoFromConnect,
    MdmManaged,
}
