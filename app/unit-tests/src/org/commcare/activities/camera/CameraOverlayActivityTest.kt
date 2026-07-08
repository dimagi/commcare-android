package org.commcare.activities.camera

import android.app.Activity
import android.net.Uri
import android.view.View
import android.widget.ImageView
import androidx.camera.core.ImageCapture
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.commcare.utils.StringUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class CameraOverlayActivityTest {
    private val outputUri = "content://org.commcare.dalvik.fileprovider/external/output.jpg"

    private fun buildActivity(withOutputUri: Boolean = true): CameraOverlayActivity {
        val builder =
            if (withOutputUri) {
                Robolectric.buildActivity(
                    CameraOverlayActivity::class.java,
                    CameraOverlayActivity.getIntent(ApplicationProvider.getApplicationContext(), Uri.parse(outputUri)),
                )
            } else {
                Robolectric.buildActivity(CameraOverlayActivity::class.java)
            }
        return builder.create().get()
    }

    private fun CameraOverlayActivity.shutter() = findViewById<ImageView>(R.id.camera_shutter_button)

    private fun CameraOverlayActivity.progress() = findViewById<View>(R.id.capture_progress)

    @Test
    fun `getIntent targets CameraOverlayActivity and carries the output uri`() {
        val uri = Uri.parse(outputUri)

        val intent =
            CameraOverlayActivity.getIntent(ApplicationProvider.getApplicationContext(), uri)

        assertEquals(CameraOverlayActivity::class.java.name, intent.component!!.className)
        assertEquals(
            uri,
            IntentCompat.getParcelableExtra(intent, CameraOverlayActivity.OUTPUT_FILE_URI_EXTRA, Uri::class.java),
        )
    }

    @Test
    fun `clicking the shutter starts a capture and shows the capturing state`() {
        val activity = buildActivity()
        val imageCapture = mock<ImageCapture>()
        activity.imageCapture = imageCapture

        activity.shutter().performClick()

        verify(imageCapture).takePicture(any(), any(), any())
        assertFalse("Shutter should be disabled while capturing", activity.shutter().isEnabled)
        assertEquals(0.5f, activity.shutter().alpha, 0f)
        assertEquals(View.VISIBLE, activity.progress().visibility)
    }

    @Test
    fun `captureImage finishes with RESULT_OK when the image is saved`() {
        val activity = buildActivity()
        val imageCapture =
            mock<ImageCapture> {
                on { takePicture(any(), any(), any()) } doAnswer {
                    it.getArgument<ImageCapture.OnImageSavedCallback>(2).onImageSaved(mock())
                }
            }
        activity.imageCapture = imageCapture

        activity.shutter().performClick()

        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertTrue("A successful capture should finish the activity", activity.isFinishing)
    }

    @Test
    fun `captureImage surfaces a retryable error when ImageCapture reports onError`() {
        val activity = buildActivity()
        val imageCapture =
            mock<ImageCapture> {
                on { takePicture(any(), any(), any()) } doAnswer {
                    it.getArgument<ImageCapture.OnImageSavedCallback>(2).onError(mock())
                }
            }
        activity.imageCapture = imageCapture

        activity.shutter().performClick()

        assertEquals(
            StringUtils.getStringRobust(activity, R.string.image_capture_failed),
            ShadowToast.getTextOfLatestToast(),
        )
        assertTrue("Shutter should be re-enabled after an error so capture can be retried", activity.shutter().isEnabled)
        assertEquals(View.GONE, activity.progress().visibility)
        assertFalse("Capture errors are non-fatal; the activity must stay open", activity.isFinishing)
    }

    @Test
    fun `clicking the shutter again while a capture is in progress is a no-op`() {
        val activity = buildActivity()
        val imageCapture = mock<ImageCapture>()
        activity.imageCapture = imageCapture

        activity.shutter().performClick()
        activity.shutter().performClick()

        verify(imageCapture, times(1)).takePicture(any(), any(), any())
    }

    @Test
    fun `clicking the shutter does nothing when the camera is not ready`() {
        val activity = buildActivity()
        activity.imageCapture = null

        activity.shutter().performClick()

        assertTrue("Shutter stays enabled when capture cannot start", activity.shutter().isEnabled)
        assertEquals(View.GONE, activity.progress().visibility)
        assertFalse(activity.isFinishing)
    }

    @Test(expected = NullPointerException::class)
    fun `clicking the shutter crashes when the intent carries no output uri`() {
        val activity = buildActivity(withOutputUri = false)
        activity.imageCapture = mock()

        activity.shutter().performClick()
    }
}
