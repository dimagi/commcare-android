package org.commcare.fragments.connectMessaging

import kotlin.math.max
import kotlin.math.min

object ConnectMessageImageZoom {
    const val ZOOM_FACTOR = 2f

    data class Limits(
        val minScale: Float,
        val maxScale: Float,
        val doubleTapScale: Float,
    )

    fun limitsFor(
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        pixelsPerDp: Float,
    ): Limits {
        require(imageWidth > 0 && imageHeight > 0 && viewWidth > 0 && viewHeight > 0) {
            "Sizes must be positive, got image ${imageWidth}x$imageHeight in view ${viewWidth}x$viewHeight"
        }
        val fitScale = min(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
        val minScale = min(fitScale, pixelsPerDp)
        val maxScale = max(ZOOM_FACTOR, ZOOM_FACTOR * minScale)
        val doubleTapScale = min(maxScale, max(1f, ZOOM_FACTOR * minScale))
        return Limits(minScale, maxScale, doubleTapScale)
    }
}
