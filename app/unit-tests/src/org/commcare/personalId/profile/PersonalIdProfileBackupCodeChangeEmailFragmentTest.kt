package org.commcare.personalId.profile

import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import org.commcare.CommCareTestApplication
import org.commcare.android.shadows.ShadowPhoneAuthProvider
import org.commcare.android.util.FirebaseTestUtils
import org.commcare.dalvik.R
import org.commcare.fragments.personalId.BackupCodeWorkflow
import org.commcare.fragments.personalId.EmailWorkFlow
import org.commcare.fragments.personalId.PersonalIdProfileSendEmailOtpFragmentArgs
import org.commcare.personalId.PersonalIdUserPreferences
import org.commcare.views.connect.NumericCodeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@Config(
    application = CommCareTestApplication::class,
    sdk = [31],
    shadows = [ShadowPhoneAuthProvider::class],
)
@RunWith(AndroidJUnit4::class)
class PersonalIdProfileBackupCodeChangeEmailFragmentTest : BasePersonalIdProfileTest() {
    private val fragmentArgs =
        PersonalIdProfileBackupCodeFragmentArgs
            .Builder(EmailWorkFlow.EXISTING_USER, BackupCodeWorkflow.EMAIL_CHANGE)
            .setPendingEmail("grace@example.com")
            .build()
            .toBundle()

    @Before
    fun navigateToBackupCodeForEmailChange() {
        FirebaseTestUtils.initializeDefaultAppIfNeeded()
        ShadowPhoneAuthProvider.reset()
        PersonalIdUserPreferences.clearBackupCodeLockout()
        user.pin = "123456"
        onUiThread {
            navController.navigate(R.id.personalid_profile_backup_code_fragment, fragmentArgs)
        }
    }

    private fun fragment() =
        navHostFragment.childFragmentManager
            .primaryNavigationFragment as PersonalIdProfileBackupCodeFragment

    private fun backupCodeView(): NumericCodeView = fragment().requireView().findViewById(R.id.backup_code_view)

    private fun continueButton(): MaterialButton = fragment().requireView().findViewById(R.id.connect_backup_code_button)

    private fun errorMessage(): TextView = fragment().requireView().findViewById(R.id.connect_backup_code_error_message)

    private fun forgotButton(): TextView = fragment().requireView().findViewById(R.id.personalid_forgot_backup_code)

    private fun setCodeAndContinue(code: String) {
        onUiThread { backupCodeView().setCode(code) }
        ShadowLooper.idleMainLooper()
        onUiThread { continueButton().performClick() }
    }

    @Test
    fun `correct code navigates to send email otp fragment`() {
        setCodeAndContinue("123456")
        assertEquals(R.id.personalid_send_email_otp_fragment, currentDestinationId())
        val args =
            PersonalIdProfileSendEmailOtpFragmentArgs.fromBundle(
                navHostFragment.childFragmentManager.primaryNavigationFragment!!.requireArguments(),
            )
        assertEquals("grace@example.com", args.email)
        assertFalse(args.masked)
    }

    @Test
    fun `wrong code shows error and stays on screen`() {
        setCodeAndContinue("000000")
        assertEquals(View.VISIBLE, errorMessage().visibility)
        assertEquals(R.id.personalid_profile_backup_code_fragment, currentDestinationId())
    }

    @Test
    fun `forgot backup code with no stored email routes to send phone otp with the pending email`() {
        user.email = null
        onUiThread { forgotButton().performClick() }

        assertEquals(R.id.personalid_profile_send_phone_otp_fragment, currentDestinationId())
        val args =
            PersonalIdProfileSendPhoneOtpFragmentArgs.fromBundle(
                navHostFragment.childFragmentManager.primaryNavigationFragment!!.requireArguments(),
            )
        assertEquals("grace@example.com", args.pendingEmail)
    }

    @Test
    fun `forgot backup code uses stored email not pending email`() {
        // user.email = "ada@example.com" (default) — stored email is used, not pendingEmail
        onUiThread { forgotButton().performClick() }

        assertEquals(R.id.personalid_send_email_otp_fragment, currentDestinationId())
        val args =
            PersonalIdProfileSendEmailOtpFragmentArgs.fromBundle(
                navHostFragment.childFragmentManager.primaryNavigationFragment!!.requireArguments(),
            )
        assertEquals("ada@example.com", args.email)
        assertEquals(EmailWorkFlow.FORGOT_BACKUP_CODE_EXISTING_USER, args.workflow)
    }
}
