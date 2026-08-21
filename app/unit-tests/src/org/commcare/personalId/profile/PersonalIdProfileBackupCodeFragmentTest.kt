package org.commcare.personalId.profile

import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.commcare.views.connect.NumericCodeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdProfileBackupCodeFragmentTest : BasePersonalIdProfileTest() {
    @Before
    fun navigateToBackupCodeScreen() {
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

    private fun errorMessage(): View = fragment().requireView().findViewById(R.id.backup_code_error_box)

    private fun forgotButton(): TextView = fragment().requireView().findViewById(R.id.not_me_button)

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
        onUiThread { backupCodeView().setCode("000000") } // wrong code — auto-submits, shows error, re-enables button
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
        onUiThread { backupCodeView().setCode("123456") }
        ShadowLooper.idleMainLooper()
        // Note: setCodeCompleteListener auto-submits when 6 digits are entered, so no need to click

        assertEquals(R.id.personalid_set_new_backup_code_fragment, currentDestinationId())
    }

    // ===== Wrong code =====

    @Test
    fun `wrong code shows error and stays on screen`() {
        onUiThread { backupCodeView().setCode("000000") }
        ShadowLooper.idleMainLooper()
        onUiThread { continueButton().performClick() }

        assertEquals(View.VISIBLE, errorMessage().visibility)
        assertEquals(R.id.personalid_profile_backup_code_fragment, currentDestinationId())
    }

    @Test
    fun `continue button re-enables after wrong code`() {
        onUiThread { backupCodeView().setCode("000000") }
        ShadowLooper.idleMainLooper()
        onUiThread { continueButton().performClick() }

        assertTrue(continueButton().isEnabled)
    }

    // ===== Locked state =====

    @Test
    fun `wrong code three times enters locked state`() {
        // Each setCode("000000") auto-submits via setCodeCompleteListener (one attempt per call).
        // Clear between attempts so validateCode re-enables the button before the next submission.
        // Do NOT clear after the third attempt — that would re-invoke validateCode and undo the
        // locked state's button setup.
        onUiThread { backupCodeView().setCode("000000") }
        ShadowLooper.idleMainLooper()
        onUiThread { backupCodeView().clearCode() }
        ShadowLooper.idleMainLooper()
        onUiThread { backupCodeView().setCode("000000") }
        ShadowLooper.idleMainLooper()
        onUiThread { backupCodeView().clearCode() }
        ShadowLooper.idleMainLooper()
        onUiThread { backupCodeView().setCode("000000") }
        ShadowLooper.idleMainLooper()

        assertEquals(View.GONE, forgotButton().visibility)
        assertFalse(backupCodeView().isEnabled)
        assertEquals(
            fragment().getString(R.string.personalid_go_back_label),
            continueButton().text.toString(),
        )
        assertTrue(continueButton().isEnabled)
        assertEquals(View.VISIBLE, errorMessage().visibility)
    }

    // ===== Forgot — user has email =====

    @Test
    fun `forgot with email navigates to email verification`() {
        // user.email is "ada@example.com" per BasePersonalIdProfileTest
        onUiThread { forgotButton().performClick() }

        assertEquals(R.id.personalid_email_verification_fragment, currentDestinationId())
    }

    // ===== Forgot — user has no email =====

    @Test
    fun `forgot with no email pops back to profile`() {
        user.email = null
        onUiThread { forgotButton().performClick() }

        assertEquals(R.id.personalid_profile_fragment, currentDestinationId())
    }
}
