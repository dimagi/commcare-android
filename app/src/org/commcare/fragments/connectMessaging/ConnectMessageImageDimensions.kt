package org.commcare.fragments.connectMessaging

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import org.javarosa.core.services.Logger
import java.io.File
import java.io.IOException

object ConnectMessageImageDimensions {
    fun read(file: File): ConnectMessageMediaSizer.Size? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null
        }
        return if (isStoredSideways(file)) {
            ConnectMessageMediaSizer.Size(options.outHeight, options.outWidth)
        } else {
            ConnectMessageMediaSizer.Size(options.outWidth, options.outHeight)
        }
    }

    private fun isStoredSideways(file: File): Boolean =
        try {
            ExifInterface(file).rotationDegrees % 180 != 0
        } catch (e: IOException) {
            Logger.exception("Could not read EXIF orientation of ${file.name}", e)
            false
        }
}
