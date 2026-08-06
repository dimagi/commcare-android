package org.commcare.fragments.personalId

import android.view.View
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.android.util.FirebaseTestUtils
import org.commcare.dalvik.R
import org.commcare.utils.OtpErrorType
import org.commcare.utils.OtpManager
import org.commcare.utils.OtpVerificationCallback
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Runs above the project-wide sdk=23 default because the OTP screen inflates NumericCodeView,
 * which closes a TypedArray with try-with-resources; TypedArray only became AutoCloseable at API 31.
 */
@Config(application = CommCareTestApplication::class, sdk = [31])
@RunWith(AndroidJUnit4::class)
class PersonalIdPhoneVerificationFragmentTest : BasePersonalIdPhoneFragmentTest() {
    private lateinit var verificationFragment: PersonalIdPhoneVerificationFragment
    private lateinit var sessionData: PersonalIdSessionData

    @Before
    fun initializeFirebase() {
        FirebaseTestUtils.initializeDefaultAppIfNeeded()
    }

    private fun launchWith(
        smsMethod: String?,
        otpFallback: Boolean,
    ) {
        sessionData =
            PersonalIdSessionData().apply {
                this.smsMethod = smsMethod
                this.otpFallback = otpFallback
                this.phoneNumber = "+15555550123"
                // The PersonalID OTP API requires a session token from start configuration.
                this.token = "test-session-token"
            }
        ViewModelProvider(activity)[PersonalIdSessionDataViewModel::class.java]
            .personalIdSessionData = sessionData

        verificationFragment = PersonalIdPhoneVerificationFragment()
        activity.supportFragmentManager
            .beginTransaction()
            .add(verificationFragment, "otp")
            .commitNow()
    }

    private fun lastOtpMethod(): String? {
        val field =
            PersonalIdPhoneVerificationFragment::class.java
                .getDeclaredField("lastOtpMethod")
        field.isAccessible = true
        return field.get(verificationFragment) as String?
    }

    /**
     * The callback the fragment hands to OtpManager. Invoking it is what FirebaseAuthService does
     * from handleFirebaseException; there is no view interaction that can produce a Firebase error.
     */
    private fun otpCallback(): OtpVerificationCallback {
        val field =
            PersonalIdPhoneVerificationFragment::class.java
                .getDeclaredField("otpCallback")
        field.isAccessible = true
        return field.get(verificationFragment) as OtpVerificationCallback
    }

    private fun errorView() = verificationFragment.requireView().findViewById<TextView>(R.id.connect_phone_verify_error)

    private fun resendButton() = verificationFragment.requireView().findViewById<View>(R.id.connect_resend_button)

    @Test
    fun `session sms method of personal_id is tracked as personal_id not firebase`() {
        launchWith(OtpManager.SMS_METHOD_PERSONAL_ID, otpFallback = false)
        assertEquals(OtpManager.SMS_METHOD_PERSONAL_ID, lastOtpMethod())
    }

    @Test
    fun `session sms method of firebase is tracked as firebase`() {
        launchWith(OtpManager.SMS_METHOD_FIREBASE, otpFallback = true)
        assertEquals(OtpManager.SMS_METHOD_FIREBASE, lastOtpMethod())
    }

    @Test
    fun `non-recoverable firebase error switches to PersonalID and requests a new OTP`() {
        launchWith(OtpManager.SMS_METHOD_FIREBASE, otpFallback = true)
        val attemptsBefore = sessionData.otpAttempts

        otpCallback().onFailure(OtpErrorType.GENERIC_ERROR, null)

        assertEquals(OtpManager.SMS_METHOD_PERSONAL_ID, lastOtpMethod())
        assertEquals(attemptsBefore + 1, sessionData.otpAttempts)
    }

    @Test
    fun `every non-recoverable error type triggers the switch to PersonalID`() {
        OtpErrorType
            .entries
            .filter { it.isNonRecoverable() }
            .forEach { errorType ->
                launchWith(OtpManager.SMS_METHOD_FIREBASE, otpFallback = true)
                otpCallback().onFailure(errorType, null)
                assertEquals(
                    "$errorType should switch to PersonalID",
                    OtpManager.SMS_METHOD_PERSONAL_ID,
                    lastOtpMethod(),
                )
            }
    }

    @Test
    fun `non-recoverable error neither switches nor resends when fallback is not allowed`() {
        launchWith(OtpManager.SMS_METHOD_FIREBASE, otpFallback = false)
        val attemptsBefore = sessionData.otpAttempts

        otpCallback().onFailure(OtpErrorType.VERIFICATION_FAILED, null)

        assertEquals(OtpManager.SMS_METHOD_FIREBASE, lastOtpMethod())
        assertEquals(attemptsBefore, sessionData.otpAttempts)
        assertEquals(View.VISIBLE, errorView().visibility)
        assertEquals(
            activity.getString(R.string.personalid_otp_verification_failed),
            errorView().text.toString(),
        )
    }

    @Test
    fun `wrong OTP does not switch to PersonalID and reports an incorrect code`() {
        launchWith(OtpManager.SMS_METHOD_FIREBASE, otpFallback = true)
        val attemptsBefore = sessionData.otpAttempts

        otpCallback().onFailure(OtpErrorType.INVALID_CREDENTIAL, null)

        assertEquals(OtpManager.SMS_METHOD_FIREBASE, lastOtpMethod())
        assertEquals(attemptsBefore, sessionData.otpAttempts)
        assertEquals(
            activity.getString(R.string.personalid_incorrect_otp),
            errorView().text.toString(),
        )
    }

    @Test
    fun `resend after an auto-switch tries firebase again before falling back`() {
        launchWith(OtpManager.SMS_METHOD_FIREBASE, otpFallback = true)
        otpCallback().onFailure(OtpErrorType.GENERIC_ERROR, null)
        assertEquals(OtpManager.SMS_METHOD_PERSONAL_ID, lastOtpMethod())

        resendButton().performClick()

        assertEquals(OtpManager.SMS_METHOD_FIREBASE, lastOtpMethod())
    }
}
