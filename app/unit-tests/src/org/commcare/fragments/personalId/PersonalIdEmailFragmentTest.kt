package org.commcare.fragments.personalId

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.MockAndroidKeyStoreProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.eq
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/**
 * Unit tests for PersonalIdEmailFragment using Robolectric to test actual fragment UI and behavior.
 * Tests initial state and the email-input -> continue-button enable/disable contract.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdEmailFragmentTest : BasePersonalIdEmailFragmentTest() {
    // ========== Initial State Tests ==========

    @Test
    fun `continue button is disabled on initial state`() {
        val continueButton = fragment.view?.findViewById<MaterialButton>(R.id.personalid_email_continue_button)
        assertFalse("Continue button should be disabled initially", continueButton!!.isEnabled)
    }

    @Test
    fun `email input is empty on initial state`() {
        val emailInput = fragment.view?.findViewById<TextInputEditText>(R.id.email_text_value)
        assertTrue("Email input should be empty at start", emailInput!!.text.toString().isEmpty())
    }

    @Test
    fun `error text is hidden on initial state`() {
        val errorText = fragment.view?.findViewById<TextView>(R.id.personalid_email_error)
        assertEquals("Error text should be GONE initially", View.GONE, errorText!!.visibility)
    }

    @Test
    fun `skip button is enabled on initial state`() {
        val skipButton = fragment.view?.findViewById<MaterialButton>(R.id.personalid_email_skip_button)
        assertTrue("Skip button should be enabled initially", skipButton!!.isEnabled)
    }

    // ========== Email Input Tests ==========

    @Test
    fun `valid email enables continue button`() {
        val emailInput = fragment.view?.findViewById<TextInputEditText>(R.id.email_text_value)
        val continueButton = fragment.view?.findViewById<MaterialButton>(R.id.personalid_email_continue_button)

        activity.runOnUiThread {
            emailInput?.setText("user@example.com")
        }
        ShadowLooper.idleMainLooper()

        assertTrue("Continue button should be enabled with a valid email", continueButton!!.isEnabled)
    }

    @Test
    fun `invalid email keeps continue button disabled`() {
        val emailInput = fragment.view?.findViewById<TextInputEditText>(R.id.email_text_value)
        val continueButton = fragment.view?.findViewById<MaterialButton>(R.id.personalid_email_continue_button)

        activity.runOnUiThread {
            emailInput?.setText("not-an-email")
        }
        ShadowLooper.idleMainLooper()

        assertFalse(
            "Continue button should remain disabled with an invalid email",
            continueButton!!.isEnabled,
        )
    }

    @Test
    fun `email with an incomplete top level domain keeps continue button disabled`() {
        val emailInput = fragment.view?.findViewById<TextInputEditText>(R.id.email_text_value)
        val continueButton = fragment.view?.findViewById<MaterialButton>(R.id.personalid_email_continue_button)

        activity.runOnUiThread {
            emailInput?.setText("user@gmail.c")
        }
        ShadowLooper.idleMainLooper()

        assertFalse(
            "Continue button should remain disabled for a single-character top-level domain",
            continueButton!!.isEnabled,
        )
    }

    @Test
    fun `blank email keeps continue button disabled`() {
        val emailInput = fragment.view?.findViewById<TextInputEditText>(R.id.email_text_value)
        val continueButton = fragment.view?.findViewById<MaterialButton>(R.id.personalid_email_continue_button)

        activity.runOnUiThread {
            emailInput?.setText("   ")
        }
        ShadowLooper.idleMainLooper()

        assertFalse(
            "Continue button should remain disabled with whitespace-only email",
            continueButton!!.isEnabled,
        )
    }

    @Test
    fun `editing a valid email to be invalid disables continue button`() {
        val emailInput = fragment.view?.findViewById<TextInputEditText>(R.id.email_text_value)
        val continueButton = fragment.view?.findViewById<MaterialButton>(R.id.personalid_email_continue_button)

        activity.runOnUiThread {
            emailInput?.setText("user@example.com")
        }
        ShadowLooper.idleMainLooper()
        assertTrue("Continue button should be enabled with valid email", continueButton!!.isEnabled)

        activity.runOnUiThread {
            emailInput?.setText("missing-at-sign")
        }
        ShadowLooper.idleMainLooper()

        assertFalse(
            "Continue button should be disabled once email becomes invalid",
            continueButton.isEnabled,
        )
    }

    // ========== RECOVERY workflow tests ==========

    @Test
    fun `skip in RECOVERY workflow passes backup_code as the recovery method`() {
        MockAndroidKeyStoreProvider.registerProvider()
        val args =
            Bundle().apply {
                putSerializable(PersonalIdEmailFragment.ARG_EMAIL_WORKFLOW, EmailWorkFlow.RECOVERY)
            }
        val sessionData =
            PersonalIdSessionData(
                token = "test-token",
                userName = "test-user",
                phoneNumber = "1234567890",
                requiredLock = PersonalIdSessionData.PIN,
                demoUser = false,
                dbKey = "dGVzdC1kYi1rZXk=",
                personalId = "test-personal-id",
                oauthPassword = "test-oauth-pwd",
            )
        navigateToFragment(sessionData, R.id.personalid_email, args)
        activity.runOnUiThread {
            installTestNavController(fragment.requireView(), R.id.personalid_email)
        }
        ShadowLooper.idleMainLooper()

        val skipButton = fragment.view?.findViewById<MaterialButton>(R.id.personalid_email_skip_button)
        activity.runOnUiThread { skipButton?.performClick() }
        ShadowLooper.idleMainLooper()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val yesButton = dialog.findViewById<Button>(R.id.positive_button)!!

        mockStatic(FirebaseAnalyticsUtil::class.java).use { mockAnalytics ->
            activity.runOnUiThread { yesButton.performClick() }
            ShadowLooper.idleMainLooper()
            mockAnalytics.verify {
                FirebaseAnalyticsUtil.reportPersonalIdAccountRecovered(
                    eq(true),
                    eq(AnalyticsParamValue.CCC_RECOVERY_METHOD_BACKUPCODE),
                )
            }
        }
    }
}
