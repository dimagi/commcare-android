package org.commcare.fragments.personalId

import android.view.View
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.android.shadows.ShadowPhoneAuthProvider
import org.commcare.android.util.FirebaseTestUtils
import org.commcare.dalvik.R
import org.commcare.utils.OtpManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Drives the OTP request cycle end to end. A test supplies only the Firebase exception; everything
 * above it is production code — FirebaseAuthService classifies it and calls back into the fragment.
 * [ShadowPhoneAuthProvider] delivers the failure asynchronously, so the state before and after the
 * callback is separately observable via [ShadowLooper.idleMainLooper].
 *
 * Runs above the project-wide sdk=23 default because the OTP screen inflates NumericCodeView,
 * which closes a TypedArray with try-with-resources; TypedArray only became AutoCloseable at API 31.
 */
@Config(
    application = CommCareTestApplication::class,
    sdk = [31],
    shadows = [ShadowPhoneAuthProvider::class],
)
@RunWith(AndroidJUnit4::class)
class PersonalIdPhoneVerificationFragmentTest : BasePersonalIdPhoneFragmentTest() {
    private lateinit var verificationFragment: PersonalIdPhoneVerificationFragment
    private lateinit var sessionData: PersonalIdSessionData

    @Before
    fun initializeFirebase() {
        FirebaseTestUtils.initializeDefaultAppIfNeeded()
        ShadowPhoneAuthProvider.reset()
    }

    @After
    fun resetPhoneAuthProvider() {
        ShadowPhoneAuthProvider.reset()
    }

    private fun givenFirebaseFailsWith(e: FirebaseException) = ShadowPhoneAuthProvider.failWith(e)

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

    private fun errorView() = verificationFragment.requireView().findViewById<TextView>(R.id.connect_phone_verify_error)

    private fun resendButton() = verificationFragment.requireView().findViewById<View>(R.id.connect_resend_button)

    @Test
    fun `session sms method of personal_id is tracked as personal_id not firebase`() {
        launchWith(OtpManager.SMS_METHOD_PERSONAL_ID, otpFallback = false)
        assertEquals(OtpManager.SMS_METHOD_PERSONAL_ID, lastOtpMethod())
        assertEquals(0, ShadowPhoneAuthProvider.getRequestCount())
    }

    @Test
    fun `session sms method of firebase is tracked as firebase`() {
        launchWith(OtpManager.SMS_METHOD_FIREBASE, otpFallback = true)
        assertEquals(OtpManager.SMS_METHOD_FIREBASE, lastOtpMethod())
        assertEquals(1, ShadowPhoneAuthProvider.getRequestCount())
    }

    @Test
    fun `unmapped firebase failure switches to PersonalID and requests a new OTP`() {
        givenFirebaseFailsWith(FirebaseException("An internal error has occurred."))

        launchWith(OtpManager.SMS_METHOD_FIREBASE, otpFallback = true)
        assertEquals(OtpManager.SMS_METHOD_FIREBASE, lastOtpMethod())
        assertEquals(1, sessionData.otpAttempts)

        ShadowLooper.idleMainLooper()

        assertEquals(OtpManager.SMS_METHOD_PERSONAL_ID, lastOtpMethod())
        assertEquals(2, sessionData.otpAttempts)
    }

    @Test
    fun `every firebase failure that blocks delivery switches to PersonalID`() {
        listOf(
            FirebaseException("An internal error has occurred."),
            FirebaseNetworkException("A network error has occurred."),
            FirebaseTooManyRequestsException("This project's quota has been exceeded."),
            FirebaseAuthMissingActivityForRecaptchaException(),
            FirebaseAuthException("ERROR_APP_NOT_AUTHORIZED", "not authorized"),
        ).forEach { exception ->
            ShadowPhoneAuthProvider.reset()
            givenFirebaseFailsWith(exception)

            launchWith(OtpManager.SMS_METHOD_FIREBASE, otpFallback = true)
            ShadowLooper.idleMainLooper()

            assertEquals(
                "${exception.javaClass.simpleName} should switch to PersonalID",
                OtpManager.SMS_METHOD_PERSONAL_ID,
                lastOtpMethod(),
            )
        }
    }

    @Test
    fun `firebase failure neither switches nor re-requests when fallback is not allowed`() {
        givenFirebaseFailsWith(FirebaseAuthException("ERROR_APP_NOT_AUTHORIZED", "not authorized"))

        launchWith(OtpManager.SMS_METHOD_FIREBASE, otpFallback = false)
        ShadowLooper.idleMainLooper()

        assertEquals(OtpManager.SMS_METHOD_FIREBASE, lastOtpMethod())
        assertEquals(1, sessionData.otpAttempts)
        assertEquals(1, ShadowPhoneAuthProvider.getRequestCount())
        assertEquals(View.VISIBLE, errorView().visibility)
        assertEquals(
            activity.getString(R.string.personalid_otp_verification_failed),
            errorView().text.toString(),
        )
    }

    /**
     * A rejected phone number is the request-path form of INVALID_CREDENTIAL, and stays recoverable
     * so the user can correct it. A wrong verification code maps to the same type; that path runs
     * through signInWithCredential and is covered by FirebaseAuthServiceTest.
     */
    @Test
    fun `rejected phone number is reported to the user without switching to PersonalID`() {
        givenFirebaseFailsWith(
            FirebaseAuthInvalidCredentialsException(
                "ERROR_INVALID_PHONE_NUMBER",
                "The format of the phone number provided is incorrect.",
            ),
        )

        launchWith(OtpManager.SMS_METHOD_FIREBASE, otpFallback = true)
        ShadowLooper.idleMainLooper()

        assertEquals(OtpManager.SMS_METHOD_FIREBASE, lastOtpMethod())
        assertEquals(1, sessionData.otpAttempts)
        assertEquals(1, ShadowPhoneAuthProvider.getRequestCount())
        assertEquals(
            activity.getString(R.string.personalid_incorrect_otp),
            errorView().text.toString(),
        )
    }

    @Test
    fun `resend after an auto-switch tries firebase again before falling back`() {
        givenFirebaseFailsWith(FirebaseTooManyRequestsException("quota exceeded"))

        launchWith(OtpManager.SMS_METHOD_FIREBASE, otpFallback = true)
        assertEquals(OtpManager.SMS_METHOD_FIREBASE, lastOtpMethod())
        assertEquals(1, ShadowPhoneAuthProvider.getRequestCount())

        ShadowLooper.idleMainLooper()
        assertEquals(OtpManager.SMS_METHOD_PERSONAL_ID, lastOtpMethod())
        assertEquals(2, sessionData.otpAttempts)

        resendButton().performClick()
        assertEquals(OtpManager.SMS_METHOD_FIREBASE, lastOtpMethod())
        assertEquals(2, ShadowPhoneAuthProvider.getRequestCount())

        ShadowLooper.idleMainLooper()
        assertEquals(OtpManager.SMS_METHOD_PERSONAL_ID, lastOtpMethod())
        assertEquals(4, sessionData.otpAttempts)
    }
}
