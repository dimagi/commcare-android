package org.commcare.fragments.connectMessaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectMessageMediaSizerTest {
    @Test
    fun `wide photo fills the available width`() {
        val size = fit(4000, 3000)

        assertEquals(ConnectMessageMediaSizer.Size(344, 258), size)
    }

    @Test
    fun `tall photo stops at the maximum height and narrows to keep its shape`() {
        val size = fit(3000, 4000)

        assertEquals(ConnectMessageMediaSizer.Size(271, 361), size)
    }

    @Test
    fun `small image is shown at its natural size in dp rather than stretched`() {
        val size = fit(100, 60)

        assertEquals(ConnectMessageMediaSizer.Size(200, 120), size)
    }

    @Test
    fun `tiny image is raised to the minimum size`() {
        val size = fit(16, 16)

        assertEquals(ConnectMessageMediaSizer.Size(MINIMUM_SIZE, MINIMUM_SIZE), size)
    }

    @Test
    fun `thin image is raised until its shorter side reaches the minimum`() {
        val size = fit(100, 20)

        assertEquals(ConnectMessageMediaSizer.Size(344, 69), size)
    }

    @Test
    fun `only images at least the minimum size in both dimensions open the viewer`() {
        assertTrue(ConnectMessageMediaSizer.isLargeEnoughToOpen(48, 48, PIXELS_PER_DP, MINIMUM_SIZE))
        assertFalse(ConnectMessageMediaSizer.isLargeEnoughToOpen(16, 16, PIXELS_PER_DP, MINIMUM_SIZE))
        assertFalse(ConnectMessageMediaSizer.isLargeEnoughToOpen(400, 40, PIXELS_PER_DP, MINIMUM_SIZE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `image without dimensions is rejected`() {
        fit(0, 100)
    }

    private fun fit(
        width: Int,
        height: Int,
    ) = ConnectMessageMediaSizer.fitImage(
        width,
        height,
        maxWidth = 344,
        maxHeight = 361,
        pixelsPerDp = PIXELS_PER_DP,
        minimumSize = MINIMUM_SIZE,
    )

    private companion object {
        const val PIXELS_PER_DP = 2f
        const val MINIMUM_SIZE = 96
    }
}
