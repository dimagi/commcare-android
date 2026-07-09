package org.commcare.personalId.profile

import android.view.Window
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.commcare.utils.PhoneNumberHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowDialog

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdProfileFragmentTest : BasePersonalIdProfileTest() {
    private fun fragment() =
        navHostFragment.childFragmentManager
            .primaryNavigationFragment as PersonalIdProfileFragment

    private fun headerName() = fragment().requireView().findViewById<TextView>(R.id.profile_name)

    private fun nameValue() = fragment().requireView().findViewById<TextView>(R.id.profile_value_name)

    private fun phoneValue() = fragment().requireView().findViewById<TextView>(R.id.profile_value_phone)

    private fun emailValue() = fragment().requireView().findViewById<TextView>(R.id.profile_value_email)

    private fun forgetButton() = fragment().requireView().findViewById<MaterialButton>(R.id.profile_btn_forget_personalid)

    @Test
    fun `the header and name field show the user name`() {
        assertEquals("Ada Lovelace", headerName().text.toString())
        assertEquals("Ada Lovelace", nameValue().text.toString())
    }

    @Test
    fun `the phone field shows the display-formatted phone number`() {
        val expectedPhone =
            PhoneNumberHelper
                .getInstance(fragment().requireContext())
                .formatForDisplay(user.primaryPhone)

        assertEquals(expectedPhone, phoneValue().text.toString())
    }

    @Test
    fun `the email field shows the user email`() {
        assertEquals("ada@example.com", emailValue().text.toString())
    }

    @Test
    fun `selecting the edit menu item navigates to the edit screen`() {
        val editItem = RoboMenuItem(R.id.action_profile_edit)

        onUiThread { activity.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, editItem) }

        assertEquals(R.id.personalid_profile_edit_fragment, currentDestinationId())
    }

    @Test
    fun `tapping forget personalid shows the confirmation dialog`() {
        onUiThread { forgetButton().performClick() }

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("Forget PersonalID should show a confirmation dialog", dialog)
        assertTrue("The confirmation dialog should be visible", dialog.isShowing)
    }
}
