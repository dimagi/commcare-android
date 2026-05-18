package org.commcare.fragments.personalId

import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdPhotoCaptureFragmentTest : BasePersonalIdPhotoCaptureFragmentTest() {

    // ========== UI initial state ==========

    @Test
    fun testInitialState_titleContainsUserName() {
        val title = fragment.view!!.findViewById<TextView>(R.id.title)
        assertEquals(
            fragment.getString(R.string.personalid_photo_capture_title, "Test User"),
            title.text.toString(),
        )
    }

    @Test
    fun testInitialState_savePhotoButtonDisabled() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        assertFalse("Save button should be disabled initially", saveButton.isEnabled)
    }

    @Test
    fun testInitialState_takePhotoButtonEnabled() {
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        assertTrue("Take photo button should be enabled initially", takeButton.isEnabled)
    }

    @Test
    fun testInitialState_errorTextViewHidden() {
        val errorView = fragment.view!!.findViewById<TextView>(R.id.errorTextView)
        assertEquals(View.GONE, errorView.visibility)
    }
}
