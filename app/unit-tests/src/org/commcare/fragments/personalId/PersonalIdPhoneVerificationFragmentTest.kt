package org.commcare.fragments.personalId

import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
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
import okhttp3.mockwebserver.MockWebServer
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.android.shadows.ShadowPhoneAuthProvider
import org.commcare.android.util.FirebaseTestUtils
import org.commcare.connect.network.ApiService
import org.commcare.connect.network.base.BaseApiClient
import org.commcare.connect.network.connectId.PersonalIdApiClient
import org.commcare.dalvik.R
import org.commcare.utils.OtpManager
import org.commcare.views.connect.NumericCodeView
import org.junit.After
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
    private var mockWebServer: MockWebServer? = null

    @Before
    fun initializeFirebase() {
        FirebaseTestUtils.initializeDefaultAppIfNeeded()
        ShadowPhoneAuthProvider.reset()
    }

    @After
    fun resetPhoneAuthProvider() {
        ShadowPhoneAuthProvider.reset()
        mockWebServer?.let {
            apiServiceField().set(null, null)
            it.shutdown()
            mockWebServer = null
        }
    }

    private fun givenFirebaseFailsWith(e: FirebaseException) = ShadowPhoneAuthProvider.failWith(e)

    private fun apiServiceField() = PersonalIdApiClient::class.java.getDeclaredField("apiService").apply { isAccessible = true }

    /**
     * Points the PersonalID API at a MockWebServer. Started per test rather than for the whole class
     * so the failure tests keep reaching the unmodified client.
     */
    private fun givenPersonalIdApiIsMocked(): MockWebServer {
        val server = MockWebServer()
        server.start()
        val apiService =
            BaseApiClient
                .buildRetrofitClient(server.url("/").toString(), PersonalIdApiClient.API_VERSION)
                .create(ApiService::class.java)
        apiServiceField().set(null, apiService)
        mockWebServer = server
        return server
    }

    /**
     * Replaces the FirebaseAuth held by the live FirebaseAuthService so signInWithCredential and
     * getIdToken resolve successfully. Reads the field at call time, so injecting after the fragment
     * is created is enough.
     */
    private fun stubFirebaseSignInWith(idToken: String) {
        val otpManager = ReflectionHelpers.getField<Any>(verificationFragment, "otpManager")
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

    private fun codeView() = verificationFragment.requireView().findViewById<NumericCodeView>(R.id.customOtpView)

    private fun verifyButton() = verificationFragment.requireView().findViewById<Button>(R.id.connect_phone_verify_button)

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

    /**
     * onCodeVerified is Firebase's local verification step, reached only once signInWithCredential
     * and getIdToken both resolve -- hence the stubbed FirebaseAuth.
     *
     * FirebaseAuthService calls submitOtp(idToken) on the line straight after onCodeVerified, so
     * reaching the toast unavoidably dispatches a PersonalID request. The MockWebServer is what stops
     * that request reaching the live server at PersonalIdApiClient.BASE_URL; its response is never
     * asserted on, and nothing is enqueued for it.
     */
    @Test
    fun `verifying a firebase code shows the verified acknowledgement`() {
        givenPersonalIdApiIsMocked()
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
