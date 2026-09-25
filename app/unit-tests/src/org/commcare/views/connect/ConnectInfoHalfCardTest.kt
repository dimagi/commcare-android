package org.commcare.views.connect

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectInfoHalfCardTest {
    private fun newCard() =
        ConnectInfoHalfCard(
            ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(),
                R.style.CommonTheme,
            ),
        )

    private fun ConnectInfoHalfCard.measuredHeightAt(width: Int): Int {
        measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return measuredHeight
    }

    @Test
    fun `an absent title or subtitle keeps its line rather than collapsing`() {
        val card = newCard()
        val title = card.findViewById<TextView>(R.id.info_card_title_text)
        val subtitle = card.findViewById<TextView>(R.id.info_card_subtitle_text)

        card.titleText = null
        card.subtitleText = null

        assertEquals(View.INVISIBLE, title.visibility)
        assertEquals(View.INVISIBLE, subtitle.visibility)
    }

    @Test
    fun `an empty subtitle is treated as absent`() {
        val card = newCard()
        val subtitle = card.findViewById<TextView>(R.id.info_card_subtitle_text)

        card.subtitleText = ""

        assertEquals(View.INVISIBLE, subtitle.visibility)
    }

    @Test
    fun `populated title and subtitle are visible`() {
        val card = newCard()
        val title = card.findViewById<TextView>(R.id.info_card_title_text)
        val subtitle = card.findViewById<TextView>(R.id.info_card_subtitle_text)

        card.titleText = "Primary Visits"
        card.subtitleText = "100 each"

        assertEquals(View.VISIBLE, title.visibility)
        assertEquals(View.VISIBLE, subtitle.visibility)
        assertEquals("Primary Visits", title.text.toString())
        assertEquals("100 each", subtitle.text.toString())
    }

    @Test
    fun `a card with no subtitle is the same height as one with a subtitle`() {
        val width = 400

        val withSubtitle =
            newCard().apply {
                valueText = "15"
                titleText = "Primary Visits"
                subtitleText = "100 each"
            }
        val withoutSubtitle =
            newCard().apply {
                valueText = "1700"
                titleText = "Total earnings"
                subtitleText = null
            }

        val populatedHeight = withSubtitle.measuredHeightAt(width)
        assertTrue("card should measure to a real height", populatedHeight > 0)
        assertEquals(populatedHeight, withoutSubtitle.measuredHeightAt(width))
    }

    @Test
    fun `the card claims no shadow padding, so equal margins read as equal gaps`() {
        val card = newCard()
        card.measuredHeightAt(400)

        assertEquals(0, card.paddingLeft)
        assertEquals(0, card.paddingTop)
        assertEquals(0, card.paddingRight)
        assertEquals(0, card.paddingBottom)
    }

    @Test
    fun `contentEnabled false grays the value and true restores the accent`() {
        val card = newCard()
        val value = card.findViewById<TextView>(R.id.info_card_value_text)

        card.contentEnabled = false
        assertEquals(
            ContextCompat.getColor(card.context, R.color.connect_dark_grey),
            value.currentTextColor,
        )

        card.contentEnabled = true
        assertEquals(
            ContextCompat.getColor(card.context, R.color.connect_dark_blue_color),
            value.currentTextColor,
        )
    }

    @Test
    fun `contentEnabled defaults to true`() {
        val card = newCard()

        assertEquals(true, card.contentEnabled)
        assertEquals(
            ContextCompat.getColor(card.context, R.color.connect_dark_blue_color),
            card.findViewById<TextView>(R.id.info_card_value_text).currentTextColor,
        )
    }

    @Test
    fun `attributes inflate into the card`() {
        val attrs =
            Robolectric
                .buildAttributeSet()
                .addAttribute(R.attr.valueText, "15")
                .addAttribute(R.attr.titleText, "Primary Visits")
                .addAttribute(R.attr.subtitleText, "100 each")
                .addAttribute(R.attr.contentEnabled, "false")
                .build()

        val card =
            ConnectInfoHalfCard(
                ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.CommonTheme),
                attrs,
            )

        assertEquals("15", card.valueText.toString())
        assertEquals("Primary Visits", card.titleText.toString())
        assertEquals("100 each", card.subtitleText.toString())
        assertEquals(false, card.contentEnabled)
        assertEquals(
            ContextCompat.getColor(card.context, R.color.connect_dark_grey),
            card.findViewById<TextView>(R.id.info_card_value_text).currentTextColor,
        )
    }
}
