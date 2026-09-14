package org.commcare.personalId

import android.view.View
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.commcare.personalId.profile.BasePersonalIdProfileTest
import org.commcare.personalId.profile.PersonalIdProfileActivity
import org.commcare.views.connect.NumericCodeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class BackupCodeReminderDialogFragmentTest : BasePersonalIdProfileTest() {
    @Before
    fun setUpDialog() {
        PersonalIdUserPreferences.clear()
        user.pin = "123456"
        showDialog()
    }

    private fun showDialog() {
        onUiThread {
            BackupCodeReminderDialog.show(activity)
        }
    }

    private fun dialogView() = activity.getCurrentAlertDialog()!!.dialog!!

    private fun codeView(): NumericCodeView = dialogView().findViewById(R.id.backup_code_view)

    private fun confirmButton(): MaterialButton = dialogView().findViewById(R.id.confirm_button)

    private fun skipButton(): MaterialButton = dialogView().findViewById(R.id.skip_button)

    private fun forgotButton(): TextView = dialogView().findViewById(R.id.forgot_button)

    private fun errorBanner(): View = dialogView().findViewById(R.id.error_banner)

    private fun errorMessage(): TextView = dialogView().findViewById(R.id.error_message)

    private fun enterCode(code: String) {
        onUiThread { codeView().setCode(code) }
    }

    private fun clickConfirm() {
        onUiThread { confirmButton().performClick() }
    }

    // ===== Initial state =====

    @Test
    fun `confirm button disabled initially`() {
        assertFalse(confirmButton().isEnabled)
    }

    @Test
    fun `error banner hidden initially`() {
        assertEquals(View.GONE, errorBanner().visibility)
    }

    @Test
    fun `forgot button is visible`() {
        assertEquals(View.VISIBLE, forgotButton().visibility)
    }

    // ===== Confirm button enable/disable =====

    @Test
    fun `confirm button enables when 6 digits entered`() {
        enterCode("123456")
        assertTrue(confirmButton().isEnabled)
    }

    @Test
    fun `confirm button stays disabled with fewer than 6 digits`() {
        enterCode("12345")
        assertFalse(confirmButton().isEnabled)
    }

    // ===== Correct code =====

    @Test
    fun `correct code dismisses dialog and shows success toast`() {
        enterCode("123456")
        clickConfirm()

        assertNull(activity.getCurrentAlertDialog())
        assertEquals(
            activity.getString(R.string.personalid_backup_code_reminder_success_toast),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    // ===== Wrong code =====

    @Test
    fun `wrong code shows error banner with attempts remaining`() {
        enterCode("000000")
        clickConfirm()

        assertEquals(View.VISIBLE, errorBanner().visibility)
        assertEquals(
            activity.getString(R.string.personalid_backup_code_reminder_incorrect, 2),
            errorMessage().text.toString(),
        )
    }

    @Test
    fun `wrong code keeps dialog open`() {
        enterCode("000000")
        clickConfirm()

        assertTrue(activity.getCurrentAlertDialog() != null)
    }

    // ===== Max attempts =====

    @Test
    fun `third wrong attempt dismisses dialog, shows danger toast, and opens recovery screen`() {
        repeat(2) {
            enterCode("000000")
            clickConfirm()
            onUiThread { codeView().clearCode() }
        }
        enterCode("000000")
        clickConfirm()

        assertNull(activity.getCurrentAlertDialog())
        assertEquals(
            activity.getString(R.string.personalid_backup_code_reminder_max_attempts_toast),
            ShadowToast.getTextOfLatestToast(),
        )

        val startedIntent = shadowOf(activity).nextStartedActivity
        val recoveryActivity =
            Robolectric
                .buildActivity(PersonalIdProfileActivity::class.java, startedIntent)
                .create()
                .postCreate(null)
                .start()
                .resume()
                .get()
        val recoveryNavHost =
            recoveryActivity.supportFragmentManager
                .findFragmentById(R.id.profile_nav_host) as NavHostFragment
        assertEquals(R.id.personalid_send_email_otp_fragment, recoveryNavHost.navController.currentDestination?.id)
    }

    // ===== Skip =====

    @Test
    fun `skip dismisses dialog without toast`() {
        onUiThread { skipButton().performClick() }

        assertNull(activity.getCurrentAlertDialog())
        assertNull(ShadowToast.getLatestToast())
    }
}
