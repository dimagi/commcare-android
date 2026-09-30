package org.commcare.connect.network.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.dalvik.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers the messages that depend on more than the error code alone: the rate-limit wait the
 * server supplies, and the out-of-attempts code introduced alongside it.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdOrConnectApiErrorHandlerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `running out of OTP attempts is reported as a code to replace, not as a lockout`() {
        assertEquals(
            context.getString(R.string.personalid_otp_limit_exceeded),
            getHandledMessage(PersonalIdOrConnectApiErrorCodes.OTP_LIMIT_EXCEEDED_ERROR, null),
        )
    }

    @Test
    fun `a rate limit under an hour is reported in minutes`() {
        assertEquals(
            getExpectedRateLimitMessage(R.plurals.personalid_otp_retry_after_minutes, 4),
            getHandledMessage(
                PersonalIdOrConnectApiErrorCodes.RATE_LIMIT_EXCEEDED_ERROR,
                RateLimitedException(240),
            ),
        )
    }

    @Test
    fun `a rate limit of an hour or more is reported in hours`() {
        assertEquals(
            getExpectedRateLimitMessage(R.plurals.personalid_otp_retry_after_hours, 4),
            getHandledMessage(
                PersonalIdOrConnectApiErrorCodes.RATE_LIMIT_EXCEEDED_ERROR,
                RateLimitedException(4 * 60 * 60),
            ),
        )
    }

    @Test
    fun `a sub-minute rate limit is reported in seconds`() {
        assertEquals(
            getExpectedRateLimitMessage(R.plurals.personalid_otp_retry_after_seconds, 5),
            getHandledMessage(
                PersonalIdOrConnectApiErrorCodes.RATE_LIMIT_EXCEEDED_ERROR,
                RateLimitedException(5),
            ),
        )
    }

    @Test
    fun `a rate limit with no wait supplied falls back to the generic cooldown message`() {
        assertEquals(
            context.getString(R.string.recovery_network_cooldown),
            getHandledMessage(PersonalIdOrConnectApiErrorCodes.RATE_LIMIT_EXCEEDED_ERROR, null),
        )
    }

    @Test
    fun `a rate limit the server gave no wait for falls back to the generic cooldown message`() {
        assertEquals(
            context.getString(R.string.recovery_network_cooldown),
            getHandledMessage(
                PersonalIdOrConnectApiErrorCodes.RATE_LIMIT_EXCEEDED_ERROR,
                RateLimitedException(null),
            ),
        )
    }

    @Test
    fun `a rate limit carrying an unrelated throwable falls back to the generic cooldown message`() {
        assertEquals(
            context.getString(R.string.recovery_network_cooldown),
            getHandledMessage(
                PersonalIdOrConnectApiErrorCodes.RATE_LIMIT_EXCEEDED_ERROR,
                RuntimeException("boom"),
            ),
        )
    }

    private fun getHandledMessage(
        errorCode: PersonalIdOrConnectApiErrorCodes,
        throwable: Throwable?,
    ) = PersonalIdOrConnectApiErrorHandler.handle(context, errorCode, throwable)

    private fun getExpectedRateLimitMessage(
        pluralsResId: Int,
        quantity: Int,
    ) = context.getString(
        R.string.personalid_rate_limited_retry_after,
        context.resources.getQuantityString(pluralsResId, quantity, quantity),
    )
}
