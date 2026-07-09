package org.commcare.personalId.profile

import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareTestApplication
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.utils.PhoneNumberHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdProfileEditFragmentTest : BasePersonalIdProfileTest() {
    private lateinit var fragment: PersonalIdProfileEditFragment

    @Before
    fun navigateToEditFragment() {
        onUiThread { navController.navigate(R.id.action_profile_to_profile_edit) }
        fragment =
            navHostFragment.childFragmentManager
                .primaryNavigationFragment as PersonalIdProfileEditFragment
    }

    private fun nameField() = fragment.requireView().findViewById<TextInputEditText>(R.id.profile_name_edit_text)

    private fun emailField() = fragment.requireView().findViewById<TextInputEditText>(R.id.profile_email_edit_text)

    private fun phoneField() = fragment.requireView().findViewById<TextInputEditText>(R.id.profile_phone_edit_text)

    private fun emailInputLayout() = fragment.requireView().findViewById<TextInputLayout>(R.id.profile_input_email)

    private fun phoneInputLayout() = fragment.requireView().findViewById<TextInputLayout>(R.id.profile_input_phone)

    private fun saveButton() = fragment.requireView().findViewById<MaterialButton>(R.id.btn_save)

    private fun clickSave() {
        onUiThread { saveButton().performClick() }
    }

    // ========== Prefill / initial state ==========

    @Test
    fun `fields prefill from the user record`() {
        val expectedPhone =
            PhoneNumberHelper
                .getInstance(fragment.requireContext())
                .formatForDisplay(user.primaryPhone)

        assertEquals("Ada Lovelace", nameField().text.toString())
        assertEquals("ada@example.com", emailField().text.toString())
        assertEquals(expectedPhone, phoneField().text.toString())
        assertFalse("Phone field should be disabled", phoneInputLayout().isEnabled)
    }

    @Test
    fun `save button is disabled on initial state`() {
        assertFalse("Save button should be disabled before any change", saveButton().isEnabled)
    }

    @Test
    fun `no email error is shown on initial state`() {
        assertNull("Email error should be absent initially", emailInputLayout().error)
    }

    // ========== Save button enable/disable contract ==========

    @Test
    fun `a valid name change enables the save button`() {
        setText(nameField(), "Grace Hopper")

        assertTrue("Save button should enable after a valid name change", saveButton().isEnabled)
    }

    @Test
    fun `a blank name keeps the save button disabled`() {
        setText(nameField(), "   ")

        assertFalse("Save button should stay disabled with a blank name", saveButton().isEnabled)
    }

    @Test
    fun `a malformed email keeps the save button disabled and shows the invalid error`() {
        setText(emailField(), "not-an-email")

        assertFalse("Save button should stay disabled with a malformed email", saveButton().isEnabled)
        assertEquals(
            fragment.getString(R.string.personalid_profile_edit_error_email_invalid),
            emailInputLayout().error,
        )
    }

    @Test
    fun `clearing an existing email keeps the save button disabled and shows the required error`() {
        setText(emailField(), "")

        assertFalse("Save button should stay disabled when clearing an existing email", saveButton().isEnabled)
        assertEquals(
            fragment.getString(R.string.personalid_profile_edit_error_email_required),
            emailInputLayout().error,
        )
    }

    @Test
    fun `a valid email change enables the save button and clears the error`() {
        setText(emailField(), "not-an-email")
        assertNotNull("Precondition: an invalid email shows an error", emailInputLayout().error)

        setText(emailField(), "grace@example.com")

        assertTrue("Save button should enable after a valid email change", saveButton().isEnabled)
        assertNull("Email error should clear once the email is valid", emailInputLayout().error)
    }

    // ========== Save flow ==========

    @Test
    fun `saving a name change sends the update-profile request and disables the save button in flight`() {
        setText(nameField(), "Grace Hopper")

        // No response is enqueued so the call stays in flight, keeping the disabled assertion deterministic.
        clickSave()

        val request = mockApiServer.takeRequestOrFail()
        assertEquals("/users/update_profile", request.path)
        assertEquals("POST", request.method)
        assertTrue(
            "Update-profile body should carry the new name",
            request.body.readUtf8().contains("Grace"),
        )
        assertFalse("Save button should disable while the update is in flight", saveButton().isEnabled)
    }

    @Test
    fun `a successful profile update persists the user and pops back`() {
        setText(nameField(), "Grace Hopper")
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        clickSave()
        mockApiServer.drainHttp()

        connectUserDatabaseUtilMock.verify {
            ConnectUserDatabaseUtil.storeUser(any(), any())
        }
        assertEquals(
            "Should pop back to the profile screen after a successful save",
            R.id.personalid_profile_fragment,
            currentDestinationId(),
        )

        val profileFragment = navHostFragment.childFragmentManager.primaryNavigationFragment!!
        val displayedName = profileFragment.requireView().findViewById<TextView>(R.id.profile_value_name)
        assertEquals(
            "The profile screen should show the updated name after saving",
            "Grace Hopper",
            displayedName.text.toString(),
        )
    }

    @Test
    fun `editing the email and saving shows the otp confirmation dialog`() {
        setText(emailField(), "grace@example.com")

        clickSave()

        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
        assertNotNull("An OTP confirmation dialog should be shown when the email changed", dialog)
        assertTrue("OTP confirmation dialog should be visible", dialog!!.isShowing)
        assertEquals(
            "No profile-update request should fire before the OTP dialog is confirmed",
            0,
            mockWebServer.requestCount,
        )
    }

    @Test
    fun `confirming the otp dialog sends the email otp`() {
        setText(emailField(), "grace@example.com")
        clickSave()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val confirmButton = dialog.findViewById<Button>(R.id.positive_button)!!
        // No response is enqueued so the OTP request is dispatched but its success callback (which
        // would navigate to the separately-tested email-verification screen) never runs, keeping
        // the assertion on the send boundary.
        onUiThread { confirmButton.performClick() }

        val request = mockApiServer.takeRequestOrFail()
        assertEquals("/users/send_email_otp", request.path)
    }

    @Test
    fun `saving a simultaneous name and email change commits the name before sending the email otp`() {
        setText(nameField(), "Grace Hopper")
        setText(emailField(), "grace@example.com")

        clickSave()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val confirmButton = dialog.findViewById<Button>(R.id.positive_button)!!
        // The name commit must succeed so the flow proceeds to the OTP send; no OTP response is
        // enqueued so the success callback (which navigates away) never runs.
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        onUiThread { confirmButton.performClick() }

        val nameRequest = mockApiServer.takeRequestOrFail()
        assertEquals("/users/update_profile", nameRequest.path)
        assertTrue(
            "Name should be committed first",
            nameRequest.body.readUtf8().contains("Grace"),
        )

        val otpRequest = mockApiServer.takeRequestOrFail()
        assertEquals("/users/send_email_otp", otpRequest.path)
    }
}
