package org.commcare.login

import org.commcare.network.HttpCalloutTask.HttpCalloutOutcomes
import org.commcare.tasks.DataPullTask.PullTaskResult
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class OutcomeMapperTest {
    @Test
    fun `httpCalloutOutcome mappings`() {
        val cases =
            mapOf(
                HttpCalloutOutcomes.AuthFailed to LoginError.BadCredentials,
                HttpCalloutOutcomes.IncorrectPin to LoginError.BadCredentials,
                HttpCalloutOutcomes.NetworkFailure to LoginError.NetworkUnavailable,
                HttpCalloutOutcomes.NetworkFailureBadPassword to LoginError.NetworkUnavailable,
                HttpCalloutOutcomes.CaptivePortal to LoginError.NetworkUnavailable,
                HttpCalloutOutcomes.TokenUnavailable to LoginError.NetworkUnavailable,
                HttpCalloutOutcomes.TokenRequestDenied to LoginError.TokenDenied,
                HttpCalloutOutcomes.AuthOverHttp to LoginError.AuthOverHttpBlocked,
                HttpCalloutOutcomes.BadResponse to LoginError.SyncFailed(SyncFailureReason.BAD_RESPONSE),
                HttpCalloutOutcomes.BadSslCertificate to LoginError.SyncFailed(SyncFailureReason.BAD_SSL_CERTIFICATE),
                HttpCalloutOutcomes.UnknownError to LoginError.SyncFailed(SyncFailureReason.UNKNOWN),
                HttpCalloutOutcomes.InsufficientRolePermission to
                    LoginError.SyncFailed(SyncFailureReason.INSUFFICIENT_ROLE_PERMISSION),
            )
        cases.forEach { (outcome, expected) ->
            assertEquals("Mapping for $outcome", expected, OutcomeMapper.fromHttpCalloutOutcome(outcome))
        }
    }

    @Test
    fun `httpCalloutOutcome covers every failure variant`() {
        val covered =
            HttpCalloutOutcomes
                .values()
                .filter { it != HttpCalloutOutcomes.Success }
                .toSet()
        covered.forEach { OutcomeMapper.fromHttpCalloutOutcome(it) }
    }

    @Test
    fun `httpCalloutOutcome Success throws`() {
        try {
            OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.Success)
            fail("expected IllegalStateException for Success")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `pullTaskResult mappings`() {
        val cases =
            mapOf(
                PullTaskResult.AUTH_FAILED to LoginError.BadCredentials,
                PullTaskResult.TOKEN_DENIED to LoginError.TokenDenied,
                PullTaskResult.AUTH_OVER_HTTP to LoginError.AuthOverHttpBlocked,
                PullTaskResult.UNREACHABLE_HOST to LoginError.NetworkUnavailable,
                PullTaskResult.CONNECTION_TIMEOUT to LoginError.NetworkUnavailable,
                PullTaskResult.CAPTIVE_PORTAL to LoginError.NetworkUnavailable,
                PullTaskResult.TOKEN_UNAVAILABLE to LoginError.NetworkUnavailable,
                PullTaskResult.BAD_DATA to LoginError.SyncFailed(SyncFailureReason.BAD_DATA, "boom"),
                PullTaskResult.BAD_DATA_REQUIRES_INTERVENTION to
                    LoginError.SyncFailed(SyncFailureReason.BAD_DATA_REQUIRES_INTERVENTION, "boom"),
                PullTaskResult.STORAGE_FULL to LoginError.SyncFailed(SyncFailureReason.STORAGE_FULL),
                PullTaskResult.SERVER_ERROR to LoginError.SyncFailed(SyncFailureReason.SERVER_ERROR),
                PullTaskResult.RATE_LIMITED_SERVER_ERROR to
                    LoginError.SyncFailed(SyncFailureReason.RATE_LIMITED_SERVER_ERROR),
                PullTaskResult.ENCRYPTION_FAILURE to LoginError.SyncFailed(SyncFailureReason.ENCRYPTION_FAILURE, "boom"),
                PullTaskResult.RECOVERY_FAILURE to LoginError.SyncFailed(SyncFailureReason.RECOVERY_FAILURE, "boom"),
                PullTaskResult.ACTIONABLE_FAILURE to LoginError.SyncFailed(SyncFailureReason.ACTIONABLE_FAILURE, "boom"),
                PullTaskResult.SESSION_EXPIRE to LoginError.SyncFailed(SyncFailureReason.SESSION_EXPIRE),
                PullTaskResult.CANCELLED to LoginError.SyncFailed(SyncFailureReason.CANCELLED),
                PullTaskResult.EMPTY_URL to LoginError.SyncFailed(SyncFailureReason.EMPTY_URL),
                PullTaskResult.UNKNOWN_FAILURE to LoginError.SyncFailed(SyncFailureReason.UNKNOWN, "boom"),
                PullTaskResult.RETRY_NEEDED to LoginError.SyncFailed(SyncFailureReason.UNKNOWN, "boom"),
                PullTaskResult.BAD_CERTIFICATE to LoginError.SyncFailed(SyncFailureReason.UNKNOWN, "boom"),
            )
        cases.forEach { (result, expected) ->
            assertEquals("Mapping for $result", expected, OutcomeMapper.fromPullTaskResult(result, "boom"))
        }
    }

    @Test
    fun `pullTaskResult covers every failure variant`() {
        PullTaskResult
            .values()
            .filter { it != PullTaskResult.DOWNLOAD_SUCCESS }
            .forEach { OutcomeMapper.fromPullTaskResult(it, null) }
    }

    @Test
    fun `pullTaskResult DOWNLOAD_SUCCESS throws`() {
        try {
            OutcomeMapper.fromPullTaskResult(PullTaskResult.DOWNLOAD_SUCCESS, null)
            fail("expected IllegalStateException for DOWNLOAD_SUCCESS")
        } catch (_: IllegalStateException) {
        }
    }
}
