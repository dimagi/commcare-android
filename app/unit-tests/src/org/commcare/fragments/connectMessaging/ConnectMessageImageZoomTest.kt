package org.commcare.fragments.connectMessaging

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectMessageImageZoomTest {
    @Test
    fun `small image opens at its natural size in dp and zooms to twice that`() {
        val limits = limitsFor(200, 100)

        assertEquals(PIXELS_PER_DP, limits.minScale, DELTA)
        assertEquals(2 * PIXELS_PER_DP, limits.maxScale, DELTA)
        assertEquals(2 * PIXELS_PER_DP, limits.doubleTapScale, DELTA)
    }

    @Test
    fun `large photo opens fitted and zooms to two screen pixels per image pixel`() {
        val limits = limitsFor(4000, 3000)

        assertEquals(0.25f, limits.minScale, DELTA)
        assertEquals(2f, limits.maxScale, DELTA)
        assertEquals(1f, limits.doubleTapScale, DELTA)
    }

    @Test
    fun `image slightly larger than the screen still doubles on double tap`() {
        val limits = limitsFor(1250, 800)

        assertEquals(0.8f, limits.minScale, DELTA)
        assertEquals(1.6f, limits.doubleTapScale, DELTA)
    }

    @Test
    fun `image whose natural size overflows the screen opens fitted and can zoom to twice that`() {
        val limits = limitsFor(800, 600)

        assertEquals(1.25f, limits.minScale, DELTA)
        assertEquals(2.5f, limits.maxScale, DELTA)
    }

    @Test
    fun `tall image is fitted by its height so it is fully visible`() {
        val limits = limitsFor(1000, 4000)

        assertEquals(0.5f, limits.minScale, DELTA)
    }

    private fun limitsFor(
        imageWidth: Int,
        imageHeight: Int,
    ) = ConnectMessageImageZoom.limitsFor(imageWidth, imageHeight, VIEW_WIDTH, VIEW_HEIGHT, PIXELS_PER_DP)

    private companion object {
        const val VIEW_WIDTH = 1000
        const val VIEW_HEIGHT = 2000
        const val PIXELS_PER_DP = 2.5f
        const val DELTA = 0.001f
    }
}
