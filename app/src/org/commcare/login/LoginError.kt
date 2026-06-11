package org.commcare.login

/**
 * Flat set of login failure reasons. The caller handles the variants it cares about and funnels the
 * rest to a generic error; message-carrying variants pass through the underlying server detail.
 */
sealed class LoginError {
    object BadCredentials : LoginError()

    object TokenDenied : LoginError()

    object NetworkUnavailable : LoginError()

    object AuthOverHttpBlocked : LoginError()

    object BadResponse : LoginError()

    object BadSslCertificate : LoginError()

    object StorageFull : LoginError()

    object ServerError : LoginError()

    object RateLimitedServerError : LoginError()

    object SessionExpire : LoginError()

    object Cancelled : LoginError()

    object EmptyUrl : LoginError()

    object InsufficientRolePermission : LoginError()

    data class BadData(
        val message: String? = null,
    ) : LoginError()

    data class BadDataRequiresIntervention(
        val message: String? = null,
    ) : LoginError()

    data class EncryptionFailure(
        val message: String? = null,
    ) : LoginError()

    data class RecoveryFailure(
        val message: String? = null,
    ) : LoginError()

    data class ActionableFailure(
        val message: String? = null,
    ) : LoginError()

    data class UnknownFailure(
        val message: String? = null,
    ) : LoginError()
}
