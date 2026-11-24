package org.commcare.fragments.connectMessaging

import kotlin.math.roundToInt

/**
 * Sizes a card in the chat (spec section 7). The card is as wide as the bubble, up to a cap for
 * tablets, and as tall as its page reports, up to a share of the window. With zoom off and the
 * layout width taken from the view, one CSS pixel is one dp.
 */
object ConnectMessageCardSizer {
    const val MAX_WIDTH_DP = 480
    const val MIN_HEIGHT_DP = 48
    const val MAX_REPORTED_HEIGHT_DP = 4000

    // A starting value, to tune on device
    const val MAX_HEIGHT_FRACTION_OF_WINDOW = 0.6f

    @JvmStatic
    fun widthPx(
        maxMediaWidthPx: Int,
        density: Float,
    ): Int = minOf(maxMediaWidthPx, (MAX_WIDTH_DP * density).roundToInt()).coerceAtLeast(1)

    /** The page's height in pixels for a height the page reported, or null to ignore the report. */
    @JvmStatic
    fun contentHeightPx(
        reportedCssPixels: Int,
        density: Float,
    ): Int? {
        if (reportedCssPixels < 0 || reportedCssPixels > MAX_REPORTED_HEIGHT_DP) {
            return null
        }
        return (maxOf(reportedCssPixels, MIN_HEIGHT_DP) * density).roundToInt()
    }

    @JvmStatic
    fun maxInlineHeightPx(windowHeightPx: Int): Int = (windowHeightPx * MAX_HEIGHT_FRACTION_OF_WINDOW).toInt().coerceAtLeast(1)

    @JvmStatic
    fun inlineHeightPx(
        contentHeightPx: Int,
        windowHeightPx: Int,
    ): Int = minOf(contentHeightPx, maxInlineHeightPx(windowHeightPx))
}
