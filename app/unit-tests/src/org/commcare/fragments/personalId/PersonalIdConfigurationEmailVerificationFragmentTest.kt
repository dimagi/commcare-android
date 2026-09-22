package org.commcare.fragments.personalId

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.NavHostFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.dalvik.R
import org.commcare.utils.MockAndroidKeyStoreProvider
import org.commcare.views.connect.NumericCodeView
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/**
 * Unit tests for PersonalIdEmailVerificationFragment.
 *
 * Uses MockWebServer to make the verify-OTP HTTP call deterministic (mirroring
 * PersonalIdPhoneFragmentStartConfigurationTest) and TestNavHostController so outbound
 * navigation can be observed without instantiating destination fragments.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdConfigurationEmailVerificationFragmentTest : BasePersonalIdEmailVerificationFragmentTest() {
    @Before
    override fun setUp() {
        super.setUp()
        attachTestNavController()
    }

    @After
    override fun tearDown() {
        MockAndroidKeyStoreProvider.deregisterProvider()
        super.tearDown()
    }

    private fun attachTestNavController() {
        val args =
            Bundle().apply {
                putString("email", TEST_EMAIL)
                putSerializable("workflow", EmailWorkFlow.REGISTRATION)
                putInt("emailOtpRequestCount", 0)
            }
        activity.runOnUiThread {
            installTestNavController(fragment.requireView(), R.id.personalid_email_verification, args)
        }
        ShadowLooper.idleMainLooper()
    }

    // ========== Initial State Tests ==========

    @Test
    fun `verify button is disabled on initial state`() {
        val verifyButton =
            fragment.view?.findViewById<MaterialButton>(R.id.personalid_email_verify_button)
        assertFalse("Verify button should be disabled initially", verifyButton!!.isEnabled)
    }

    @Test
    fun `otp code is empty on initial state`() {
        val codeView = fragment.view?.findViewById<NumericCodeView>(R.id.otp_code_view)
        assertTrue("OTP code should be empty at start", codeView!!.codeValue.isEmpty())
    }

    @Test
    fun `error text is hidden on initial state`() {
        val errorText = fragment.view?.findViewById<TextView>(R.id.personalid_email_verify_error)
        assertEquals("Error text should be GONE initially", View.GONE, errorText!!.visibility)
    }

    @Test
    fun `description text contains the email passed as nav arg`() {
        val description =
            fragment.view?.findViewById<TextView>(R.id.email_verification_description)
        assertTrue(
            "Description should reference the entered email",
            description!!.text.toString().contains(TEST_EMAIL),
        )
    }

    // ========== OTP Input Tests ==========

    @Test
    fun `partial code keeps verify button disabled`() {
        val codeView = fragment.view?.findViewById<NumericCodeView>(R.id.otp_code_view)
        val verifyButton =
            fragment.view?.findViewById<MaterialButton>(R.id.personalid_email_verify_button)

        activity.runOnUiThread { codeView?.setCode("123") }
        ShadowLooper.idleMainLooper()

        assertFalse(
            "Verify button should remain disabled with fewer than 6 digits",
            verifyButton!!.isEnabled,
        )
    }

    @Test
    fun `complete 6-digit code does not auto-submit without pressing the verify button`() {
        val codeView = fragment.view?.findViewById<NumericCodeView>(R.id.otp_code_view)

        activity.runOnUiThread { codeView?.setCode("123456") }
        ShadowLooper.idleMainLooper()

        assertEquals(
            "Entering 6 digits must not trigger an API call — user must press the verify button",
            0,
            mockWebServer.requestCount,
        )
    }

    // ========== API-Backed OTP Submission Tests ==========

    @Test
    fun `complete 6-digit code submits OTP request to verify endpoint`() {
        mockWebServer.enqueue(successResponse())

        enterCode("123456")

        val request = mockWebServer.takeRequest()
        assertEquals("/users/verify_email_otp", request.path)
        assertEquals("POST", request.method)
        val body = JSONObject(request.body.readUtf8())
        assertEquals(TEST_EMAIL, body.getString("email"))
        assertEquals("123456", body.getString("otp"))
    }

    @Test
    fun `successful OTP verification navigates to photo capture for REGISTRATION`() {
        mockWebServer.enqueue(successResponse())

        enterCode("123456")
        drainHttp()

        assertEquals(
            "Should navigate to photo capture on successful verification",
            R.id.personalid_photo_capture,
            navController.currentDestination!!.id,
        )
    }

    @Test
    fun `failed OTP verification shows error and leaves verify button disabled`() {
        mockWebServer.enqueue(incorrectOtpResponse())

        enterCode("123456")
        drainHttp()

        val errorText =
            fragment.view?.findViewById<TextView>(R.id.personalid_email_verify_error)
        assertEquals(
            "Error text should be visible after a failed OTP attempt",
            View.VISIBLE,
            errorText!!.visibility,
        )
        assertEquals(
            "A wrong code should name the code, not report an authorization failure",
            activity.getString(R.string.personalid_incorrect_otp),
            errorText.text.toString(),
        )
        // INCORRECT_OTP_ERROR is not in the shouldAllowRetry() allow-list (only NETWORK / SERVER /
        // INTEGRITY / TOKEN_UNAVAILABLE / UNKNOWN), so the verify button stays disabled — the user
        // must re-type the OTP (re-enabling the button via the code-changed listener) and then
        // press the verify button manually.
        val verifyButton =
            fragment.view?.findViewById<MaterialButton>(R.id.personalid_email_verify_button)
        assertFalse(
            "Verify button should remain disabled after an incorrect-OTP failure",
            verifyButton!!.isEnabled,
        )
    }

    @Test
    fun `email already in use response shows the email-in-use error instead of crashing`() {
        mockWebServer.enqueue(emailAlreadyInUseResponse())

        enterCode("123456")
        drainHttp()

        val errorText =
            fragment.view?.findViewById<TextView>(R.id.personalid_email_verify_error)
        assertEquals(
            "Error text should be visible after a 400 response",
            View.VISIBLE,
            errorText!!.visibility,
        )
        assertEquals(
            "A duplicate-email 400 should surface the email-already-in-use message",
            activity.getString(R.string.personalid_email_already_in_use),
            errorText.text.toString(),
        )
    }

    @Test
    fun `auth failure sharing the 401 is not reported as a wrong code`() {
        mockWebServer.enqueue(lockedAccountResponse())

        enterCode("123456")
        drainHttp()

        assertEquals(
            "A locked account should route to the configuration-failed message screen",
            R.id.personalid_message_display,
            navController.currentDestination!!.id,
        )
    }

    @Test
    fun `malformed request fails fast rather than reporting an in-use email`() {
        mockWebServer.enqueue(missingDataResponse())

        assertThrows(RuntimeException::class.java) {
            enterCode("123456")
            drainHttp()
        }
    }

    // ========== OTP_LIMIT_EXCEEDED Tests ==========

    @Test
    fun `wrong codes alone never raise the verification-unsuccessful dialog`() {
        repeat(3) {
            mockWebServer.enqueue(incorrectOtpResponse())
            enterCode("123456")
            drainHttp()
        }

        assertFalse(
            "Only the server's OTP_LIMIT_EXCEEDED decides when the guesses have run out",
            ShadowDialog.getLatestDialog()?.isShowing ?: false,
        )
    }

    @Test
    fun `a code that has run out of attempts raises the verification-unsuccessful dialog`() {
        mockWebServer.enqueue(otpLimitExceededResponse())

        enterCode("123456")
        drainHttp()

        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
        assertNotNull("OTP_LIMIT_EXCEEDED should offer the skip-email escape during REGISTRATION", dialog)
        assertTrue("Dialog should be visible", dialog!!.isShowing)
    }

    @Test
    fun `a code that has run out of attempts closes the screen for the server's wait`() {
        mockWebServer.enqueue(otpLimitExceededResponse())

        enterCode("123456")
        drainHttp()

        val codeView = fragment.view?.findViewById<NumericCodeView>(R.id.otp_code_view)
        val errorText =
            fragment.view?.findViewById<TextView>(R.id.personalid_email_verify_error)
        val verifyButton =
            fragment.view?.findViewById<MaterialButton>(R.id.personalid_email_verify_button)

        assertTrue("The dead code should not be left in the field", codeView!!.codeValue.isEmpty())
        assertFalse("Nothing can be typed until a new code is requested", codeView.isEnabled)
        assertEquals(
            activity.getString(R.string.personalid_otp_limit_exceeded),
            errorText!!.text.toString(),
        )
        assertFalse("Nothing to verify, so verify stays disabled", verifyButton!!.isEnabled)
        assertEquals(
            "No new code can be requested until the server's wait elapses",
            View.GONE,
            fragment.requireView().findViewById<View>(R.id.personalid_email_resend_button).visibility,
        )
    }

    @Test
    fun `running out of attempts counts down to requesting a new code rather than resending`() {
        mockWebServer.enqueue(otpLimitExceededResponse())

        enterCode("123456")
        drainHttp()

        val countdown = fragment.requireView().findViewById<TextView>(R.id.personalid_resend_countdown_text)
        assertEquals(View.VISIBLE, countdown.visibility)
        assertEquals(
            activity.getString(
                R.string.personalid_otp_request_new_code_wait,
                activity.resources.getQuantityString(R.plurals.personalid_otp_retry_after_hours, 1, 1),
            ),
            countdown.text.toString(),
        )
    }

    @Test
    fun `retry CTA on verification-unsuccessful dialog leaves the countdown running`() {
        mockWebServer.enqueue(otpLimitExceededResponse())
        enterCode("123456")
        drainHttp()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        // StandardAlertDialog uses a custom content view, so the buttons are inside that
        // view (R.id.positive_button / R.id.negative_button) — not the native AlertDialog
        // BUTTON_POSITIVE / BUTTON_NEGATIVE slots, which are never populated.
        val retryButton = dialog.findViewById<Button>(R.id.positive_button)!!
        activity.runOnUiThread { retryButton.performClick() }
        ShadowLooper.idleMainLooper()

        val errorText =
            fragment.view?.findViewById<TextView>(R.id.personalid_email_verify_error)
        assertEquals(
            "The reason should survive the dialog, since it is what explains the wait",
            activity.getString(R.string.personalid_otp_limit_exceeded),
            errorText!!.text.toString(),
        )
        assertEquals(
            "Dismissing the dialog must not shortcut the server's wait",
            View.GONE,
            fragment.requireView().findViewById<View>(R.id.personalid_email_resend_button).visibility,
        )
    }

    @Test
    fun `skip CTA on verification-unsuccessful dialog navigates to photo capture for REGISTRATION`() {
        mockWebServer.enqueue(otpLimitExceededResponse())
        enterCode("123456")
        drainHttp()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val skipButton = dialog.findViewById<Button>(R.id.negative_button)!!
        activity.runOnUiThread { skipButton.performClick() }
        ShadowLooper.idleMainLooper()

        assertEquals(
            "Skip should route to photo capture for the REGISTRATION workflow",
            R.id.personalid_photo_capture,
            navController.currentDestination!!.id,
        )
    }

    // ========== RATE_LIMITED Tests ==========

    @Test
    fun `a rate-limited resend reports how long the wait actually is`() {
        clickResend(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error_code":"RATE_LIMITED","retry_after_seconds":7200}"""),
        )

        val errorText = fragment.view?.findViewById<TextView>(R.id.personalid_email_verify_error)
        assertEquals(
            activity.getString(
                R.string.personalid_rate_limited_retry_after,
                activity.resources.getQuantityString(R.plurals.personalid_otp_retry_after_hours, 2, 2),
            ),
            errorText!!.text.toString(),
        )
    }

    @Test
    fun `a rate-limited resend holds the resend button for the server's wait`() {
        clickResend(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error_code":"RATE_LIMITED","retry_after_seconds":7200}"""),
        )

        assertEquals(
            "Resend must not reappear on the default two-minute cooldown after a two-hour wait",
            View.GONE,
            fragment.requireView().findViewById<View>(R.id.personalid_email_resend_button).visibility,
        )
    }

    @Test
    fun `a rate-limited resend without a retry hint falls back to the generic cooldown message`() {
        clickResend(
            MockResponse().setResponseCode(429).setBody("""{"error_code":"RATE_LIMITED"}"""),
        )

        val errorText = fragment.view?.findViewById<TextView>(R.id.personalid_email_verify_error)
        assertEquals(
            activity.getString(R.string.recovery_network_cooldown),
            errorText!!.text.toString(),
        )
    }

    @Test
    fun `the resend countdown names a multi-hour wait in hours rather than raw seconds`() {
        clickResend(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error_code":"RATE_LIMITED","retry_after_seconds":7200}"""),
        )

        val countdown = fragment.requireView().findViewById<TextView>(R.id.personalid_resend_countdown_text)
        assertEquals(View.VISIBLE, countdown.visibility)
        assertEquals(
            activity.getString(
                R.string.personalid_otp_resend_wait,
                activity.resources.getQuantityString(R.plurals.personalid_otp_retry_after_hours, 2, 2),
            ),
            countdown.text.toString(),
        )
    }

    // ========== Saved State Tests ==========

    @Test
    fun `the wait after running out of attempts survives a configuration change`() {
        mockWebServer.enqueue(otpLimitExceededResponse())
        enterCode("123456")
        drainHttp()

        recreateFragment()

        assertEquals(
            "A rotation must not shortcut the server's wait",
            View.GONE,
            fragment.requireView().findViewById<View>(R.id.personalid_email_resend_button).visibility,
        )
        assertEquals(
            "The wording must still be about requesting a new code, not resending",
            activity.getString(
                R.string.personalid_otp_request_new_code_wait,
                activity.resources.getQuantityString(R.plurals.personalid_otp_retry_after_hours, 1, 1),
            ),
            fragment
                .requireView()
                .findViewById<TextView>(R.id.personalid_resend_countdown_text)
                .text
                .toString(),
        )
    }

    @Test
    fun `a server-supplied wait survives a configuration change`() {
        clickResend(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error_code":"RATE_LIMITED","retry_after_seconds":7200}"""),
        )

        recreateFragment()

        val countdown = fragment.requireView().findViewById<TextView>(R.id.personalid_resend_countdown_text)
        assertEquals(
            "The two-hour wait must not collapse back to the two-minute default",
            activity.getString(
                R.string.personalid_otp_resend_wait,
                activity.resources.getQuantityString(R.plurals.personalid_otp_retry_after_hours, 2, 2),
            ),
            countdown.text.toString(),
        )
    }

    // ========== Skip-Eligibility Tests ==========

    /**
     * The skip dialog is raised by OTP_LIMIT_EXCEEDED, and its "proceed without email" button routes
     * through proceedWithoutEmail(), which throws for any workflow it has no branch for. These two
     * must therefore agree exactly.
     */
    @Test
    fun `only the workflows proceedWithoutEmail can route are offered a skip`() {
        mapOf(
            EmailWorkFlow.REGISTRATION to true,
            EmailWorkFlow.RECOVERY to true,
            EmailWorkFlow.EXISTING_USER to false,
            EmailWorkFlow.FORGOT_BACKUP_CODE_RECOVERY to false,
        ).forEach { (workflow, expected) ->
            setUpWorkflow(workflow)
            assertEquals(
                "$workflow skip eligibility",
                expected,
                fragment.canSkipEmailVerification(),
            )
        }
    }

    // ========== FORGOT_BACKUP_CODE_RECOVERY tests ==========

    @Test
    fun `FORGOT_BACKUP_CODE_RECOVERY calls complete_recovery endpoint`() {
        setUpForgotBackupCodeRecoveryFlow()
        mockCompleteRecovery(true)

        enterCode("123456")
        val request = takeRequestOrFail()

        assertEquals("/users/recover/complete_recovery", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("email_otp", body.getString("method"))
        assertEquals("123456", body.getString("otp"))
    }

    @Test
    fun `FORGOT_BACKUP_CODE_RECOVERY success navigates to set new backup code fragment`() {
        setUpForgotBackupCodeRecoveryFlow()
        mockCompleteRecovery(true)

        enterCode("123456")
        drainHttp()

        assertEquals(R.id.personalid_account_config_set_new_backup_code_fragment, navController.currentDestination!!.id)
    }

    @Test
    fun `FORGOT_BACKUP_CODE_RECOVERY failure shows an error message`() {
        setUpForgotBackupCodeRecoveryFlow()
        mockCompleteRecovery(false)

        enterCode("000000")
        drainHttp()

        val errorText = fragment.view?.findViewById<TextView>(R.id.personalid_email_verify_error)
        assertEquals(View.VISIBLE, errorText!!.visibility)
        assertEquals(
            activity.getString(R.string.personalid_incorrect_otp),
            errorText.text.toString(),
        )
    }

    @Test
    fun `FORGOT_BACKUP_CODE_RECOVERY treats running out of attempts as recoverable rather than a lockout`() {
        setUpForgotBackupCodeRecoveryFlow()
        mockWebServer.enqueue(otpLimitExceededResponse())

        enterCode("000000")
        drainHttp()

        assertEquals(
            "Running out of guesses no longer locks the account, so recovery stays on this screen",
            R.id.personalid_email_verification,
            navController.currentDestination!!.id,
        )
        assertFalse(
            "Email OTP is the recovery factor here, so there is nothing to proceed without",
            ShadowDialog.getLatestDialog()?.isShowing ?: false,
        )
        val errorText = fragment.view?.findViewById<TextView>(R.id.personalid_email_verify_error)
        assertEquals(
            activity.getString(R.string.personalid_otp_limit_exceeded),
            errorText!!.text.toString(),
        )
    }

    @Test
    fun `FORGOT_BACKUP_CODE_RECOVERY resend omits email from request body`() {
        setUpForgotBackupCodeRecoveryFlow()
        activity.runOnUiThread {
            fragment.requireView().findViewById<View>(R.id.personalid_email_resend_button).visibility = View.VISIBLE
        }
        ShadowLooper.idleMainLooper()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        activity.runOnUiThread {
            fragment.requireView().findViewById<View>(R.id.personalid_email_resend_button).performClick()
        }
        ShadowLooper.idleMainLooper()

        val request = takeRequestOrFail()
        assertEquals("/users/send_email_otp", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertFalse("resend in FORGOT_BACKUP_CODE_RECOVERY must not include email", body.has("email"))
    }

    @Test
    fun `REGISTRATION resend includes email in request body`() {
        activity.runOnUiThread {
            fragment.requireView().findViewById<View>(R.id.personalid_email_resend_button).visibility = View.VISIBLE
        }
        ShadowLooper.idleMainLooper()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        activity.runOnUiThread {
            fragment.requireView().findViewById<View>(R.id.personalid_email_resend_button).performClick()
        }
        ShadowLooper.idleMainLooper()

        val request = takeRequestOrFail()
        assertEquals("/users/send_email_otp", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals(TEST_EMAIL, body.getString("email"))
    }

    // ========== Helpers ==========

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

    private fun setUpWorkflow(workflow: EmailWorkFlow) {
        val args =
            Bundle().apply {
                putString("email", TEST_EMAIL)
                putSerializable("workflow", workflow)
                putInt("emailOtpRequestCount", 0)
            }
        navigateToFragment(PersonalIdSessionData(token = "test-token"), R.id.personalid_email_verification, args)
        activity.runOnUiThread {
            installTestNavController(fragment.requireView(), R.id.personalid_email_verification, args)
        }
        ShadowLooper.idleMainLooper()
    }

    private fun setUpForgotBackupCodeRecoveryFlow() {
        MockAndroidKeyStoreProvider.registerProvider()
        val args =
            Bundle().apply {
                putString("email", TEST_EMAIL)
                putSerializable("workflow", EmailWorkFlow.FORGOT_BACKUP_CODE_RECOVERY)
                putInt("emailOtpRequestCount", 0)
            }
        val sessionData =
            PersonalIdSessionData(
                token = "test-token",
                userName = "test-user",
                phoneNumber = "1234567890",
                requiredLock = PersonalIdSessionData.PIN,
                demoUser = false,
            )
        navigateToFragment(sessionData, R.id.personalid_email_verification, args)
        activity.runOnUiThread {
            installTestNavController(
                fragment.requireView(),
                R.id.personalid_email_verification,
                args,
            )
        }
        ShadowLooper.idleMainLooper()
    }

    private fun mockCompleteRecovery(success: Boolean) {
        if (success) {
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"username":"u","password":"p","db_key":"dGVzdC1kYi1rZXk=","invited_user":false}""",
                ),
            )
        } else {
            mockWebServer.enqueue(
                MockResponse().setResponseCode(401).setBody("""{"error_code":"INCORRECT_OTP"}"""),
            )
        }
    }

    private fun enterCode(code: String) {
        val codeView = fragment.view?.findViewById<NumericCodeView>(R.id.otp_code_view)
        val verifyButton = fragment.view?.findViewById<View>(R.id.personalid_email_verify_button)
        activity.runOnUiThread {
            codeView?.setCode(code)
            verifyButton?.performClick()
        }
        ShadowLooper.idleMainLooper()
    }

    /** Reveals the resend button past its countdown, clicks it, and lets [response] come back. */
    private fun clickResend(response: MockResponse) {
        val resendButton = fragment.requireView().findViewById<View>(R.id.personalid_email_resend_button)
        activity.runOnUiThread { resendButton.visibility = View.VISIBLE }
        ShadowLooper.idleMainLooper()

        mockWebServer.enqueue(response)
        activity.runOnUiThread { resendButton.performClick() }
        drainHttp()
    }

    private fun successResponse(): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody("""{"status":"success"}""")

    private fun incorrectOtpResponse(): MockResponse =
        MockResponse()
            .setResponseCode(401)
            .setBody("""{"error_code":"INCORRECT_OTP"}""")

    private fun otpLimitExceededResponse(): MockResponse =
        MockResponse()
            .setResponseCode(401)
            .setBody("""{"error_code":"OTP_LIMIT_EXCEEDED","retry_after_seconds":3600}""")

    private fun emailAlreadyInUseResponse(): MockResponse =
        MockResponse()
            .setResponseCode(400)
            .setBody("""{"error_code":"EMAIL_ALREADY_IN_USE"}""")

    private fun lockedAccountResponse(): MockResponse =
        MockResponse()
            .setResponseCode(401)
            .setBody("""{"error_code":"LOCKED_ACCOUNT"}""")

    private fun missingDataResponse(): MockResponse =
        MockResponse()
            .setResponseCode(400)
            .setBody("""{"error_code":"MISSING_DATA"}""")
}
