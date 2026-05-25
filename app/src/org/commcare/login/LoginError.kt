package org.commcare.login

sealed class LoginError {
    object BadCredentials : LoginError()

    object TokenDenied : LoginError()

    object NetworkUnavailable : LoginError()

    object AuthOverHttpBlocked : LoginError()

    data class SyncFailed(
        val reason: String,
        val message: String? = null,
    ) : LoginError()
}
