package org.commcare.fragments.personalId

import android.os.Build
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Unit tests for PersonalIdEmailVerificationFragment using Robolectric.
 * Covers initial UI state and the OTP-input -> verify-button enable/disable contract.
 *
 * Pinned to API 32 because NumericCodeView uses try-with-resources on TypedArray, and
 * TypedArray.close() was added in API 31. Robolectric 4.8.2 supports up to API 32 (S_V2).
 */
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.S_V2])
@RunWith(AndroidJUnit4::class)
class PersonalIdEmailVerificationFragmentTest : BasePersonalIdEmailVerificationFragmentTest() {
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

        activity.runOnUiThread {
            codeView?.setCode("123")
        }
        ShadowLooper.idleMainLooper()

        assertFalse(
            "Verify button should remain disabled with fewer than 6 digits",
            verifyButton!!.isEnabled,
        )
    }

    @Test
    fun `complete 6-digit code triggers submission and disables verify button`() {
        // NumericCodeView fires its code-complete listener synchronously when a 6-digit code
        // is entered, which calls submitOtp(). submitOtp() in turn disables the verify button
        // at the start of the request. So even though the on-code-changed listener flips the
        // button to enabled at length==6, the auto-submit immediately flips it back. From the
        // user's perspective: typing the 6th digit kicks off verification, button stays disabled
        // until the network response.
        val codeView = fragment.view?.findViewById<NumericCodeView>(R.id.otp_code_view)
        val verifyButton =
            fragment.view?.findViewById<MaterialButton>(R.id.personalid_email_verify_button)

        activity.runOnUiThread {
            codeView?.setCode("123456")
        }
        ShadowLooper.idleMainLooper()

        assertFalse(
            "Verify button should be disabled while submission is in flight",
            verifyButton!!.isEnabled,
        )
    }
}
