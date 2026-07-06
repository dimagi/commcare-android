package org.commcare.activities.camera

import android.net.Uri
import android.view.View
import android.widget.ImageView
import androidx.camera.core.ImageCapture
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class CameraOverlayActivityTest {
    private fun buildActivity(): CameraOverlayActivity {
        val intent =
            CameraOverlayActivity.getIntent(
                ApplicationProvider.getApplicationContext(),
                Uri.parse("content://org.commcare.dalvik.fileprovider/external/output.jpg"),
            )
        return Robolectric.buildActivity(CameraOverlayActivity::class.java, intent).create().get()
    }

    @Test
    fun `getIntent targets CameraOverlayActivity and carries the output uri`() {
        val uri = Uri.parse("content://org.commcare.dalvik.fileprovider/external/output.jpg")

        val intent =
            CameraOverlayActivity.getIntent(ApplicationProvider.getApplicationContext(), uri)

        assertEquals(CameraOverlayActivity::class.java.name, intent.component?.className)
        assertEquals(
            uri,
            IntentCompat.getParcelableExtra(intent, CameraOverlayActivity.OUTPUT_FILE_URI_EXTRA, Uri::class.java),
        )
    }

    @Test
    fun `entering capturing state disables the shutter and shows progress`() {
        val activity = buildActivity()
        val shutter = activity.findViewById<ImageView>(R.id.camera_shutter_button)
        val progress = activity.findViewById<View>(R.id.capture_progress)

        activity.handleCapturingState(true)

        assertFalse("Shutter should be disabled while capturing", shutter.isEnabled)
        assertEquals(0.5f, shutter.alpha, 0f)
        assertEquals(View.VISIBLE, progress.visibility)
    }

    @Test
    fun `leaving capturing state re-enables the shutter and hides progress`() {
        val activity = buildActivity()
        val shutter = activity.findViewById<ImageView>(R.id.camera_shutter_button)
        val progress = activity.findViewById<View>(R.id.capture_progress)
        activity.handleCapturingState(true)

        activity.handleCapturingState(false)

        assertTrue("Shutter should be re-enabled when not capturing", shutter.isEnabled)
        assertEquals(1f, shutter.alpha, 0f)
        assertEquals(View.GONE, progress.visibility)
    }

    @Test
    fun `handleCaptureError toasts the failure and re-enables the shutter without finishing`() {
        val activity = buildActivity()
        val shutter = activity.findViewById<ImageView>(R.id.camera_shutter_button)
        val progress = activity.findViewById<View>(R.id.capture_progress)
        activity.handleCapturingState(true)

        activity.handleCaptureError("capture blew up", null)

        assertEquals(
            activity.getString(R.string.image_capture_failed),
            ShadowToast.getTextOfLatestToast(),
        )
        assertTrue("Shutter should be re-enabled after an error so capture can be retried", shutter.isEnabled)
        assertEquals(View.GONE, progress.visibility)
        assertFalse("Capture errors are non-fatal; the activity must stay open", activity.isFinishing)
    }

    @Test
    fun `captureImage enters capturing state and delegates to ImageCapture`() {
        val activity = buildActivity()
        val shutter = activity.findViewById<ImageView>(R.id.camera_shutter_button)
        val progress = activity.findViewById<View>(R.id.capture_progress)
        val imageCapture = mock<ImageCapture>()
        activity.imageCapture = imageCapture

        activity.captureImage()

        verify(imageCapture).takePicture(any(), any(), any())
        assertFalse("Shutter should be disabled while the capture is in flight", shutter.isEnabled)
        assertEquals(View.VISIBLE, progress.visibility)
    }

    @Test
    fun `captureImage is a no-op while a capture is already in progress`() {
        val activity = buildActivity()
        val imageCapture = mock<ImageCapture>()
        activity.imageCapture = imageCapture
        activity.handleCapturingState(true)

        activity.captureImage()

        verify(imageCapture, never()).takePicture(any(), any(), any())
    }

    @Test(expected = NullPointerException::class)
    fun `captureImage crashes when the intent carries no output uri`() {
        val activity =
            Robolectric.buildActivity(CameraOverlayActivity::class.java).create().get()
        activity.imageCapture = mock()

        activity.captureImage()
    }

    @Test
    fun `captureImage does nothing when the camera is not ready`() {
        val activity = buildActivity()
        val shutter = activity.findViewById<ImageView>(R.id.camera_shutter_button)
        val progress = activity.findViewById<View>(R.id.capture_progress)
        activity.imageCapture = null

        activity.captureImage()

        assertTrue("Shutter stays enabled when capture cannot start", shutter.isEnabled)
        assertEquals(View.GONE, progress.visibility)
        assertFalse(activity.isFinishing)
    }
}
