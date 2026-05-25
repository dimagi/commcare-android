package org.commcare.login

import org.commcare.network.HttpCalloutTask.HttpCalloutOutcomes
import org.commcare.tasks.DataPullTask.PullTaskResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutcomeMapperTest {
    @Test
    fun `AuthFailed and IncorrectPin map to BadCredentials`() {
        assertEquals(LoginError.BadCredentials, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.AuthFailed))
        assertEquals(LoginError.BadCredentials, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.IncorrectPin))
    }

    @Test
    fun `network-class outcomes map to NetworkUnavailable`() {
        listOf(
            HttpCalloutOutcomes.NetworkFailure,
            HttpCalloutOutcomes.NetworkFailureBadPassword,
            HttpCalloutOutcomes.CaptivePortal,
            HttpCalloutOutcomes.TokenUnavailable,
        ).forEach { outcome ->
            assertEquals(LoginError.NetworkUnavailable, OutcomeMapper.fromHttpCalloutOutcome(outcome))
        }
    }

    @Test
    fun `TokenRequestDenied maps to TokenDenied`() {
        assertEquals(LoginError.TokenDenied, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.TokenRequestDenied))
    }

    @Test
    fun `AuthOverHttp maps to AuthOverHttpBlocked`() {
        assertEquals(LoginError.AuthOverHttpBlocked, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.AuthOverHttp))
    }

    @Test
    fun `unmapped http outcomes fall through to SyncFailed`() {
        val result = OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.BadResponse)
        assertTrue(result is LoginError.SyncFailed)
        assertEquals("BadResponse", (result as LoginError.SyncFailed).reason)
    }

    @Test
    fun `BAD_DATA and BAD_DATA_REQUIRES_INTERVENTION map to SyncFailed with message`() {
        val a = OutcomeMapper.fromPullTaskResult(PullTaskResult.BAD_DATA, "broken xml")
        val b = OutcomeMapper.fromPullTaskResult(PullTaskResult.BAD_DATA_REQUIRES_INTERVENTION, "missing case")
        assertEquals(LoginError.SyncFailed("BAD_DATA", "broken xml"), a)
        assertEquals(LoginError.SyncFailed("BAD_DATA_REQUIRES_INTERVENTION", "missing case"), b)
    }

    @Test
    fun `AUTH_FAILED pull result maps to BadCredentials`() {
        assertEquals(LoginError.BadCredentials, OutcomeMapper.fromPullTaskResult(PullTaskResult.AUTH_FAILED, null))
    }

    @Test
    fun `TOKEN_DENIED pull result maps to TokenDenied`() {
        assertEquals(LoginError.TokenDenied, OutcomeMapper.fromPullTaskResult(PullTaskResult.TOKEN_DENIED, null))
    }

    @Test
    fun `AUTH_OVER_HTTP pull result maps to AuthOverHttpBlocked`() {
        assertEquals(LoginError.AuthOverHttpBlocked, OutcomeMapper.fromPullTaskResult(PullTaskResult.AUTH_OVER_HTTP, null))
    }

    @Test(expected = IllegalStateException::class)
    fun `Success input throws`() {
        OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.Success)
    }

    @Test(expected = IllegalStateException::class)
    fun `DOWNLOAD_SUCCESS input throws`() {
        OutcomeMapper.fromPullTaskResult(PullTaskResult.DOWNLOAD_SUCCESS, null)
    }
}
