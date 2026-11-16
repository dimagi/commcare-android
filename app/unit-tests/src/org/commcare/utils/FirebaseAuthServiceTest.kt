package org.commcare.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException
import org.commcare.CommCareTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers the Firebase-exception-to-[OtpErrorType] classification that decides whether the OTP
 * screen falls back to PersonalID. Neither FirebaseNetworkException nor FirebaseTooManyRequestsException
 * extends FirebaseAuthException, so both bypass the FirebaseAuthException branch entirely.
 *
 * Needs Robolectric because the Firebase exception constructors validate their arguments through
 * android.text.TextUtils, which is not available to a plain JVM test.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class FirebaseAuthServiceTest {
    @Test
    fun `invalid verification code maps to INVALID_CREDENTIAL`() {
        assertEquals(
            OtpErrorType.INVALID_CREDENTIAL,
            FirebaseAuthService.errorTypeFrom(
                FirebaseAuthInvalidCredentialsException(
                    "ERROR_INVALID_VERIFICATION_CODE",
                    "The verification code from SMS/TOTP is invalid.",
                ),
            ),
        )
    }

    @Test
    fun `expired verification session maps to SESSION_EXPIRED`() {
        assertEquals(
            OtpErrorType.SESSION_EXPIRED,
            FirebaseAuthService.errorTypeFrom(
                FirebaseAuthInvalidCredentialsException(
                    "ERROR_SESSION_EXPIRED",
                    "The sms code has expired. Please re-send the verification code to try again.",
                ),
            ),
        )
    }

    @Test
    fun `an expired code is classified as recoverable and does not trigger the fallback`() {
        val errorType =
            FirebaseAuthService.errorTypeFrom(
                FirebaseAuthInvalidCredentialsException("ERROR_SESSION_EXPIRED", "expired"),
            )
        assertEquals(false, errorType.isNonRecoverable)
    }

    @Test
    fun `exhausted SMS quota maps to TOO_MANY_REQUESTS`() {
        assertEquals(
            OtpErrorType.TOO_MANY_REQUESTS,
            FirebaseAuthService.errorTypeFrom(
                FirebaseTooManyRequestsException("This project's quota for this operation has been exceeded."),
            ),
        )
    }

    @Test
    fun `missing activity for recaptcha maps to MISSING_ACTIVITY`() {
        assertEquals(
            OtpErrorType.MISSING_ACTIVITY,
            FirebaseAuthService.errorTypeFrom(FirebaseAuthMissingActivityForRecaptchaException()),
        )
    }

    @Test
    fun `app not authorized maps to VERIFICATION_FAILED`() {
        assertEquals(
            OtpErrorType.VERIFICATION_FAILED,
            FirebaseAuthService.errorTypeFrom(
                FirebaseAuthException(
                    "ERROR_APP_NOT_AUTHORIZED",
                    "This app is not authorized to use Firebase Authentication.",
                ),
            ),
        )
    }

    @Test
    fun `network failure maps to GENERIC_ERROR`() {
        assertEquals(
            OtpErrorType.GENERIC_ERROR,
            FirebaseAuthService.errorTypeFrom(FirebaseNetworkException("A network error has occurred.")),
        )
    }

    /**
     * Firebase returns a plain FirebaseException for backend codes it has no mapping for, which is
     * how conditions such as disabled billing or a blocked SMS region reach the app.
     */
    @Test
    fun `unmapped backend failure maps to GENERIC_ERROR`() {
        assertEquals(
            OtpErrorType.GENERIC_ERROR,
            FirebaseAuthService.errorTypeFrom(FirebaseException("An internal error has occurred.")),
        )
    }

    @Test
    fun `null exception maps to GENERIC_ERROR`() {
        assertEquals(OtpErrorType.GENERIC_ERROR, FirebaseAuthService.errorTypeFrom(null))
    }

    /**
     * FirebaseAuthInvalidCredentialsException extends FirebaseAuthException, so only the ordering of
     * the checks keeps a wrong code out of the non-recoverable bucket that triggers the fallback.
     */
    @Test
    fun `a wrong code is classified as recoverable and does not trigger the fallback`() {
        val errorType =
            FirebaseAuthService.errorTypeFrom(
                FirebaseAuthInvalidCredentialsException("ERROR_INVALID_VERIFICATION_CODE", "bad code"),
            )
        assertEquals(false, errorType.isNonRecoverable())
    }

    @Test
    fun `every failure other than a bad credential is non-recoverable`() {
        listOf(
            FirebaseTooManyRequestsException("quota"),
            FirebaseAuthMissingActivityForRecaptchaException(),
            FirebaseAuthException("ERROR_APP_NOT_AUTHORIZED", "not authorized"),
            FirebaseNetworkException("network"),
            FirebaseException("An internal error has occurred."),
        ).forEach {
            val errorType = FirebaseAuthService.errorTypeFrom(it)
            assertEquals(
                "${it.javaClass.simpleName} should be non-recoverable but mapped to $errorType",
                true,
                errorType.isNonRecoverable(),
            )
        }
    }

    @Test
    fun `an auth failure reports its Firebase error code`() {
        assertEquals(
            "ERROR_APP_NOT_AUTHORIZED",
            FirebaseAuthService.errorCodeFrom(
                FirebaseAuthException("ERROR_APP_NOT_AUTHORIZED", "not authorized"),
            ),
        )
    }

    @Test
    fun `an invalid credential reports its Firebase error code`() {
        assertEquals(
            "ERROR_INVALID_VERIFICATION_CODE",
            FirebaseAuthService.errorCodeFrom(
                FirebaseAuthInvalidCredentialsException("ERROR_INVALID_VERIFICATION_CODE", "bad code"),
            ),
        )
    }

    @Test
    fun `a failure without an auth error code reports its exception class`() {
        assertEquals(
            "FirebaseNetworkException",
            FirebaseAuthService.errorCodeFrom(FirebaseNetworkException("network")),
        )
        assertEquals(
            "FirebaseTooManyRequestsException",
            FirebaseAuthService.errorCodeFrom(FirebaseTooManyRequestsException("quota")),
        )
        assertEquals(
            "FirebaseException",
            FirebaseAuthService.errorCodeFrom(FirebaseException("An internal error has occurred.")),
        )
    }

    @Test
    fun `an unmapped backend failure reports the code Firebase puts in brackets`() {
        assertEquals(
            "BILLING_NOT_ENABLED",
            FirebaseAuthService.errorCodeFrom(
                FirebaseException("An internal error has occurred. [ BILLING_NOT_ENABLED ]"),
            ),
        )
    }

    @Test
    fun `a classified failure keeps its class name even with a bracketed message`() {
        assertEquals(
            "FirebaseTooManyRequestsException",
            FirebaseAuthService.errorCodeFrom(
                FirebaseTooManyRequestsException("We have blocked all requests. [ Error code:17010 ]"),
            ),
        )
    }

    @Test
    fun `an error code longer than GA4 allows is cut to the limit`() {
        val longCode = "X".repeat(FirebaseAuthService.MAX_ERROR_CODE_LENGTH + 20)
        assertEquals(
            longCode.take(FirebaseAuthService.MAX_ERROR_CODE_LENGTH),
            FirebaseAuthService.errorCodeFrom(FirebaseException("Failed. [ $longCode ]")),
        )
    }

    @Test
    fun `an anonymous exception class reports its full class name`() {
        val exception = object : RuntimeException("boom") {}
        assertEquals(
            exception.javaClass.name.take(FirebaseAuthService.MAX_ERROR_CODE_LENGTH),
            FirebaseAuthService.errorCodeFrom(exception),
        )
    }

    @Test
    fun `no exception reports no error code`() {
        assertNull(FirebaseAuthService.errorCodeFrom(null))
    }
}
