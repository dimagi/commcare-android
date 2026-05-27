package org.commcare.login

sealed class LoginError {
    object BadCredentials : LoginError()

    object TokenDenied : LoginError()

    object NetworkUnavailable : LoginError()

    object AuthOverHttpBlocked : LoginError()

    data class SyncFailed(
        val reason: SyncFailureReason,
        val message: String? = null,
    ) : LoginError()
}

enum class SyncFailureReason {
    BAD_DATA,
    BAD_DATA_REQUIRES_INTERVENTION,
    BAD_RESPONSE,
    BAD_SSL_CERTIFICATE,
    STORAGE_FULL,
    SERVER_ERROR,
    RATE_LIMITED_SERVER_ERROR,
    ENCRYPTION_FAILURE,
    RECOVERY_FAILURE,
    ACTIONABLE_FAILURE,
    SESSION_EXPIRE,
    CANCELLED,
    EMPTY_URL,
    INSUFFICIENT_ROLE_PERMISSION,
    UNKNOWN,
}
