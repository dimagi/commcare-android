package org.commcare.login

import org.commcare.network.HttpCalloutTask.HttpCalloutOutcomes
import org.commcare.tasks.DataPullTask.PullTaskResult

/**
 * Translates task-layer outcome enums into LoginError. Pure functions — no Android dependencies.
 * Mapping is taken verbatim from the parent investigation plan; do not invent new entries.
 */
internal object OutcomeMapper {
    /** Maps a non-Success HttpCalloutOutcomes from ManageKeyRecordTask to a LoginError. */
    fun fromHttpCalloutOutcome(outcome: HttpCalloutOutcomes): LoginError =
        when (outcome) {
            HttpCalloutOutcomes.AuthFailed,
            HttpCalloutOutcomes.IncorrectPin,
            -> LoginError.BadCredentials

            HttpCalloutOutcomes.NetworkFailure,
            HttpCalloutOutcomes.NetworkFailureBadPassword,
            HttpCalloutOutcomes.CaptivePortal,
            HttpCalloutOutcomes.TokenUnavailable,
            -> LoginError.NetworkUnavailable

            HttpCalloutOutcomes.TokenRequestDenied -> LoginError.TokenDenied
            HttpCalloutOutcomes.AuthOverHttp -> LoginError.AuthOverHttpBlocked

            HttpCalloutOutcomes.BadResponse,
            HttpCalloutOutcomes.BadSslCertificate,
            HttpCalloutOutcomes.UnknownError,
            HttpCalloutOutcomes.InsufficientRolePermission,
            -> LoginError.SyncFailed(outcome.name)

            HttpCalloutOutcomes.Success -> error("Success is not a failure outcome")
        }

    /** Maps a non-DOWNLOAD_SUCCESS PullTaskResult from DataPullTask to a LoginError. */
    fun fromPullTaskResult(
        result: PullTaskResult,
        errorMessage: String?,
    ): LoginError =
        when (result) {
            PullTaskResult.AUTH_FAILED -> LoginError.BadCredentials
            PullTaskResult.TOKEN_DENIED -> LoginError.TokenDenied
            PullTaskResult.AUTH_OVER_HTTP -> LoginError.AuthOverHttpBlocked

            PullTaskResult.UNREACHABLE_HOST,
            PullTaskResult.CONNECTION_TIMEOUT,
            PullTaskResult.CAPTIVE_PORTAL,
            PullTaskResult.TOKEN_UNAVAILABLE,
            -> LoginError.NetworkUnavailable

            PullTaskResult.BAD_DATA,
            PullTaskResult.BAD_DATA_REQUIRES_INTERVENTION,
            PullTaskResult.STORAGE_FULL,
            PullTaskResult.SERVER_ERROR,
            PullTaskResult.RATE_LIMITED_SERVER_ERROR,
            PullTaskResult.ENCRYPTION_FAILURE,
            PullTaskResult.RECOVERY_FAILURE,
            PullTaskResult.ACTIONABLE_FAILURE,
            PullTaskResult.SESSION_EXPIRE,
            PullTaskResult.CANCELLED,
            PullTaskResult.EMPTY_URL,
            PullTaskResult.UNKNOWN_FAILURE,
            PullTaskResult.RETRY_NEEDED,
            PullTaskResult.BAD_CERTIFICATE,
            -> LoginError.SyncFailed(result.name, errorMessage)

            PullTaskResult.DOWNLOAD_SUCCESS -> error("DOWNLOAD_SUCCESS is not a failure outcome")
        }
}
