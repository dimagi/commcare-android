package org.commcare.activities.camera

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Size
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.UseCase
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import org.commcare.dalvik.R
import org.commcare.util.LogTypes
import org.javarosa.core.services.Logger
import java.io.IOException
import java.io.OutputStream

/**
 * Back-camera capture screen that draws a static [org.commcare.views.RectangleOverlayView] reticle
 * over the preview as a framing guide. Capture is manual via the shutter
 * button; the full-resolution frame is written to the caller-supplied output URI without cropping,
 * so the reticle never appears in the saved image.
 */
class CameraOverlayActivity : BaseCameraActivity() {
    private val outputUri: Uri by lazy { intentOutputUri()!! }

    @VisibleForTesting
    internal var imageCapture: ImageCapture? = null
    private var isCapturing = false

    override fun getContentLayout(): Int = R.layout.camera_overlay_activity

    override fun getTitleRes(): Int = R.string.image_capture_activity_title

    override fun getCameraView(): PreviewView = findViewById(R.id.view_finder)

    override fun getCameraSelector(): CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    override fun getTargetResolution(): Size = PREVIEW_TARGET_RESOLUTION

    override fun buildCaptureUseCase(
        targetResolution: Size?,
        targetRotation: Int,
    ): UseCase {
        val capture =
            ImageCapture
                .Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(targetRotation)
                .build()
        imageCapture = capture

        findViewById<ImageView>(R.id.camera_shutter_button).setOnClickListener { captureImage() }
        return capture
    }

    @VisibleForTesting
    internal fun captureImage() {
        if (isCapturing) {
            return
        }
        val capture = imageCapture ?: return
        val outputStream =
            contentResolver.openOutputStream(outputUri) ?: run {
                handleCaptureError("Unable to open output stream for captured image", null)
                return
            }
        handleCapturingState(true)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputStream).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    outputStream.closeQuietly()
                    setResult(RESULT_OK)
                    finish()
                }

                override fun onError(exception: ImageCaptureException) {
                    outputStream.closeQuietly()
                    handleCaptureError("Failed to capture image", exception)
                }
            },
        )
    }

    /**
     * Reports a non-fatal capture failure: logs it, toasts the user, and re-enables the shutter so
     * the capture can be retried without leaving the screen.
     */
    @VisibleForTesting
    internal fun handleCaptureError(
        logMessage: String,
        e: Throwable?,
    ) {
        if (e == null) {
            Logger.log(LogTypes.TYPE_EXCEPTION, logMessage)
        } else {
            Logger.exception(logMessage, e)
        }
        Toast.makeText(this, getString(R.string.image_capture_failed), Toast.LENGTH_LONG).show()
        handleCapturingState(false)
    }

    @VisibleForTesting
    internal fun handleCapturingState(capturing: Boolean) {
        isCapturing = capturing
        findViewById<ImageView>(R.id.camera_shutter_button).apply {
            isEnabled = !capturing
            alpha = if (capturing) DISABLED_SHUTTER_ALPHA else 1f
        }
        findViewById<View>(R.id.capture_progress).visibility =
            if (capturing) View.VISIBLE else View.GONE
    }

    private fun OutputStream.closeQuietly() {
        try {
            close()
        } catch (e: IOException) {
            Logger.exception("Failed to close output stream after image capture", e)
        }
    }

    private fun intentOutputUri(): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(OUTPUT_FILE_URI_EXTRA, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(OUTPUT_FILE_URI_EXTRA)
        }

    companion object {
        const val OUTPUT_FILE_URI_EXTRA = "camera_overlay_output_file_uri_extra"

        private const val DISABLED_SHUTTER_ALPHA = 0.5f

        private val PREVIEW_TARGET_RESOLUTION = Size(1080, 1920)

        @JvmStatic
        fun getIntent(
            context: Context,
            outputFileUri: Uri,
        ): Intent =
            Intent(context, CameraOverlayActivity::class.java)
                .putExtra(OUTPUT_FILE_URI_EXTRA, outputFileUri)
    }
}
