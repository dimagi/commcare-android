package org.commcare.fragments.personalId

import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * UI-only unit tests for PersonalIdNameFragment. Verifies initial state and the name TextWatcher
 * that drives the continue button. Network/navigation behavior — including the enter-key
 * shortcut, which can only meaningfully fire when the API is involved — lives in
 * [PersonalIdNameFragmentAddOrVerifyNameTest].
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdNameFragmentTest : BasePersonalIdNameFragmentTest() {
    // ========== Helpers ==========

    private fun nameInput(): EditText = fragment.requireView().findViewById(R.id.nameTextValue)

    private fun continueButton(): MaterialButton = fragment.requireView().findViewById(R.id.personalid_name_continue_button)

    private fun errorView(): TextView = fragment.requireView().findViewById(R.id.personalid_name_error)

    private fun setName(value: String) {
        activity.runOnUiThread { nameInput().setText(value) }
        ShadowLooper.idleMainLooper()
    }

    // ========== Initial UI state ==========

    @Test
    fun `continue button is disabled before any name is typed`() {
        assertFalse(continueButton().isEnabled)
    }

    @Test
    fun `name input is empty on first render`() {
        assertEquals("", nameInput().text.toString())
    }

    @Test
    fun `error text view starts hidden`() {
        assertEquals(View.GONE, errorView().visibility)
    }

    // ========== TextWatcher behavior ==========

    @Test
    fun `typing a non-empty name enables the continue button`() {
        setName("Ada Lovelace")

        assertTrue(continueButton().isEnabled)
    }

    @Test
    fun `clearing the name disables the continue button again`() {
        setName("Ada Lovelace")
        assertTrue(continueButton().isEnabled)

        setName("")

        assertFalse(continueButton().isEnabled)
    }

    @Test
    fun `whitespace-only input does not enable the continue button`() {
        setName("    ")

        assertFalse(continueButton().isEnabled)
    }

    @Test
    fun `leading and trailing whitespace still enables the continue button when a name is present`() {
        setName("  Grace Hopper  ")

        assertTrue(continueButton().isEnabled)
    }
}
