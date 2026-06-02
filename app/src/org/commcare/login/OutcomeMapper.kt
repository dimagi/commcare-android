package org.commcare.login

import org.commcare.network.HttpCalloutTask.HttpCalloutOutcomes
import org.commcare.tasks.DataPullTask.PullTaskResult

internal object OutcomeMapper {
    fun fromHttpCalloutOutcome(outcome: HttpCalloutOutcomes): LoginError =
        when (outcome) {
            HttpCalloutOutcomes.AuthFailed,
            HttpCalloutOutcomes.IncorrectPin,
            -> {
                LoginError.BadCredentials
            }

            HttpCalloutOutcomes.NetworkFailure,
            HttpCalloutOutcomes.NetworkFailureBadPassword,
            HttpCalloutOutcomes.CaptivePortal,
            HttpCalloutOutcomes.TokenUnavailable,
            -> {
                LoginError.NetworkUnavailable
            }

            HttpCalloutOutcomes.TokenRequestDenied -> {
                LoginError.TokenDenied
            }

            HttpCalloutOutcomes.AuthOverHttp -> {
                LoginError.AuthOverHttpBlocked
            }

            HttpCalloutOutcomes.BadResponse -> {
                LoginError.BadResponse
            }

            HttpCalloutOutcomes.BadSslCertificate -> {
                LoginError.BadSslCertificate
            }

            HttpCalloutOutcomes.InsufficientRolePermission -> {
                LoginError.InsufficientRolePermission
            }

            HttpCalloutOutcomes.UnknownError -> {
                LoginError.UnknownFailure()
            }

            HttpCalloutOutcomes.Success -> {
                error("Success is not a failure outcome")
            }
        }

    fun fromPullTaskResult(
        result: PullTaskResult,
        errorMessage: String?,
    ): LoginError =
        when (result) {
            PullTaskResult.AUTH_FAILED -> {
                LoginError.BadCredentials
            }

            PullTaskResult.TOKEN_DENIED -> {
                LoginError.TokenDenied
            }

            PullTaskResult.AUTH_OVER_HTTP -> {
                LoginError.AuthOverHttpBlocked
            }

            PullTaskResult.UNREACHABLE_HOST,
            PullTaskResult.CONNECTION_TIMEOUT,
            PullTaskResult.CAPTIVE_PORTAL,
            PullTaskResult.TOKEN_UNAVAILABLE,
            -> {
                LoginError.NetworkUnavailable
            }

            PullTaskResult.BAD_DATA -> {
                LoginError.BadData(errorMessage)
            }

            PullTaskResult.BAD_DATA_REQUIRES_INTERVENTION -> {
                LoginError.BadDataRequiresIntervention(errorMessage)
            }

            PullTaskResult.STORAGE_FULL -> {
                LoginError.StorageFull
            }

            PullTaskResult.SERVER_ERROR -> {
                LoginError.ServerError
            }

            PullTaskResult.RATE_LIMITED_SERVER_ERROR -> {
                LoginError.RateLimitedServerError
            }

            PullTaskResult.ENCRYPTION_FAILURE -> {
                LoginError.EncryptionFailure(errorMessage)
            }

            PullTaskResult.RECOVERY_FAILURE -> {
                LoginError.RecoveryFailure(errorMessage)
            }

            PullTaskResult.ACTIONABLE_FAILURE -> {
                LoginError.ActionableFailure(errorMessage)
            }

            PullTaskResult.SESSION_EXPIRE -> {
                LoginError.SessionExpire
            }

            PullTaskResult.CANCELLED -> {
                LoginError.Cancelled
            }

            PullTaskResult.EMPTY_URL -> {
                LoginError.EmptyUrl
            }

            PullTaskResult.UNKNOWN_FAILURE,
            PullTaskResult.RETRY_NEEDED,
            PullTaskResult.BAD_CERTIFICATE,
            -> {
                LoginError.UnknownFailure(errorMessage)
            }

            PullTaskResult.DOWNLOAD_SUCCESS -> {
                error("DOWNLOAD_SUCCESS is not a failure outcome")
            }
        }
}
