package org.commcare.personalId

import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import org.commcare.CommCareTestApplication
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.profile.BasePersonalIdProfileTest
import org.commcare.views.connect.NumericCodeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class BackupCodeReminderDialogFragmentTest : BasePersonalIdProfileTest() {
    @Before
    fun setUpDialog() {
        PersonalIdUserPreferences.clear()
        user.pin = "123456"
        connectUserDatabaseUtilMock
            .`when`<Any> { ConnectUserDatabaseUtil.getUser(any()) }
            .thenReturn(user)
        showDialog()
    }

    private fun showDialog() {
        onUiThread {
            BackupCodeReminderDialogFragment
                .newInstance()
                .show(activity.supportFragmentManager, BackupCodeReminderDialogFragment.TAG)
        }
    }

    private fun dialog(): BackupCodeReminderDialogFragment =
        activity.supportFragmentManager
            .findFragmentByTag(BackupCodeReminderDialogFragment.TAG)
            as BackupCodeReminderDialogFragment

    private fun codeView(): NumericCodeView = dialog().requireView().findViewById(R.id.backup_code_view)

    private fun confirmButton(): MaterialButton = dialog().requireView().findViewById(R.id.confirm_button)

    private fun skipButton(): MaterialButton = dialog().requireView().findViewById(R.id.skip_button)

    private fun forgotButton(): TextView = dialog().requireView().findViewById(R.id.forgot_button)

    private fun errorBanner(): View = dialog().requireView().findViewById(R.id.error_banner)

    private fun errorMessage(): TextView = dialog().requireView().findViewById(R.id.error_message)

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
    fun `forgot button visible when user has email`() {
        // user.email = "ada@example.com" (set in BasePersonalIdProfileTest)
        assertEquals(View.VISIBLE, forgotButton().visibility)
    }

    @Test
    fun `forgot button hidden when user has no email`() {
        user.email = null
        // re-show dialog with updated user
        onUiThread { dialog().dismiss() }
        ShadowLooper.idleMainLooper()
        showDialog()

        assertEquals(View.GONE, forgotButton().visibility)
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

        assertNull(activity.supportFragmentManager.findFragmentByTag(BackupCodeReminderDialogFragment.TAG))
        assertEquals(
            activity.getString(R.string.personalid_backup_code_reminder_success_toast),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun `correct code calls scheduleNext`() {
        PersonalIdReminderHelper.initialize()
        enterCode("123456")
        clickConfirm()

        // After scheduleNext, next due should be ~3 days from now
        val nextDue = PersonalIdUserPreferences.getNextReminderDue()
        assertTrue(nextDue > System.currentTimeMillis())
    }

    // ===== Wrong code =====

    @Test
    fun `wrong code shows error banner with attempts remaining`() {
        enterCode("000000")
        clickConfirm()

        assertEquals(View.VISIBLE, errorBanner().visibility)
        assertTrue(errorMessage().text.contains("2"))
    }

    @Test
    fun `wrong code keeps dialog open`() {
        enterCode("000000")
        clickConfirm()

        assertTrue(
            activity.supportFragmentManager
                .findFragmentByTag(BackupCodeReminderDialogFragment.TAG) != null,
        )
    }

    // ===== Max attempts =====

    @Test
    fun `third wrong attempt dismisses dialog and shows danger toast`() {
        repeat(2) {
            enterCode("000000")
            clickConfirm()
            onUiThread { codeView().clearCode() }
        }
        enterCode("000000")
        clickConfirm()

        assertNull(activity.supportFragmentManager.findFragmentByTag(BackupCodeReminderDialogFragment.TAG))
        assertEquals(
            activity.getString(R.string.personalid_backup_code_reminder_max_attempts_toast),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    // ===== Skip =====

    @Test
    fun `skip dismisses dialog without toast`() {
        onUiThread { skipButton().performClick() }

        assertNull(activity.supportFragmentManager.findFragmentByTag(BackupCodeReminderDialogFragment.TAG))
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun `skip calls scheduleNext`() {
        PersonalIdReminderHelper.initialize()
        onUiThread { skipButton().performClick() }

        val nextDue = PersonalIdUserPreferences.getNextReminderDue()
        assertTrue(nextDue > System.currentTimeMillis())
    }
}
