package org.commcare.fragments.connectMessaging

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectMessageMediaSizerTest {
    @Test
    fun `wide image fills the available width`() {
        val size = ConnectMessageMediaSizer.fitImage(4000, 3000, maxWidth = 344, maxHeight = 361)

        assertEquals(ConnectMessageMediaSizer.Size(344, 258), size)
    }

    @Test
    fun `tall image stops at the maximum height and narrows to keep its shape`() {
        val size = ConnectMessageMediaSizer.fitImage(3000, 4000, maxWidth = 344, maxHeight = 361)

        assertEquals(ConnectMessageMediaSizer.Size(271, 361), size)
    }

    @Test
    fun `small image is scaled up to the available width`() {
        val size = ConnectMessageMediaSizer.fitImage(100, 100, maxWidth = 344, maxHeight = 361)

        assertEquals(ConnectMessageMediaSizer.Size(344, 344), size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `image without dimensions is rejected`() {
        ConnectMessageMediaSizer.fitImage(0, 100, maxWidth = 344, maxHeight = 361)
    }
}
