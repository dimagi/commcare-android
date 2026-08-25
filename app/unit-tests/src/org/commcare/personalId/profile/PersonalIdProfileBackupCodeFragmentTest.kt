package org.commcare.personalId.profile

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.commcare.personalId.PersonalIdUserPreferences
import org.commcare.views.connect.NumericCodeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdProfileBackupCodeFragmentTest : BasePersonalIdProfileTest() {
    @Before
    fun navigateToBackupCodeScreen() {
        PersonalIdUserPreferences.clearBackupCodeLockout()
        user.pin = "123456" // override to 6 digits so the code view can hold it
        onUiThread {
            navController.navigate(R.id.action_profile_to_profile_backup_code)
        }
    }

    private fun fragment() =
        navHostFragment.childFragmentManager
            .primaryNavigationFragment as PersonalIdProfileBackupCodeFragment

    private fun backupCodeView(): NumericCodeView = fragment().requireView().findViewById(R.id.backup_code_view)

    private fun confirmCodeLayout(): View = fragment().requireView().findViewById(R.id.confirm_code_layout)

    private fun confirmCodeLabel(): View = fragment().requireView().findViewById(R.id.confirm_code_label)

    private fun welcomeBackLayout(): View = fragment().requireView().findViewById(R.id.welcome_back_layout)

    private fun continueButton(): MaterialButton = fragment().requireView().findViewById(R.id.connect_backup_code_button)

    private fun errorMessage(): TextView = fragment().requireView().findViewById(R.id.connect_backup_code_error_message)

    private fun forgotButton(): TextView = fragment().requireView().findViewById(R.id.not_me_button)

    private fun setCodeAndContinue(code: String = "000000") {
        onUiThread { backupCodeView().setCode(code) }
        onUiThread { continueButton().performClick() }
    }

    // ===== Initial state =====

    @Test
    fun `confirm code layout is hidden`() {
        assertEquals(View.GONE, confirmCodeLabel().visibility)
        assertEquals(View.GONE, confirmCodeLayout().visibility)
    }

    @Test
    fun `welcome back layout is hidden`() {
        assertEquals(View.GONE, welcomeBackLayout().visibility)
    }

    @Test
    fun `continue button starts disabled`() {
        assertFalse(continueButton().isEnabled)
    }

    @Test
    fun `error message starts hidden`() {
        assertEquals(View.GONE, errorMessage().visibility)
    }

    @Test
    fun `forgot backup code link is visible`() {
        assertEquals(View.VISIBLE, forgotButton().visibility)
    }

    // ===== Validation =====

    @Test
    fun `continue button enables when 6 digits are entered`() {
        onUiThread { backupCodeView().setCode("000000") }
        ShadowLooper.idleMainLooper()

        assertTrue(continueButton().isEnabled)
    }

    @Test
    fun `continue button stays disabled until 6 digits`() {
        onUiThread { backupCodeView().setCode("12345") }
        ShadowLooper.idleMainLooper()

        assertFalse(continueButton().isEnabled)
    }

    // ===== Correct code =====

    @Test
    fun `correct code navigates to set-new-backup-code`() {
        setCodeAndContinue("123456")

        assertEquals(R.id.personalid_set_new_backup_code_fragment, currentDestinationId())
    }

    // ===== Wrong code =====

    @Test
    fun `wrong code shows error and stays on screen`() {
        setCodeAndContinue()
        assertEquals(View.VISIBLE, errorMessage().visibility)
        assertEquals(R.id.personalid_profile_backup_code_fragment, currentDestinationId())
    }

    @Test
    fun `continue button re-enables after wrong code`() {
        setCodeAndContinue()
        assertTrue(continueButton().isEnabled)
    }

    // ===== Locked state =====

    @Test
    fun `wrong code three times enters locked state`() {
        setCodeAndContinue()
        setCodeAndContinue()
        setCodeAndContinue()

        assertEquals(View.VISIBLE, forgotButton().visibility)
        assertFalse(backupCodeView().isEnabled)
        assertFalse(continueButton().isEnabled)
        assertEquals(View.VISIBLE, errorMessage().visibility)
        assertTrue(PersonalIdUserPreferences.isBackupCodeLockedOut())
    }

    @Test
    fun `continue button stays disabled in locked state even if code is set`() {
        setCodeAndContinue()
        setCodeAndContinue()
        setCodeAndContinue()

        onUiThread { backupCodeView().setCode("123456") }

        assertFalse(continueButton().isEnabled)
    }

    @Test
    fun `lockout in prefs causes locked state on fragment init`() {
        PersonalIdUserPreferences.triggerBackupCodeLockout()

        // Navigate back to profile, then forward again so the fragment is recreated
        onUiThread { navController.popBackStack() }
        ShadowLooper.idleMainLooper()
        onUiThread { navController.navigate(R.id.action_profile_to_profile_backup_code) }
        ShadowLooper.idleMainLooper()

        assertFalse(backupCodeView().isEnabled)
        assertFalse(continueButton().isEnabled)
        assertEquals(View.VISIBLE, errorMessage().visibility)
        assertEquals(View.VISIBLE, forgotButton().visibility)
    }

    @Test
    fun `third wrong attempt after 24 hours does not trigger lockout`() {
        // Two wrong attempts within the window
        setCodeAndContinue()
        onUiThread { backupCodeView().clearCode() }
        setCodeAndContinue()
        onUiThread { backupCodeView().clearCode() }

        // Backdated the window start to more than 24 hours ago so the next failure resets the count
        activity
            .getSharedPreferences("personalid_prefs", Context.MODE_PRIVATE)
            .edit()
            .putLong("backup_code_window_start", System.currentTimeMillis() - 25 * 60 * 60 * 1000L)
            .commit()

        // Third wrong attempt
        setCodeAndContinue()

        assertFalse(PersonalIdUserPreferences.isBackupCodeLockedOut())
        assertEquals(View.VISIBLE, errorMessage().visibility)
        assertTrue(continueButton().isEnabled)
    }

    // ===== Forgot =====

    @Test
    fun `forgot with email navigates to email verification`() {
        // user.email is "ada@example.com" per BasePersonalIdProfileTest
        onUiThread { forgotButton().performClick() }

        assertEquals(R.id.personalid_email_verification_fragment, currentDestinationId())
    }

    @Test
    fun `forgot with no email pops back to profile`() {
        user.email = null
        onUiThread { forgotButton().performClick() }

        assertEquals(
            activity.getString(R.string.personalid_no_email_forgot_backup_code_toast),
            ShadowToast.getTextOfLatestToast(),
        )
        assertEquals(R.id.personalid_profile_fragment, currentDestinationId())
    }
}
