package org.commcare.fragments.connectMessaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectMessageCardSizerTest {
    @Test
    fun `a card is as wide as the bubble on a phone`() {
        assertEquals(840, ConnectMessageCardSizer.widthPx(840, DENSITY))
    }

    @Test
    fun `a card stops at 480dp on a wide screen`() {
        assertEquals(1260, ConnectMessageCardSizer.widthPx(2000, DENSITY))
    }

    @Test
    fun `a reported height becomes pixels, with a minimum`() {
        assertEquals(1050, ConnectMessageCardSizer.contentHeightPx(400, DENSITY))
        assertEquals(126, ConnectMessageCardSizer.contentHeightPx(0, DENSITY))
    }

    @Test
    fun `implausible heights are ignored`() {
        assertNull(ConnectMessageCardSizer.contentHeightPx(-1, DENSITY))
        assertNull(ConnectMessageCardSizer.contentHeightPx(4001, DENSITY))
    }

    @Test
    fun `inline height stops at 60 percent of the window`() {
        assertEquals(500, ConnectMessageCardSizer.inlineHeightPx(500, 2000))
        assertEquals(1200, ConnectMessageCardSizer.inlineHeightPx(5000, 2000))
    }

    private companion object {
        const val DENSITY = 2.625f
    }
}
