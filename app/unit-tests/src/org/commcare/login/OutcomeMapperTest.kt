package org.commcare.login

import org.commcare.network.HttpCalloutTask.HttpCalloutOutcomes
import org.commcare.tasks.DataPullTask.PullTaskResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OutcomeMapperTest {
    private val detail = "server detail"

    // --- fromHttpCalloutOutcome ---

    @Test
    fun `http auth-class outcomes map to BadCredentials`() {
        assertEquals(LoginError.BadCredentials, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.AuthFailed))
        assertEquals(LoginError.BadCredentials, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.IncorrectPin))
    }

    @Test
    fun `http network-class outcomes map to NetworkUnavailable`() {
        assertEquals(LoginError.NetworkUnavailable, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.NetworkFailure))
        assertEquals(LoginError.NetworkUnavailable, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.NetworkFailureBadPassword))
        assertEquals(LoginError.NetworkUnavailable, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.CaptivePortal))
        assertEquals(LoginError.NetworkUnavailable, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.TokenUnavailable))
    }

    @Test
    fun `http TokenRequestDenied maps to TokenDenied`() {
        assertEquals(LoginError.TokenDenied, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.TokenRequestDenied))
    }

    @Test
    fun `http AuthOverHttp maps to AuthOverHttpBlocked`() {
        assertEquals(LoginError.AuthOverHttpBlocked, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.AuthOverHttp))
    }

    @Test
    fun `http BadResponse maps to BadResponse`() {
        assertEquals(LoginError.BadResponse, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.BadResponse))
    }

    @Test
    fun `http BadSslCertificate maps to BadSslCertificate`() {
        assertEquals(LoginError.BadSslCertificate, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.BadSslCertificate))
    }

    @Test
    fun `http InsufficientRolePermission maps to InsufficientRolePermission`() {
        assertEquals(
            LoginError.InsufficientRolePermission,
            OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.InsufficientRolePermission),
        )
    }

    @Test
    fun `http UnknownError maps to UnknownFailure with no message`() {
        assertEquals(LoginError.UnknownFailure(null), OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.UnknownError))
    }

    @Test
    fun `http Success is rejected as not a failure outcome`() {
        assertThrows(IllegalStateException::class.java) {
            OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.Success)
        }
    }

    // --- fromPullTaskResult ---

    @Test
    fun `pull AUTH_FAILED maps to BadCredentials`() {
        assertEquals(LoginError.BadCredentials, OutcomeMapper.fromPullTaskResult(PullTaskResult.AUTH_FAILED, detail))
    }

    @Test
    fun `pull TOKEN_DENIED maps to TokenDenied`() {
        assertEquals(LoginError.TokenDenied, OutcomeMapper.fromPullTaskResult(PullTaskResult.TOKEN_DENIED, detail))
    }

    @Test
    fun `pull AUTH_OVER_HTTP maps to AuthOverHttpBlocked`() {
        assertEquals(LoginError.AuthOverHttpBlocked, OutcomeMapper.fromPullTaskResult(PullTaskResult.AUTH_OVER_HTTP, detail))
    }

    @Test
    fun `pull network-class results map to NetworkUnavailable`() {
        assertEquals(LoginError.NetworkUnavailable, OutcomeMapper.fromPullTaskResult(PullTaskResult.UNREACHABLE_HOST, detail))
        assertEquals(LoginError.NetworkUnavailable, OutcomeMapper.fromPullTaskResult(PullTaskResult.CONNECTION_TIMEOUT, detail))
        assertEquals(LoginError.NetworkUnavailable, OutcomeMapper.fromPullTaskResult(PullTaskResult.CAPTIVE_PORTAL, detail))
        assertEquals(LoginError.NetworkUnavailable, OutcomeMapper.fromPullTaskResult(PullTaskResult.TOKEN_UNAVAILABLE, detail))
    }

    @Test
    fun `pull STORAGE_FULL maps to StorageFull`() {
        assertEquals(LoginError.StorageFull, OutcomeMapper.fromPullTaskResult(PullTaskResult.STORAGE_FULL, detail))
    }

    @Test
    fun `pull SERVER_ERROR maps to ServerError`() {
        assertEquals(LoginError.ServerError, OutcomeMapper.fromPullTaskResult(PullTaskResult.SERVER_ERROR, detail))
    }

    @Test
    fun `pull RATE_LIMITED_SERVER_ERROR maps to RateLimitedServerError`() {
        assertEquals(
            LoginError.RateLimitedServerError,
            OutcomeMapper.fromPullTaskResult(PullTaskResult.RATE_LIMITED_SERVER_ERROR, detail),
        )
    }

    @Test
    fun `pull SESSION_EXPIRE maps to SessionExpire`() {
        assertEquals(LoginError.SessionExpire, OutcomeMapper.fromPullTaskResult(PullTaskResult.SESSION_EXPIRE, detail))
    }

    @Test
    fun `pull CANCELLED maps to Cancelled`() {
        assertEquals(LoginError.Cancelled, OutcomeMapper.fromPullTaskResult(PullTaskResult.CANCELLED, detail))
    }

    @Test
    fun `pull EMPTY_URL maps to EmptyUrl`() {
        assertEquals(LoginError.EmptyUrl, OutcomeMapper.fromPullTaskResult(PullTaskResult.EMPTY_URL, detail))
    }

    @Test
    fun `pull message-carrying results thread the error message through`() {
        assertEquals(LoginError.BadData(detail), OutcomeMapper.fromPullTaskResult(PullTaskResult.BAD_DATA, detail))
        assertEquals(
            LoginError.BadDataRequiresIntervention(detail),
            OutcomeMapper.fromPullTaskResult(PullTaskResult.BAD_DATA_REQUIRES_INTERVENTION, detail),
        )
        assertEquals(
            LoginError.EncryptionFailure(detail),
            OutcomeMapper.fromPullTaskResult(PullTaskResult.ENCRYPTION_FAILURE, detail),
        )
        assertEquals(LoginError.RecoveryFailure(detail), OutcomeMapper.fromPullTaskResult(PullTaskResult.RECOVERY_FAILURE, detail))
        assertEquals(
            LoginError.ActionableFailure(detail),
            OutcomeMapper.fromPullTaskResult(PullTaskResult.ACTIONABLE_FAILURE, detail),
        )
    }

    @Test
    fun `pull message-carrying results preserve a null error message`() {
        assertEquals(LoginError.BadData(null), OutcomeMapper.fromPullTaskResult(PullTaskResult.BAD_DATA, null))
    }

    @Test
    fun `pull unknown-class results map to UnknownFailure carrying the message`() {
        assertEquals(LoginError.UnknownFailure(detail), OutcomeMapper.fromPullTaskResult(PullTaskResult.UNKNOWN_FAILURE, detail))
        assertEquals(LoginError.UnknownFailure(detail), OutcomeMapper.fromPullTaskResult(PullTaskResult.RETRY_NEEDED, detail))
        assertEquals(LoginError.UnknownFailure(detail), OutcomeMapper.fromPullTaskResult(PullTaskResult.BAD_CERTIFICATE, detail))
    }

    @Test
    fun `pull DOWNLOAD_SUCCESS is rejected as not a failure outcome`() {
        assertThrows(IllegalStateException::class.java) {
            OutcomeMapper.fromPullTaskResult(PullTaskResult.DOWNLOAD_SUCCESS, detail)
        }
    }
}
