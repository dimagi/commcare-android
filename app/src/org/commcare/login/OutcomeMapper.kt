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
                LoginError.SyncFailed(SyncFailureReason.BAD_RESPONSE)
            }

            HttpCalloutOutcomes.BadSslCertificate -> {
                LoginError.SyncFailed(SyncFailureReason.BAD_SSL_CERTIFICATE)
            }

            HttpCalloutOutcomes.InsufficientRolePermission -> {
                LoginError.SyncFailed(SyncFailureReason.INSUFFICIENT_ROLE_PERMISSION)
            }

            HttpCalloutOutcomes.UnknownError -> {
                LoginError.SyncFailed(SyncFailureReason.UNKNOWN)
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
                LoginError.SyncFailed(SyncFailureReason.BAD_DATA, errorMessage)
            }

            PullTaskResult.BAD_DATA_REQUIRES_INTERVENTION -> {
                LoginError.SyncFailed(
                    SyncFailureReason.BAD_DATA_REQUIRES_INTERVENTION,
                    errorMessage,
                )
            }

            PullTaskResult.STORAGE_FULL -> {
                LoginError.SyncFailed(SyncFailureReason.STORAGE_FULL)
            }

            PullTaskResult.SERVER_ERROR -> {
                LoginError.SyncFailed(SyncFailureReason.SERVER_ERROR)
            }

            PullTaskResult.RATE_LIMITED_SERVER_ERROR -> {
                LoginError.SyncFailed(SyncFailureReason.RATE_LIMITED_SERVER_ERROR)
            }

            PullTaskResult.ENCRYPTION_FAILURE -> {
                LoginError.SyncFailed(SyncFailureReason.ENCRYPTION_FAILURE, errorMessage)
            }

            PullTaskResult.RECOVERY_FAILURE -> {
                LoginError.SyncFailed(SyncFailureReason.RECOVERY_FAILURE, errorMessage)
            }

            PullTaskResult.ACTIONABLE_FAILURE -> {
                LoginError.SyncFailed(SyncFailureReason.ACTIONABLE_FAILURE, errorMessage)
            }

            PullTaskResult.SESSION_EXPIRE -> {
                LoginError.SyncFailed(SyncFailureReason.SESSION_EXPIRE)
            }

            PullTaskResult.CANCELLED -> {
                LoginError.SyncFailed(SyncFailureReason.CANCELLED)
            }

            PullTaskResult.EMPTY_URL -> {
                LoginError.SyncFailed(SyncFailureReason.EMPTY_URL)
            }

            PullTaskResult.UNKNOWN_FAILURE,
            PullTaskResult.RETRY_NEEDED,
            PullTaskResult.BAD_CERTIFICATE,
            -> {
                LoginError.SyncFailed(SyncFailureReason.UNKNOWN, errorMessage)
            }

            PullTaskResult.DOWNLOAD_SUCCESS -> {
                error("DOWNLOAD_SUCCESS is not a failure outcome")
            }
        }
}
