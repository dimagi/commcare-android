package org.commcare.login

import org.commcare.activities.DataPullController.DataPullMode
import org.commcare.activities.LoginMode

data class LoginRequest(
    val appId: String,
    val username: String,
    val passwordOrPin: String,
    val credentialType: LoginMode,
    val authSource: AuthSource,
    val restoreSession: Boolean,
    val pullMode: DataPullMode,
    val triggerMultipleUsersWarning: Boolean,
    val blockRemoteKeyManagement: Boolean,
)

enum class AuthSource {
    /** User typed credentials into LoginActivity. */
    Manual,

    /** Caller already authenticated externally (Connect, PersonalID-managed login). */
    AutoFromConnect,

    /** MDM-supplied credentials. */
    MdmManaged,

    /** Demo CCZ user — bypass sync. */
    Demo,
}
