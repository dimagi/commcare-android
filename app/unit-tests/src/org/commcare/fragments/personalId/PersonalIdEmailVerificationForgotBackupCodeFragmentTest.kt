package org.commcare.fragments.personalId

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.commcare.personalId.profile.BasePersonalIdProfileTest
import org.commcare.views.connect.NumericCodeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Tests for [PersonalIdEmailVerificationFragment] under the [EmailWorkFlow.FORGOT_BACKUP_CODE_EXISTING_USER]
 * workflow. This flow is reached from the profile nav graph (Profile → Backup-Code → Send-Email-OTP →
 * Email-Verification), so the tests extend [BasePersonalIdProfileTest] and use
 * [R.navigation.nav_graph_personalid_profile].
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdEmailVerificationForgotBackupCodeFragmentTest : BasePersonalIdProfileTest() {
    private val fragmentArgs =
        Bundle().apply {
            putString("email", TEST_EMAIL)
            putInt("emailOtpRequestCount", 0)
        }

    @Before
    fun navigateToEmailVerification() {
        onUiThread {
            navController.navigate(R.id.personalid_email_verification_forgot_backup_code_fragment, fragmentArgs)
        }
    }

    private fun fragment() =
        navHostFragment.childFragmentManager
            .primaryNavigationFragment as PersonalIdEmailVerificationForgotBackupCodeFragment

    // ========== email masking ==========

    @Test
    fun `email shown in description is masked`() {
        val descriptionView =
            fragment().requireView().findViewById<TextView>(R.id.email_verification_description)
        val maskedEmail = EmailHelper.maskEmail(TEST_EMAIL)
        val expectedText = activity.getString(R.string.personalid_email_verification_description, maskedEmail)
        assertEquals(
            "Description should show masked email",
            expectedText,
            descriptionView.text.toString(),
        )
    }

    // ========== onEmailVerified ==========

    @Test
    fun `successful OTP verification navigates to set new backup code`() {
        mockWebServer.enqueue(successResponse())

        val testNavController = TestNavHostController(ApplicationProvider.getApplicationContext())
        onUiThread {
            testNavController.setGraph(R.navigation.nav_graph_personalid_profile)
            testNavController.setCurrentDestination(R.id.personalid_email_verification_forgot_backup_code_fragment, fragmentArgs)
            Navigation.setViewNavController(fragment().requireView(), testNavController)
            val view = fragment().requireView()
            view.findViewById<NumericCodeView>(R.id.otp_code_view).setCode("123456")
            view.findViewById<View>(R.id.personalid_email_verify_button).performClick()
        }
        mockApiServer.drainHttp()

        assertEquals(
            "Verified email in FORGOT_BACKUP_CODE_EXISTING_USER flow should navigate to set-new-backup-code",
            R.id.personalid_profile_set_new_backup_code_fragment,
            testNavController.currentDestination!!.id,
        )
    }

    // ========== OTP_LIMIT_EXCEEDED ==========

    @Test
    fun `running out of attempts shows the request-a-new-code error instead of the proceed-without-email dialog`() {
        mockWebServer.enqueue(otpLimitExceededResponse())

        enterCode()

        assertFalse(
            "Email OTP is the only way back into this flow, so there is nothing to proceed without",
            ShadowDialog.getLatestDialog()?.isShowing ?: false,
        )

        val errorText =
            fragment().requireView().findViewById<TextView>(R.id.personalid_email_verify_error)
        assertEquals(
            "Error text should be visible once the code has run out of attempts",
            View.VISIBLE,
            errorText.visibility,
        )
        assertEquals(
            activity.getString(R.string.personalid_otp_limit_exceeded),
            errorText.text.toString(),
        )
    }

    @Test
    fun `a code that has run out of attempts closes the screen for the server's wait`() {
        mockWebServer.enqueue(otpLimitExceededResponse())

        enterCode()

        val codeView = fragment().requireView().findViewById<NumericCodeView>(R.id.otp_code_view)
        assertEquals("The dead code should not be left in the field", "", codeView.codeValue)
        assertFalse("Nothing can be typed until a new code is requested", codeView.isEnabled)
        assertEquals(
            "No new code can be requested until the server's wait elapses",
            View.GONE,
            fragment().requireView().findViewById<View>(R.id.personalid_email_resend_button).visibility,
        )
    }

    @Test
    fun `a wrong code is still reported as a wrong code`() {
        mockWebServer.enqueue(incorrectOtpResponse())

        enterCode()

        val errorText =
            fragment().requireView().findViewById<TextView>(R.id.personalid_email_verify_error)
        assertEquals(
            activity.getString(R.string.personalid_incorrect_otp),
            errorText.text.toString(),
        )
    }

    // ========== Helpers ==========

    private fun enterCode(code: String = "123456") {
        onUiThread {
            val view = fragment().requireView()
            view.findViewById<NumericCodeView>(R.id.otp_code_view).setCode(code)
            view.findViewById<View>(R.id.personalid_email_verify_button).performClick()
        }
        mockApiServer.drainHttp()
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

    companion object {
        private const val TEST_EMAIL = "user@example.com"
    }
}
