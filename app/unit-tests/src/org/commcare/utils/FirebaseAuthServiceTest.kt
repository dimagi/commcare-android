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
}
