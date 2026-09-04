package org.commcare.fragments.personalId

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareTestApplication
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.views.connect.NumericCodeView
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
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
class PersonalIdEmailVerificationFragmentTest : BasePersonalIdEmailVerificationFragmentTest() {
    @Before
    override fun setUp() {
        super.setUp()
        attachTestNavController()
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
        // retries by re-typing the OTP, which re-fires the auto-submit chain via
        // setOnCodeCompleteListener.
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

    @Test
    fun `three failed OTP attempts show the verification-unsuccessful dialog`() {
        mockWebServer.enqueue(incorrectOtpResponse())
        mockWebServer.enqueue(incorrectOtpResponse())
        mockWebServer.enqueue(incorrectOtpResponse())

        repeat(3) {
            enterCode("123456")
            drainHttp()
        }

        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Verification-unsuccessful dialog should be shown after 3 failed attempts", dialog)
        assertTrue("Dialog should be visible", dialog!!.isShowing)
        // The fragment shows exactly one dialog (showProceedWithoutEmailDialog) and only after
        // failedOtpAttempts >= 3, so reaching this point proves the failure path took the
        // dialog branch rather than the per-attempt re-enable branch.
    }

    @Test
    fun `retry CTA on verification-unsuccessful dialog clears OTP state and dismisses the dialog`() {
        mockWebServer.enqueue(incorrectOtpResponse())
        mockWebServer.enqueue(incorrectOtpResponse())
        mockWebServer.enqueue(incorrectOtpResponse())

        repeat(3) {
            enterCode("123456")
            drainHttp()
        }

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        // StandardAlertDialog uses a custom content view, so the buttons are inside that
        // view (R.id.positive_button / R.id.negative_button) — not the native AlertDialog
        // BUTTON_POSITIVE / BUTTON_NEGATIVE slots, which are never populated.
        val retryButton = dialog.findViewById<Button>(R.id.positive_button)!!
        activity.runOnUiThread { retryButton.performClick() }
        ShadowLooper.idleMainLooper()

        val codeView = fragment.view?.findViewById<NumericCodeView>(R.id.otp_code_view)
        val errorText =
            fragment.view?.findViewById<TextView>(R.id.personalid_email_verify_error)
        val verifyButton =
            fragment.view?.findViewById<MaterialButton>(R.id.personalid_email_verify_button)

        assertTrue("OTP code should be cleared after Retry", codeView!!.codeValue.isEmpty())
        assertEquals(
            "Error text should be hidden after Retry",
            View.GONE,
            errorText!!.visibility,
        )
        assertFalse(
            "Verify button should be disabled after Retry (no 6-digit code present)",
            verifyButton!!.isEnabled,
        )
    }

    @Test
    fun `skip CTA on verification-unsuccessful dialog navigates to photo capture for REGISTRATION`() {
        mockWebServer.enqueue(incorrectOtpResponse())
        mockWebServer.enqueue(incorrectOtpResponse())
        mockWebServer.enqueue(incorrectOtpResponse())

        repeat(3) {
            enterCode("123456")
            drainHttp()
        }

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

    // ========== BACKUP_CODE_RECOVERY_SIGN_IN tests ==========

    @Test
    fun `BACKUP_CODE_RECOVERY_SIGN_IN calls complete_recovery endpoint not verify_email_otp`() {
        setUpPersonalIdActivityWithEmailVerificationFragment(
            workflow = EmailWorkFlow.FORGOT_BACKUP_CODE_RECOVERY,
        )
        activity.runOnUiThread {
            installTestNavController(
                fragment.requireView(),
                R.id.personalid_email_verification,
                Bundle().apply {
                    putString("email", TEST_EMAIL)
                    putSerializable("workflow", EmailWorkFlow.FORGOT_BACKUP_CODE_RECOVERY)
                    putInt("emailOtpRequestCount", 0)
                },
            )
        }
        ShadowLooper.idleMainLooper()

        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"username":"u","password":"p","db_key":"k","invited_user":false}""",
            ),
        )

        val connectDatabaseHelperMock = Mockito.mockStatic(ConnectDatabaseHelper::class.java)
        val connectUserDatabaseUtilMock = Mockito.mockStatic(ConnectUserDatabaseUtil::class.java)
        try {
            enterCode("123456")
            val request = takeRequestOrFail()
            assertEquals("/users/recover/complete_recovery", request.path)
            val body = JSONObject(request.body.readUtf8())
            assertEquals("email_otp", body.getString("method"))
            assertEquals("123456", body.getString("otp"))
        } finally {
            connectUserDatabaseUtilMock.close()
            connectDatabaseHelperMock.close()
        }
    }

    @Test
    fun `BACKUP_CODE_RECOVERY_SIGN_IN success navigates to backup code fragment`() {
        setUpPersonalIdActivityWithEmailVerificationFragment(
            workflow = EmailWorkFlow.FORGOT_BACKUP_CODE_RECOVERY,
        )
        activity.runOnUiThread {
            installTestNavController(
                fragment.requireView(),
                R.id.personalid_email_verification,
                Bundle().apply {
                    putString("email", TEST_EMAIL)
                    putSerializable("workflow", EmailWorkFlow.FORGOT_BACKUP_CODE_RECOVERY)
                    putInt("emailOtpRequestCount", 0)
                },
            )
        }
        ShadowLooper.idleMainLooper()

        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"username":"u","password":"p","db_key":"k","invited_user":false}""",
            ),
        )

        val connectDatabaseHelperMock = Mockito.mockStatic(ConnectDatabaseHelper::class.java)
        val connectUserDatabaseUtilMock = Mockito.mockStatic(ConnectUserDatabaseUtil::class.java)
        try {
            enterCode("123456")
            drainHttp()

            assertEquals(R.id.personalid_backup_code, navController.currentDestination!!.id)
        } finally {
            connectUserDatabaseUtilMock.close()
            connectDatabaseHelperMock.close()
        }
    }

    @Test
    fun `BACKUP_CODE_RECOVERY_SIGN_IN three failures navigate to message screen`() {
        setUpPersonalIdActivityWithEmailVerificationFragment(
            workflow = EmailWorkFlow.FORGOT_BACKUP_CODE_RECOVERY,
        )
        activity.runOnUiThread {
            installTestNavController(
                fragment.requireView(),
                R.id.personalid_email_verification,
                Bundle().apply {
                    putString("email", TEST_EMAIL)
                    putSerializable("workflow", EmailWorkFlow.FORGOT_BACKUP_CODE_RECOVERY)
                    putInt("emailOtpRequestCount", 0)
                },
            )
        }
        ShadowLooper.idleMainLooper()

        repeat(3) {
            mockWebServer.enqueue(
                MockResponse().setResponseCode(401).setBody("""{"error_code":"INCORRECT_OTP"}"""),
            )
            enterCode("000000")
            drainHttp()
        }

        assertEquals(R.id.personalid_message_display, navController.currentDestination!!.id)
    }

    // ========== Helpers ==========

    private fun enterCode(code: String) {
        val codeView = fragment.view?.findViewById<NumericCodeView>(R.id.otp_code_view)
        activity.runOnUiThread { codeView?.setCode(code) }
        ShadowLooper.idleMainLooper()
    }

    private fun successResponse(): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody("""{"status":"success"}""")

    private fun incorrectOtpResponse(): MockResponse =
        MockResponse()
            .setResponseCode(401)
            .setBody("""{"error_code":"INCORRECT_OTP"}""")

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
