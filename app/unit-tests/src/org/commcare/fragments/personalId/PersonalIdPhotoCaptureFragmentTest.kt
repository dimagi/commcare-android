package org.commcare.fragments.personalId

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.connect.network.ApiPersonalId
import org.commcare.dalvik.R
import org.commcare.fragments.MicroImageActivity
import org.commcare.utils.MediaUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    // ========== Photo result handling ==========

    @Test
    fun testPhotoResult_onSuccess_displaysImageAndEnablesSaveButton() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        val photoView = fragment.view!!.findViewById<ImageView>(R.id.photo_image_view)

        // Click first so the take-photo button gets disabled (the path under test re-enables it).
        @Suppress("UNCHECKED_CAST")
        val mockLauncher =
            Mockito.mock(ActivityResultLauncher::class.java)
                as ActivityResultLauncher<Intent>
        activity.runOnUiThread {
            fragment.replaceTakePhotoLauncher(mockLauncher)
            takeButton.performClick()
        }
        ShadowLooper.idleMainLooper()

        // Act: simulate the user finishing photo capture.
        activity.runOnUiThread {
            fragment.simulatePhotoResult(Activity.RESULT_OK, "fake-base64-photo")
        }
        ShadowLooper.idleMainLooper()

        // Assert
        assertTrue("Save button should be enabled after successful capture", saveButton.isEnabled)
        assertTrue("Take photo button should re-enable after capture", takeButton.isEnabled)
        mediaUtilMock.verify {
            MediaUtil.decodeBase64EncodedBitmap("fake-base64-photo")
        }
        assertNotNull("ImageView drawable should be set", photoView.drawable)
    }

    @Test
    fun testPhotoResult_onCancel_keepsSaveDisabledAndReenablesTakeButton() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)

        @Suppress("UNCHECKED_CAST")
        val mockLauncher =
            Mockito.mock(ActivityResultLauncher::class.java)
                as ActivityResultLauncher<Intent>
        activity.runOnUiThread {
            fragment.replaceTakePhotoLauncher(mockLauncher)
            takeButton.performClick()
        }
        ShadowLooper.idleMainLooper()

        // Act: simulate cancellation.
        activity.runOnUiThread {
            fragment.simulatePhotoResult(Activity.RESULT_CANCELED, null)
        }
        ShadowLooper.idleMainLooper()

        // Assert
        assertFalse("Save button should remain disabled on cancel", saveButton.isEnabled)
        assertTrue("Take photo button should re-enable on cancel", takeButton.isEnabled)
        mediaUtilMock.verify(
            { MediaUtil.decodeBase64EncodedBitmap(Mockito.anyString()) },
            Mockito.never(),
        )
    }

    // ========== Save / completeProfile ==========

    @Test
    fun testSavePhotoClick_callsCompleteProfileWithCorrectArgs() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        val errorView = fragment.view!!.findViewById<TextView>(R.id.errorTextView)

        // Arrange: simulate a captured photo so the save button is enabled and
        // photoAsBase64 is populated.
        @Suppress("UNCHECKED_CAST")
        val mockLauncher =
            Mockito.mock(ActivityResultLauncher::class.java)
                as ActivityResultLauncher<Intent>
        activity.runOnUiThread {
            fragment.replaceTakePhotoLauncher(mockLauncher)
            takeButton.performClick()
            fragment.simulatePhotoResult(Activity.RESULT_OK, "fake-base64-photo")
            // Seed a visible error so we can verify clearError() fires on save click.
            errorView.visibility = View.VISIBLE
            errorView.text = "stale error"
        }
        ShadowLooper.idleMainLooper()

        // Act
        activity.runOnUiThread { saveButton.performClick() }
        ShadowLooper.idleMainLooper()

        // Assert: API called with the session's args.
        apiPersonalIdMock.verify {
            ApiPersonalId.setPhotoAndCompleteProfile(
                Mockito.any(),
                Mockito.eq("Test User"),
                Mockito.eq("fake-base64-photo"),
                Mockito.eq("123456"),
                Mockito.eq("test-token"),
                Mockito.any(),
            )
        }
        // Buttons disabled while in flight.
        assertFalse("Save button disabled while save in flight", saveButton.isEnabled)
        assertFalse("Take photo button disabled while save in flight", takeButton.isEnabled)
        // Error cleared.
        assertEquals(View.GONE, errorView.visibility)
        assertEquals("", errorView.text.toString())
    }
}
