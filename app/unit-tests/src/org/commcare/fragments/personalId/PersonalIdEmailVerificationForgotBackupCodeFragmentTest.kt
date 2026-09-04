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
            fragment()
                .requireView()
                .findViewById<NumericCodeView>(R.id.otp_code_view)
                .setCode("123456")
        }
        mockApiServer.drainHttp()

        assertEquals(
            "Verified email in FORGOT_BACKUP_CODE_EXISTING_USER flow should navigate to set-new-backup-code",
            R.id.personalid_set_new_backup_code_fragment,
            testNavController.currentDestination!!.id,
        )
    }

    // ========== showProceedWithoutEmailDialog ==========

    @Test
    fun `three failed OTP attempts show max-attempts error instead of dialog`() {
        mockWebServer.enqueue(incorrectOtpResponse())
        mockWebServer.enqueue(incorrectOtpResponse())
        mockWebServer.enqueue(incorrectOtpResponse())

        repeat(3) {
            onUiThread {
                fragment()
                    .requireView()
                    .findViewById<NumericCodeView>(R.id.otp_code_view)
                    .setCode("123456")
            }
            mockApiServer.drainHttp()
        }

        assertFalse(
            "FORGOT_BACKUP_CODE_EXISTING_USER should not show a visible dialog after 3 failed OTP attempts",
            ShadowDialog.getLatestDialog()?.isShowing ?: false,
        )

        val errorText =
            fragment().requireView().findViewById<TextView>(R.id.personalid_email_verify_error)
        assertEquals(
            "Error text should be visible after 3 failed attempts",
            View.VISIBLE,
            errorText.visibility,
        )
        assertEquals(
            "Max-attempts error message should be shown instead of the proceed-without-email dialog",
            activity.getString(R.string.personalid_email_otp_max_attempts_reached),
            errorText.text.toString(),
        )
    }

    // ========== Helpers ==========

    private fun successResponse(): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody("""{"status":"success"}""")

    private fun incorrectOtpResponse(): MockResponse =
        MockResponse()
            .setResponseCode(401)
            .setBody("""{"error_code":"INCORRECT_OTP"}""")

    companion object {
        private const val TEST_EMAIL = "user@example.com"
    }
}
