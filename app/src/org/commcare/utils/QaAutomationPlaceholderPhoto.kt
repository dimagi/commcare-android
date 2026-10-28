package org.commcare.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import org.commcare.activities.camera.MicroImageActivity
import org.commcare.dalvik.R
import java.io.IOException

/**
 * Builds a stand-in profile photo for the QA build to use on the photo page (to avoid the camera)
 */
object QaAutomationPlaceholderPhoto {
    @JvmStatic
    @Throws(IOException::class, ImageSizeTooLargeException::class)
    fun generateBase64(
        context: Context,
        maxDimensionPx: Int,
        maxSizeBytes: Int,
    ): String {
        val bitmap = renderSilhouette(context, maxDimensionPx)
        try {
            val compressed = MediaUtil.compressBitmapToTargetSize(bitmap, maxSizeBytes)
            return MicroImageActivity.BASE_64_IMAGE_PREFIX + Base64.encodeToString(compressed, Base64.DEFAULT)
        } finally {
            bitmap.recycle()
        }
    }

    private fun renderSilhouette(
        context: Context,
        sizePx: Int,
    ): Bitmap {
        val silhouette =
            requireNotNull(ContextCompat.getDrawable(context, R.drawable.baseline_person_24)) {
                "Placeholder photo drawable could not be loaded"
            }
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        silhouette.setBounds(0, 0, sizePx, sizePx)
        silhouette.draw(canvas)
        return bitmap
    }
}
