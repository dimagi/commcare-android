package org.commcare.fragments.personalId

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.commcare.personalId.PersonalIdUserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests [PersonalIdBackupCodeFragment] in account registration mode.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdBackupCodeFragmentTest : BasePersonalIdBackupCodeFragmentTest() {
    @Before
    override fun setUp() {
        super.setUp()
        launchBackupCodeFragment()
    }

    // ========== Initial State ==========

    @Test
    fun `screen uses the set-code title`() {
        assertEquals(fragment.getString(R.string.connect_backup_code_title_set), activity.title.toString())
    }

    @Test
    fun `subtitle states the required code length`() {
        assertEquals(
            fragment.getString(R.string.connect_backup_code_remember, 6),
            subtitle.text.toString(),
        )
    }

    @Test
    fun `confirm code field is shown in set mode`() {
        assertEquals(View.VISIBLE, confirmCodeLabel.visibility)
        assertEquals(View.VISIBLE, confirmCodeLayout.visibility)
    }

    @Test
    fun `welcome back header is hidden in set mode`() {
        assertEquals(View.GONE, welcomeBackLayout.visibility)
    }

    @Test
    fun `both code fields start empty with continue disabled and no error`() {
        assertTrue("Backup code should start empty", backupCodeView.codeValue.isEmpty())
        assertTrue("Confirm code should start empty", confirmCodeView.codeValue.isEmpty())
        assertFalse("Continue button should be disabled initially", continueButton.isEnabled)
        assertEquals(View.GONE, errorMessage.visibility)
    }

    @Test
    fun `codes are hidden by default`() {
        assertFalse("Backup code should be masked initially", backupCodeView.isPasswordVisible)
        assertFalse("Confirm code should be masked initially", confirmCodeView.isPasswordVisible)
    }

    // ========== Validation ==========

    @Test
    fun `a complete backup code alone keeps continue disabled and shows no error`() {
        enterBackupCode(TEST_BACKUP_CODE)

        assertFalse("Continue should stay disabled until the code is confirmed", continueButton.isEnabled)
        assertEquals("Error should not be shown while the confirm code is incomplete", View.GONE, errorMessage.visibility)
    }

    @Test
    fun `a partial backup code keeps continue disabled`() {
        enterBackupCode("123")

        assertFalse("Continue should stay disabled with fewer than 6 digits", continueButton.isEnabled)
    }

    @Test
    fun `a complete but mismatched confirm code shows the mismatch error`() {
        enterBackupCode(TEST_BACKUP_CODE)
        enterConfirmCode("654321")

        assertEquals(View.VISIBLE, errorMessage.visibility)
        assertEquals(
            fragment.getString(R.string.connect_backup_code_mismatch),
            errorMessage.text.toString(),
        )
        assertFalse("Continue should stay disabled while the codes differ", continueButton.isEnabled)
    }

    @Test
    fun `an incomplete confirm code does not show the mismatch error`() {
        enterBackupCode(TEST_BACKUP_CODE)
        enterConfirmCode("12")

        assertEquals(View.GONE, errorMessage.visibility)
        assertFalse(continueButton.isEnabled)
    }

    @Test
    fun `shortening a mismatched confirm code clears the error`() {
        enterBackupCode(TEST_BACKUP_CODE)
        enterConfirmCode("654321")
        assertEquals(View.VISIBLE, errorMessage.visibility)

        enterConfirmCode("65432")

        assertEquals(View.GONE, errorMessage.visibility)
        assertEquals("", errorMessage.text.toString())
    }

    // ========== Submission ==========

    @Test
    fun `matching codes store the backup code and navigate to photo capture`() {
        enterBackupCode(TEST_BACKUP_CODE)
        enterConfirmCode(TEST_BACKUP_CODE)

        assertEquals(TEST_BACKUP_CODE, sessionData.backupCode)
        assertEquals(R.id.personalid_photo_capture, navController.currentDestination?.id)
    }

    @Test
    fun `no email offer date is recorded when the email toggle is inactive`() {
        enterBackupCode(TEST_BACKUP_CODE)
        enterConfirmCode(TEST_BACKUP_CODE)

        assertNull(PersonalIdUserPreferences.getLastEmailOfferDate())
    }

    @Test
    fun `matching codes navigate to the email screen when the email toggle is active`() {
        activateEmailOtpToggle()

        enterBackupCode(TEST_BACKUP_CODE)
        enterConfirmCode(TEST_BACKUP_CODE)

        assertEquals(TEST_BACKUP_CODE, sessionData.backupCode)
        assertEquals(R.id.personalid_email, navController.currentDestination?.id)
        assertEquals(
            EmailWorkFlow.REGISTRATION,
            navController.backStack
                .last()
                .arguments
                ?.getSerializable("workflow"),
        )
        assertNotNull(
            "Navigating to the email screen should record the offer date",
            PersonalIdUserPreferences.getLastEmailOfferDate(),
        )
    }

    @Test
    fun `tapping continue submits when the codes were completed out of order`() {
        completeCodesWithoutAutoSubmit()

        clickView(continueButton)

        assertEquals(R.id.personalid_photo_capture, navController.currentDestination?.id)
    }

    @Test
    fun `pressing enter submits when the codes were completed out of order`() {
        completeCodesWithoutAutoSubmit()

        pressEnterOnBackupCode()

        assertEquals(R.id.personalid_photo_capture, navController.currentDestination?.id)
    }

    /**
     * Filling the confirm field first leaves the screen valid but unsubmitted: the confirm field's
     * completion listener runs while the button is still disabled, and the backup field's listener
     * only auto-submits during recovery. That is the only state from which the continue button and
     * the enter key are reachable in set mode.
     */
    private fun completeCodesWithoutAutoSubmit() {
        enterConfirmCode(TEST_BACKUP_CODE)
        enterBackupCode(TEST_BACKUP_CODE)

        assertTrue("Continue should be enabled once both codes match", continueButton.isEnabled)
        assertEquals(
            "Codes should not have been submitted yet",
            R.id.personalid_backup_code,
            navController.currentDestination?.id,
        )
    }

    // ========== Visibility Toggles ==========

    @Test
    fun `backup code visibility toggle flips only the backup code`() {
        clickView(backupCodeVisibilityToggle)

        assertTrue("Backup code should be revealed", backupCodeView.isPasswordVisible)
        assertFalse("Confirm code should stay masked", confirmCodeView.isPasswordVisible)

        clickView(backupCodeVisibilityToggle)

        assertFalse("Backup code should be masked again", backupCodeView.isPasswordVisible)
    }

    @Test
    fun `confirm code visibility toggle flips only the confirm code`() {
        clickView(confirmCodeVisibilityToggle)

        assertTrue("Confirm code should be revealed", confirmCodeView.isPasswordVisible)
        assertFalse("Backup code should stay masked", backupCodeView.isPasswordVisible)

        clickView(confirmCodeVisibilityToggle)

        assertFalse("Confirm code should be masked again", confirmCodeView.isPasswordVisible)
    }
}
