package org.commcare.fragments.personalId

import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.navigation.fragment.NavHostFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.android.shadows.ShadowPhoneAuthProvider
import org.commcare.android.util.FirebaseTestUtils
import org.commcare.dalvik.R
import org.commcare.utils.OtpManager
import org.commcare.views.connect.NumericCodeView
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers

/**
 * Drives the OTP request cycle end to end. A test supplies only the Firebase outcome; everything
 * above it is production code — FirebaseAuthService classifies it and calls back into the fragment.
 * [ShadowPhoneAuthProvider] delivers that outcome asynchronously, so a resend can be observed both
 * before and after its callback lands. The launch-time request is already settled by the time
 * launchWith returns, because navigateToFragment idles the looper itself.
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
class PersonalIdPhoneVerificationFragmentTest : BasePersonalIdConfigurationTest<PersonalIdPhoneVerificationFragment>() {
    private lateinit var sessionData: PersonalIdSessionData

    @Before
    @CallSuper
    override fun setUp() {
        super.setUp()
        FirebaseTestUtils.initializeDefaultAppIfNeeded()
        ShadowPhoneAuthProvider.reset()
    }

    @CallSuper
    override fun tearDown() {
        ShadowPhoneAuthProvider.reset()
        activityController.pause().stop().destroy()
        super.tearDown()
    }

    /**
     * The Firebase outcome has to be staged before launching, because the fragment requests an OTP
     * from onCreateView.
     */
    private fun launchWith(
        smsMethod: String?,
        otpFallback: Boolean,
    ) {
        sessionData =
            PersonalIdSessionData(
                smsMethod = smsMethod,
                otpFallback = otpFallback,
                phoneNumber = "+15555550123",
                token = "test-session-token",
            )
        navigateToFragment(sessionData, R.id.personalid_otp_page)
    }

    private fun givenFirebaseFailsWith(e: FirebaseException) = ShadowPhoneAuthProvider.failWith(e)

    /**
     * Replaces the FirebaseAuth held by the live FirebaseAuthService so signInWithCredential and
     * getIdToken resolve successfully. Read at call time, so injecting after launch is enough.
     */
    private fun stubFirebaseSignInWith(idToken: String) {
        val otpManager = ReflectionHelpers.getField<Any>(fragment, "otpManager")
        val authService = ReflectionHelpers.getField<Any>(otpManager, "authService")

        val tokenResult = mock(GetTokenResult::class.java)
        `when`(tokenResult.token).thenReturn(idToken)

        val user = mock(FirebaseUser::class.java)
        `when`(user.getIdToken(false)).thenReturn(Tasks.forResult(tokenResult))

        val authResult = mock(AuthResult::class.java)
        `when`(authResult.user).thenReturn(user)

        val firebaseAuth = mock(FirebaseAuth::class.java)
        `when`(firebaseAuth.signInWithCredential(any())).thenReturn(Tasks.forResult(authResult))

        ReflectionHelpers.setField(authService, "firebaseAuth", firebaseAuth)
    }

    private fun lastOtpMethod(): String? = ReflectionHelpers.getField(fragment, "lastOtpMethod")

    private fun codeView() = fragment.requireView().findViewById<NumericCodeView>(R.id.customOtpView)

    private fun verifyButton() = fragment.requireView().findViewById<Button>(R.id.connect_phone_verify_button)

    private fun errorView() = fragment.requireView().findViewById<TextView>(R.id.connect_phone_verify_error)

    private fun resendButton() = fragment.requireView().findViewById<View>(R.id.connect_resend_button)

    /**
     * Launches on the PersonalID (Twilio) path, which is the only one that talks to
     * confirm_session_otp. The launch-time send_session_otp call needs its own queued response or
     * the mock server leaves it hanging.
     */
    private fun launchOnPersonalIdPath() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        launchWith(OtpManager.SMS_METHOD_PERSONAL_ID, otpFallback = false)
        drainHttp()
    }

    /** Rebuilds the activity from saved state, as a rotation would, and re-resolves the fragment. */
    private fun recreateFragment() {
        activityController.recreate()
        activity = activityController.get()
        navHostFragment =
            activity.supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_connectid) as NavHostFragment
        captureNavFragment()
        ShadowLooper.idleMainLooper()
    }

    /** Types a code, hits Verify, and lets [response] come back from confirm_session_otp. */
    private fun submitCodeAgainst(response: MockResponse) {
        mockWebServer.enqueue(response)
        activity.runOnUiThread {
            codeView().setCode("123456")
            verifyButton().performClick()
        }
        drainHttp()
    }

    private fun incorrectOtpResponse(): MockResponse =
        MockResponse()
            .setResponseCode(401)
            .setBody("""{"error":"INCORRECT_OTP"}""")

    private fun otpLimitExceededResponse(): MockResponse =
        MockResponse()
            .setResponseCode(401)
            .setBody("""{"error_code":"OTP_LIMIT_EXCEEDED"}""")

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
    fun `firebase reporting the code was sent leaves the user on Firebase with no error`() {
        ShadowPhoneAuthProvider.sendCodeWith("test-verification-id")

        launchWith(OtpManager.SMS_METHOD_FIREBASE, otpFallback = true)
        ShadowLooper.idleMainLooper()

        assertEquals(OtpManager.SMS_METHOD_FIREBASE, lastOtpMethod())
        assertEquals(1, ShadowPhoneAuthProvider.getRequestCount())
        assertEquals(1, sessionData.otpAttempts)
        assertEquals(View.GONE, errorView().visibility)
        assertEquals(activity.getString(R.string.connect_otp_sent), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `unmapped firebase failure switches to PersonalID and requests a new OTP`() {
        givenFirebaseFailsWith(FirebaseException("An internal error has occurred."))

        launchWith(OtpManager.SMS_METHOD_FIREBASE, otpFallback = true)

        assertEquals(OtpManager.SMS_METHOD_PERSONAL_ID, lastOtpMethod())
        assertEquals(1, ShadowPhoneAuthProvider.getRequestCount())
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

    /**
     * onCodeVerified is Firebase's local verification step, reached only once signInWithCredential
     * and getIdToken both resolve -- hence the stubbed FirebaseAuth. FirebaseAuthService submits the
     * token on the line straight after, which the base class's mock server absorbs.
     */
    @Test
    fun `verifying a firebase code shows the verified acknowledgement`() {
        ShadowPhoneAuthProvider.sendCodeWith("test-verification-id")

        launchWith(OtpManager.SMS_METHOD_FIREBASE, otpFallback = true)
        ShadowLooper.idleMainLooper()
        stubFirebaseSignInWith("test-id-token")

        codeView().setCode("123456")
        verifyButton().performClick()
        ShadowLooper.idleMainLooper()

        assertEquals(
            activity.getString(R.string.connect_otp_verified),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    // ========== OTP_LIMIT_EXCEEDED ==========

    /**
     * Only the PersonalID (Twilio) path talks to confirm_session_otp, so OTP_LIMIT_EXCEEDED can
     * only reach this screen there; the Firebase path never sees it.
     */
    @Test
    fun `running out of attempts tells the user to request a new code rather than reporting a wrong one`() {
        launchOnPersonalIdPath()
        submitCodeAgainst(otpLimitExceededResponse())

        assertEquals(View.VISIBLE, errorView().visibility)
        assertEquals(
            activity.getString(R.string.personalid_otp_limit_exceeded),
            errorView().text.toString(),
        )
    }

    @Test
    fun `a code that has run out of attempts is cleared and resend is offered straight away`() {
        launchOnPersonalIdPath()
        submitCodeAgainst(otpLimitExceededResponse())

        assertEquals("The dead code should not be left in the field", "", codeView().codeValue)
        assertEquals(
            "Resend should be offered without waiting out the two-minute cooldown",
            View.VISIBLE,
            resendButton().visibility,
        )
    }

    @Test
    fun `a wrong code is still reported as a wrong code and holds the resend cooldown`() {
        launchOnPersonalIdPath()
        submitCodeAgainst(incorrectOtpResponse())

        assertEquals(
            activity.getString(R.string.personalid_incorrect_otp),
            errorView().text.toString(),
        )
        assertEquals(View.GONE, resendButton().visibility)
    }

    @Test
    fun `a rate-limited request holds resend for the wait the server reported`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error_code":"RATE_LIMITED","retry_after_seconds":7200}"""),
        )
        launchWith(OtpManager.SMS_METHOD_PERSONAL_ID, otpFallback = false)
        drainHttp()

        assertEquals(
            "Resend must stay hidden for the server's wait, not the local two minutes",
            View.GONE,
            resendButton().visibility,
        )
        assertEquals(
            activity.getString(
                R.string.personalid_otp_resend_wait,
                activity.resources.getQuantityString(R.plurals.personalid_otp_retry_after_hours, 2, 2),
            ),
            fragment
                .requireView()
                .findViewById<TextView>(R.id.connect_phone_verify_resend)
                .text
                .toString(),
        )
    }

    @Test
    fun `a skipped cooldown survives a configuration change`() {
        launchOnPersonalIdPath()
        submitCodeAgainst(otpLimitExceededResponse())

        recreateFragment()

        assertEquals(
            "Running out of attempts must not put the user back behind the two-minute cooldown",
            View.VISIBLE,
            resendButton().visibility,
        )
    }

    @Test
    fun `a running cooldown survives a configuration change`() {
        launchOnPersonalIdPath()

        recreateFragment()

        assertEquals(
            "The cooldown from the launch-time request should still be running",
            View.GONE,
            resendButton().visibility,
        )
    }

    @Test
    fun `resending after running out of attempts puts the cooldown back`() {
        launchOnPersonalIdPath()
        submitCodeAgainst(otpLimitExceededResponse())

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        activity.runOnUiThread { resendButton().performClick() }
        drainHttp()

        assertEquals(
            "A fresh code restarts the wait, so resend hides again",
            View.GONE,
            resendButton().visibility,
        )
    }

    @Test
    fun `resend after an auto-switch tries firebase again before falling back`() {
        givenFirebaseFailsWith(FirebaseTooManyRequestsException("quota exceeded"))

        launchWith(OtpManager.SMS_METHOD_FIREBASE, otpFallback = true)
        assertEquals(OtpManager.SMS_METHOD_PERSONAL_ID, lastOtpMethod())
        assertEquals(1, ShadowPhoneAuthProvider.getRequestCount())
        assertEquals(2, sessionData.otpAttempts)

        resendButton().performClick()
        assertEquals(OtpManager.SMS_METHOD_FIREBASE, lastOtpMethod())
        assertEquals(2, ShadowPhoneAuthProvider.getRequestCount())

        ShadowLooper.idleMainLooper()
        assertEquals(OtpManager.SMS_METHOD_PERSONAL_ID, lastOtpMethod())
        assertEquals(4, sessionData.otpAttempts)
    }
}
