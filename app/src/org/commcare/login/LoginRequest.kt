package org.commcare.login

import org.commcare.activities.DataPullController.DataPullMode
import org.commcare.activities.LoginMode

/** Caller-supplied description of a login attempt; [authSource] decides how credentials are resolved. */
data class LoginRequest(
    val appId: String,
    val username: String,
    val passwordOrPin: String,
    val credentialType: LoginMode,
    val authSource: AuthSource,
    val restoreSession: Boolean,
    val triggerMultipleUsersWarning: Boolean,
    val blockRemoteKeyManagement: Boolean,
    val dataPullMode: DataPullMode = DataPullMode.NORMAL,
) {
    override fun toString(): String =
        "LoginRequest(appId=$appId, username=$username, passwordOrPin=***, " +
            "credentialType=$credentialType, authSource=$authSource, " +
            "restoreSession=$restoreSession, triggerMultipleUsersWarning=$triggerMultipleUsersWarning, " +
            "blockRemoteKeyManagement=$blockRemoteKeyManagement, dataPullMode=$dataPullMode)"
}

enum class AuthSource {
    /** User typed credentials into LoginActivity. */
    Manual,

    /** PersonalID-managed login; credentials are resolved from the linked-app record, created if missing. */
    PersonalId,

    /** MDM-supplied credentials. */
    MdmManaged,
}
