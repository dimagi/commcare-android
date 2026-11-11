package org.commcare.fragments.connectMessaging

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ConnectMessageMediaSizer {
    const val MAX_HEIGHT_FRACTION_OF_WINDOW = 0.4f

    data class Size(
        val width: Int,
        val height: Int,
    )

    fun fitImage(
        intrinsicWidth: Int,
        intrinsicHeight: Int,
        maxWidth: Int,
        maxHeight: Int,
        pixelsPerDp: Float,
        minimumSize: Int,
    ): Size {
        require(intrinsicWidth > 0 && intrinsicHeight > 0) {
            "Image dimensions must be positive, got ${intrinsicWidth}x$intrinsicHeight"
        }
        val aspectRatio = intrinsicWidth.toFloat() / intrinsicHeight
        val widthLimit = min(maxWidth.toFloat(), maxHeight * aspectRatio)
        var width = min(widthLimit, intrinsicWidth * pixelsPerDp)
        val shorterSide = min(width, width / aspectRatio)
        if (shorterSide < minimumSize) {
            width = min(widthLimit, width * minimumSize / shorterSide)
        }
        val height = width / aspectRatio
        return Size(max(1, width.roundToInt()), max(1, height.roundToInt()))
    }

    fun isLargeEnoughToOpen(
        intrinsicWidth: Int,
        intrinsicHeight: Int,
        pixelsPerDp: Float,
        minimumSize: Int,
    ): Boolean = intrinsicWidth * pixelsPerDp >= minimumSize && intrinsicHeight * pixelsPerDp >= minimumSize
}
