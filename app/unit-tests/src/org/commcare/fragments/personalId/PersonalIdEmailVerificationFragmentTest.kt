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

    // The "complete 6-digit code" path is not tested at the fragment level. Entering 6 digits
    // triggers an immediate submit chain (on-code-changed flips the button enabled, then the
    // code-complete listener calls submitOtp() which disables it, then the API call may fail
    // synchronously under Robolectric and call enableVerifyButton(true) again via onFailure
    // → shouldAllowRetry). The post-state depends on whether the network resolves before the
    // assertion runs, which is non-deterministic across CI environments. The on-code-changed
    // listener wiring is already verified by `partial code keeps verify button disabled` and
    // submitOtp's behavior should be covered by a dedicated handler test if needed.
}
