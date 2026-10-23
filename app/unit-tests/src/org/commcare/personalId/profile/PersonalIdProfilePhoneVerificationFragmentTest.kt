package org.commcare.personalId.profile

import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.auth.PhoneAuthCredential
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.commcare.CommCareTestApplication
import org.commcare.android.shadows.ShadowPhoneAuthProvider
import org.commcare.android.util.FirebaseTestUtils
import org.commcare.connect.network.personalId.PersonalIdApiEndpoints
import org.commcare.core.network.AuthInfo
import org.commcare.dalvik.R
import org.commcare.fragments.personalId.BackupCodeWorkflow
import org.commcare.fragments.personalId.EmailWorkFlow
import org.commcare.fragments.personalId.PersonalIdProfileSendEmailOtpFragmentArgs
import org.commcare.network.HttpUtils
import org.commcare.views.connect.NumericCodeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Manage Profile phone OTP, reached from "Forgot backup code?" while adding an email with none on
 * file. Firebase first; always allowed to fall back to PersonalID SMS, which authenticates with the
 * stored user's basic-auth credentials.
 *
 * sdk = 31 for the same reason as PersonalIdPhoneVerificationFragmentTest: NumericCodeView needs
 * TypedArray to be AutoCloseable.
 */
@Config(
    application = CommCareTestApplication::class,
    sdk = [31],
    shadows = [ShadowPhoneAuthProvider::class],
)
@RunWith(AndroidJUnit4::class)
class PersonalIdProfilePhoneVerificationFragmentTest : BasePersonalIdProfileTest() {
    private val pendingEmail = "grace@example.com"

    @Before
    fun resetFirebase() {
        FirebaseTestUtils.initializeDefaultAppIfNeeded()
        ShadowPhoneAuthProvider.reset()
    }

    private fun currentFragment() = navHostFragment.childFragmentManager.primaryNavigationFragment!!

    private fun tapSendPhoneOtp() {
        val send = currentFragment().requireView().findViewById<View>(R.id.send_otp_button)
        onUiThread { send.performClick() }
    }

    private fun launch() {
        val args =
            PersonalIdProfileSendPhoneOtpFragmentArgs
                .Builder(pendingEmail)
                .build()
                .toBundle()
        onUiThread { navController.navigate(R.id.personalid_profile_send_phone_otp_fragment, args) }
        tapSendPhoneOtp()
    }

    private fun fragment() = currentFragment() as PersonalIdProfilePhoneVerificationFragment

    private fun codeView() = fragment().requireView().findViewById<NumericCodeView>(R.id.customOtpView)

    private fun verifyButton() = fragment().requireView().findViewById<Button>(R.id.connect_phone_verify_button)

    private fun changeNumberLink() = fragment().requireView().findViewById<View>(R.id.connect_phone_verify_change)

    private fun resendButton() = fragment().requireView().findViewById<View>(R.id.connect_resend_button)

    private fun errorView() = fragment().requireView().findViewById<TextView>(R.id.connect_phone_verify_error)

    private fun okResponse() = MockResponse().setResponseCode(200).setBody("{}")

    private fun incorrectOtpResponse() = MockResponse().setResponseCode(401).setBody("""{"error":"INCORRECT_OTP"}""")

    private fun expectedBasicAuth() = HttpUtils.getCredential(AuthInfo.ProvidedAuth(user.userId, user.password, false))

    /** Asserts [request] went to [expectedPath] with basic auth and, if given, carries [bodyField]. */
    private fun assertBasicAuthRequest(
        request: RecordedRequest,
        expectedPath: String,
        bodyField: Pair<String, String>? = null,
    ) {
        assertEquals(expectedPath, request.path)
        assertEquals(expectedBasicAuth(), request.getHeader("Authorization"))
        bodyField?.let { (name, value) ->
            assertTrue(request.body.readUtf8().contains("\"$name\":\"$value\""))
        }
    }

    private fun assertSentOnlyThroughFirebase() {
        assertEquals(1, ShadowPhoneAuthProvider.getRequestCount())
        assertEquals(0, mockWebServer.requestCount)
    }

    private fun assertIncorrectOtpShown() {
        assertEquals(View.VISIBLE, errorView().visibility)
        assertEquals(activity.getString(R.string.personalid_incorrect_otp), errorView().text.toString())
        assertEquals(R.id.personalid_profile_phone_verification_fragment, currentDestinationId())
    }

    /**
     * After a wrong code, enters the correct one, checks the error clears, and verifies it against a
     * server that accepts it. Returns the verify request.
     */
    private fun retryWithCorrectCode(): RecordedRequest {
        mockWebServer.enqueue(okResponse())
        onUiThread { codeView().setCode(CORRECT_CODE) }
        assertEquals(View.GONE, errorView().visibility)
        onUiThread { verifyButton().performClick() }
        val request = mockApiServer.drainHttp()
        assertEquals(R.id.personalid_send_email_otp_fragment, currentDestinationId())
        return request
    }

    private fun enterCodeAndVerify(code: String) {
        onUiThread { codeView().setCode(code) }
        onUiThread { verifyButton().performClick() }
    }

    /** Verifies [code] against a server that answers with [response]; returns the verify request. */
    private fun verifyCodeWithServer(
        code: String = CORRECT_CODE,
        response: MockResponse = okResponse(),
    ): RecordedRequest {
        mockWebServer.enqueue(response)
        enterCodeAndVerify(code)
        return mockApiServer.drainHttp()
    }

    /** Signs in and returns [idToken] for [code]; rejects any other code as Firebase does a wrong one. */
    private fun firebaseAuthAccepting(
        code: String = CORRECT_CODE,
        idToken: String = FIREBASE_ID_TOKEN,
    ): FirebaseAuth {
        val tokenResult = mock(GetTokenResult::class.java)
        `when`(tokenResult.token).thenReturn(idToken)
        val user = mock(FirebaseUser::class.java)
        `when`(user.getIdToken(false)).thenReturn(Tasks.forResult(tokenResult))
        val authResult = mock(AuthResult::class.java)
        `when`(authResult.user).thenReturn(user)
        val firebaseAuth = mock(FirebaseAuth::class.java)
        `when`(firebaseAuth.signInWithCredential(any())).thenAnswer { invocation ->
            if (invocation.getArgument<PhoneAuthCredential>(0).smsCode == code) {
                Tasks.forResult(authResult)
            } else {
                Tasks.forException<AuthResult>(
                    FirebaseAuthInvalidCredentialsException("ERROR_INVALID_VERIFICATION_CODE", "wrong code"),
                )
            }
        }
        return firebaseAuth
    }

    private fun launchWithFirebaseCodeSent() {
        ShadowPhoneAuthProvider.sendCodeWith("test-verification-id")
        launch()
        ShadowLooper.idleMainLooper()
    }

    /**
     * Starts on Firebase with the code sent. FirebaseAuthService reads FirebaseAuth.getInstance() only
     * when the screen creates it, so the static mock can close once the screen is up.
     */
    private fun launchOnFirebasePath(firebaseAuth: FirebaseAuth = firebaseAuthAccepting()) {
        mockStatic(FirebaseAuth::class.java).use { firebaseAuthStatic ->
            firebaseAuthStatic.`when`<FirebaseAuth> { FirebaseAuth.getInstance() }.thenReturn(firebaseAuth)
            launchWithFirebaseCodeSent()
        }
    }

    /**
     * Forces the PersonalID path: a non-recoverable Firebase failure triggers the auto-switch once
     * [open] reaches phone verification. Returns the PersonalID send request.
     */
    private fun launchOnPersonalIdPath(open: () -> Unit = { launch() }): RecordedRequest {
        ShadowPhoneAuthProvider.failWith(FirebaseAuthException("ERROR_APP_NOT_AUTHORIZED", "not authorized"))
        mockWebServer.enqueue(okResponse())
        open()
        ShadowLooper.idleMainLooper()
        return mockApiServer.drainHttp()
    }

    @Test
    fun `starts with Firebase and sends to the stored phone number`() {
        launch()
        assertSentOnlyThroughFirebase()
        assertEquals(user.primaryPhone, ShadowPhoneAuthProvider.getLastPhoneNumber())
    }

    @Test
    fun `change number link is hidden`() {
        launch()
        assertEquals(View.GONE, changeNumberLink().visibility)
    }

    @Test
    fun `non-recoverable Firebase failure switches to PersonalID with basic auth`() {
        assertBasicAuthRequest(launchOnPersonalIdPath(), PersonalIdApiEndpoints.VALIDATE_PHONE)
    }

    @Test
    fun `first resend switches to PersonalID`() {
        launchWithFirebaseCodeSent()
        assertSentOnlyThroughFirebase()

        mockWebServer.enqueue(okResponse())
        onUiThread { resendButton().performClick() }

        assertBasicAuthRequest(mockApiServer.takeRequestOrFail(), PersonalIdApiEndpoints.VALIDATE_PHONE)
        assertEquals(1, ShadowPhoneAuthProvider.getRequestCount())
    }

    @Test
    fun `verified OTP navigates to send email otp with the unmasked pending email`() {
        launchOnPersonalIdPath()
        verifyCodeWithServer()

        assertEquals(R.id.personalid_send_email_otp_fragment, currentDestinationId())
        val args = PersonalIdProfileSendEmailOtpFragmentArgs.fromBundle(currentFragment().requireArguments())
        assertEquals(pendingEmail, args.email)
        assertFalse(args.masked)
        assertEquals(EmailWorkFlow.EXISTING_USER, args.workflow)
    }

    @Test
    fun `wrong Firebase code shows an error, then the right code verifies through validate_firebase_id_token`() {
        launchOnFirebasePath()

        enterCodeAndVerify(WRONG_CODE)
        assertIncorrectOtpShown()
        assertSentOnlyThroughFirebase()

        assertBasicAuthRequest(
            retryWithCorrectCode(),
            PersonalIdApiEndpoints.VALIDATE_FIREBASE_ID_TOKEN,
            "token" to FIREBASE_ID_TOKEN,
        )
    }

    @Test
    fun `wrong PersonalID code shows an error, then the right code verifies through confirm_otp`() {
        launchOnPersonalIdPath()

        assertBasicAuthRequest(
            verifyCodeWithServer(WRONG_CODE, incorrectOtpResponse()),
            PersonalIdApiEndpoints.CONFIRM_OTP,
            "token" to WRONG_CODE,
        )
        assertIncorrectOtpShown()

        assertBasicAuthRequest(retryWithCorrectCode(), PersonalIdApiEndpoints.CONFIRM_OTP, "token" to CORRECT_CODE)
    }

    @Test
    fun `back from Send email OTP lands on the Forgot Backup Code page`() {
        launchOnPersonalIdPath { openFromForgotBackupCode() }
        assertEquals(R.id.personalid_profile_phone_verification_fragment, currentDestinationId())

        verifyCodeWithServer()
        assertEquals(R.id.personalid_send_email_otp_fragment, currentDestinationId())

        onUiThread { navController.popBackStack() }

        assertEquals(R.id.personalid_profile_backup_code_fragment, currentDestinationId())
    }

    private fun openFromForgotBackupCode() {
        val backupCodeArgs =
            PersonalIdProfileBackupCodeFragmentArgs
                .Builder(EmailWorkFlow.EXISTING_USER, BackupCodeWorkflow.EMAIL_CHANGE)
                .setPendingEmail(pendingEmail)
                .build()
                .toBundle()
        onUiThread { navController.navigate(R.id.personalid_profile_backup_code_fragment, backupCodeArgs) }
        user.email = null
        val forgotButton = currentFragment().requireView().findViewById<View>(R.id.personalid_forgot_backup_code)
        onUiThread { forgotButton.performClick() }
        assertEquals(R.id.personalid_profile_send_phone_otp_fragment, currentDestinationId())
        tapSendPhoneOtp()
    }

    // Regression test due to saveInstanceState saving view state before it getting initialized
    @Test
    fun `onSaveInstanceState does not crash on a fragment whose view was never created`() {
        val hostController =
            Robolectric
                .buildActivity(FragmentActivity::class.java)
                .create()
                .start()
                .resume()
        val fragmentManager = hostController.get().supportFragmentManager

        val headlessFragment =
            PersonalIdProfilePhoneVerificationFragment().apply {
                arguments =
                    PersonalIdProfilePhoneVerificationFragmentArgs
                        .Builder(pendingEmail)
                        .build()
                        .toBundle()
            }
        onUiThread {
            fragmentManager
                .beginTransaction()
                .add(headlessFragment, "headless-phone-verification")
                .setMaxLifecycle(headlessFragment, Lifecycle.State.CREATED)
                .commitNow()
        }

        try {
            onUiThread { fragmentManager.saveFragmentInstanceState(headlessFragment) }
        } finally {
            hostController.pause().stop().destroy()
        }
    }

    @Test
    fun `email change with no stored backup code skips the gate and goes to send phone otp`() {
        user.pin = null
        saveEmailChangeFromEdit()

        assertEquals(R.id.personalid_profile_send_phone_otp_fragment, currentDestinationId())
        val args = PersonalIdProfileSendPhoneOtpFragmentArgs.fromBundle(currentFragment().requireArguments())
        assertEquals(pendingEmail, args.pendingEmail)

        onUiThread { navController.popBackStack() }
        assertEquals(R.id.personalid_profile_edit_fragment, currentDestinationId())
    }

    private fun saveEmailChangeFromEdit() {
        onUiThread { navController.navigate(R.id.action_profile_to_profile_edit) }
        val edit = currentFragment().requireView()
        setText(edit.findViewById<TextInputEditText>(R.id.profile_email_edit_text), pendingEmail)
        onUiThread { edit.findViewById<MaterialButton>(R.id.btn_save).performClick() }
    }

    companion object {
        private const val CORRECT_CODE = "123456"
        private const val WRONG_CODE = "000000"
        private const val FIREBASE_ID_TOKEN = "firebase-id-token"
    }
}
