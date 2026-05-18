package org.commcare.fragments.personalId

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.commcare.fragments.MicroImageActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

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

    // ========== Take photo flow ==========

    @Test
    fun testTakePhotoClick_disablesButtonAndLaunchesMicroImageActivity() {
        // Arrange: swap in a mock launcher so we can capture the intent.
        @Suppress("UNCHECKED_CAST")
        val mockLauncher =
            Mockito.mock(ActivityResultLauncher::class.java)
                as ActivityResultLauncher<Intent>
        fragment.replaceTakePhotoLauncher(mockLauncher)

        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)

        // Act
        activity.runOnUiThread { takeButton.performClick() }
        ShadowLooper.idleMainLooper()

        // Assert: button disabled and launcher invoked with the right Intent.
        assertFalse("Take photo button should be disabled after click", takeButton.isEnabled)
        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        Mockito.verify(mockLauncher).launch(intentCaptor.capture())
        val launched = intentCaptor.value
        assertEquals(
            MicroImageActivity::class.java.name,
            launched.component?.className,
        )
        assertEquals(
            160,
            launched.getIntExtra(MicroImageActivity.MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA, -1),
        )
        assertEquals(
            100 * 1024,
            launched.getIntExtra(MicroImageActivity.MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA, -1),
        )
    }
}
