package org.commcare.fragments.connectMessaging

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ConnectMessageMediaSizer {
    const val MAX_HEIGHT_FRACTION_OF_LIST = 0.5f

    data class Size(
        val width: Int,
        val height: Int,
    )

    fun fitImage(
        intrinsicWidth: Int,
        intrinsicHeight: Int,
        maxWidth: Int,
        maxHeight: Int,
    ): Size {
        require(intrinsicWidth > 0 && intrinsicHeight > 0) {
            "Image dimensions must be positive, got ${intrinsicWidth}x$intrinsicHeight"
        }
        val aspectRatio = intrinsicWidth.toFloat() / intrinsicHeight
        val width = min(maxWidth.toFloat(), maxHeight * aspectRatio)
        val height = width / aspectRatio
        return Size(max(1, width.roundToInt()), max(1, height.roundToInt()))
    }
}
