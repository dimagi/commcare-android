package org.commcare.views.connect

import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.commcare.views.connect.ConnectProgressCard.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectProgressCardTest {
    private fun newCard() =
        ConnectProgressCard(
            ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(),
                R.style.CommonTheme,
            ),
        )

    @Test
    fun `title shows and hides`() {
        val card = newCard()
        val title = card.findViewById<TextView>(R.id.progress_card_title)

        assertEquals(View.GONE, title.visibility)

        card.bind(State(title = "Delivery Progress"))
        assertEquals(View.VISIBLE, title.visibility)
        assertEquals("Delivery Progress", title.text.toString())

        card.bind(State())
        assertEquals(View.GONE, title.visibility)
    }

    @Test
    fun `semi-circle is shown when present and values propagate`() {
        val card = newCard()
        val semi = card.findViewById<SemiCircleProgressBar>(R.id.progress_card_semi_circle)

        assertEquals(View.GONE, semi.visibility)

        card.bind(
            State(
                semiCircle = State.SemiCircle(current = 17, max = 40, description = "total visits completed"),
            ),
        )

        assertEquals(View.VISIBLE, semi.visibility)
        assertEquals(40, semi.max)
        assertEquals(17, semi.current)
        assertEquals("total visits completed", semi.descriptionText.toString())

        card.bind(State())
        assertEquals(View.GONE, semi.visibility)
    }

    @Test
    fun `linear progress label shows and hides`() {
        val card = newCard()
        val label = card.findViewById<TextView>(R.id.progress_card_bar_label)

        assertEquals(View.GONE, label.visibility)
        card.bind(State(linearProgress = State.LinearProgress(label = "Learning Progress")))
        assertEquals(View.VISIBLE, label.visibility)
        assertEquals("Learning Progress", label.text.toString())

        card.bind(State())
        assertEquals(View.GONE, label.visibility)
    }

    @Test
    fun `linear current and max drive the derived count label`() {
        val card = newCard()
        val count = card.findViewById<TextView>(R.id.progress_card_bar_count)

        card.bind(State(linearProgress = State.LinearProgress(current = 2, max = 5)))

        assertEquals(View.VISIBLE, count.visibility)
        assertEquals("2 of 5", count.text.toString())
    }

    @Test
    fun `count is hidden when max is not positive`() {
        val card = newCard()
        val count = card.findViewById<TextView>(R.id.progress_card_bar_count)

        card.bind(State(linearProgress = State.LinearProgress(current = 3, max = 0)))

        assertEquals(View.GONE, count.visibility)
    }

    @Test
    fun `linear current is clamped to max in the count label`() {
        val card = newCard()
        val count = card.findViewById<TextView>(R.id.progress_card_bar_count)

        card.bind(State(linearProgress = State.LinearProgress(current = 999, max = 5)))

        assertEquals("5 of 5", count.text.toString())
    }

    @Test
    fun `linear caption shows and hides`() {
        val card = newCard()
        val caption = card.findViewById<TextView>(R.id.progress_card_bar_caption)

        assertEquals(View.GONE, caption.visibility)
        card.bind(State(linearProgress = State.LinearProgress(caption = "You're almost there!")))
        assertEquals(View.VISIBLE, caption.visibility)
        assertEquals("You're almost there!", caption.text.toString())

        card.bind(State(linearProgress = State.LinearProgress(caption = "")))
        assertEquals(View.GONE, caption.visibility)
    }

    @Test
    fun `bar row hugs the card padding only when nothing is shown above it`() {
        val card = newCard()
        val row = card.findViewById<View>(R.id.progress_card_bar_row)
        val gap = card.resources.getDimensionPixelSize(R.dimen.connect_space_lg)

        fun topMargin() = (row.layoutParams as ViewGroup.MarginLayoutParams).topMargin

        card.bind(State(linearProgress = State.LinearProgress(label = "Modules Completed")))
        assertEquals(0, topMargin())

        card.bind(State(title = "Delivery Progress"))
        assertEquals(gap, topMargin())

        card.bind(State(semiCircle = State.SemiCircle()))
        assertEquals(gap, topMargin())

        card.bind(State())
        assertEquals(0, topMargin())
    }

    @Test
    fun `contentEnabled leaves the primary text at full strength either way`() {
        val card = newCard()
        val title = card.findViewById<TextView>(R.id.progress_card_title)
        val label = card.findViewById<TextView>(R.id.progress_card_bar_label)

        val primary = ContextCompat.getColor(card.context, R.color.connect_text_color)

        val content =
            State(
                title = "Delivery Progress",
                linearProgress = State.LinearProgress(label = "Learning Progress"),
            )

        card.bind(content.copy(contentEnabled = false))
        assertEquals(primary, title.currentTextColor)
        assertEquals(primary, label.currentTextColor)

        card.bind(content.copy(contentEnabled = true))
        assertEquals(primary, title.currentTextColor)
        assertEquals(primary, label.currentTextColor)
    }

    @Test
    fun `contentEnabled recolors caption and semi-circle`() {
        val card = newCard()
        val caption = card.findViewById<TextView>(R.id.progress_card_bar_caption)
        val semi = card.findViewById<SemiCircleProgressBar>(R.id.progress_card_semi_circle)

        val grey = ContextCompat.getColor(card.context, R.color.connect_grey)
        val accent = ContextCompat.getColor(card.context, R.color.connect_dark_blue_color)
        val primary = ContextCompat.getColor(card.context, R.color.connect_text_color)

        val content =
            State(
                linearProgress = State.LinearProgress(caption = "Almost there"),
                semiCircle = State.SemiCircle(),
            )

        card.bind(content.copy(contentEnabled = false))
        assertEquals(grey, caption.currentTextColor)
        assertEquals(grey, semi.progressColor)
        assertEquals(grey, semi.valueTextColor)
        assertEquals(primary, semi.descriptionTextColor)

        card.bind(content.copy(contentEnabled = true))
        assertEquals(accent, caption.currentTextColor)
        assertEquals(accent, semi.progressColor)
        assertEquals(accent, semi.valueTextColor)
        assertEquals(primary, semi.descriptionTextColor)
    }

    @Test
    fun `info shows and hides the info box`() {
        val card = newCard()
        val box = card.findViewById<View>(R.id.progress_card_info_message)
        val text = card.findViewById<TextView>(R.id.progress_card_info_text)

        assertEquals(View.GONE, box.visibility)
        card.bind(State(info = State.Info(message = "This opportunity is not yet available.")))
        assertEquals(View.VISIBLE, box.visibility)
        assertEquals("This opportunity is not yet available.", text.text.toString())

        card.bind(State())
        assertEquals(View.GONE, box.visibility)
    }

    @Test
    fun `info CTA shows and the callback fires on click`() {
        val card = newCard()
        val cta = card.findViewById<View>(R.id.progress_card_info_cta)

        assertEquals(View.GONE, cta.visibility)

        var clicked = false
        card.bind(
            State(
                info = State.Info(message = "Info", ctaText = "Learn more", onCtaClick = { clicked = true }),
            ),
        )

        assertEquals(View.VISIBLE, cta.visibility)
        cta.performClick()
        assertTrue(clicked)
    }

    @Test
    fun `info defaults to the blue appearance with no icon`() {
        val card = newCard()
        val box = card.findViewById<CardView>(R.id.progress_card_info_message)
        val text = card.findViewById<TextView>(R.id.progress_card_info_text)
        val icon = card.findViewById<ImageView>(R.id.progress_card_info_icon)

        card.bind(State(info = State.Info(message = "Complete assigned tasks")))

        assertEquals(
            ContextCompat.getColor(card.context, R.color.connect_dark_blue_color),
            box.cardBackgroundColor.defaultColor,
        )
        assertEquals(ContextCompat.getColor(card.context, R.color.white), text.currentTextColor)
        assertEquals(View.GONE, icon.visibility)
    }

    @Test
    fun `info warning appearance recolors the banner and reveals the icon`() {
        val card = newCard()
        val box = card.findViewById<CardView>(R.id.progress_card_info_message)
        val text = card.findViewById<TextView>(R.id.progress_card_info_text)
        val icon = card.findViewById<ImageView>(R.id.progress_card_info_icon)

        val blue = ContextCompat.getColor(card.context, R.color.connect_dark_blue_color)

        card.bind(
            State(
                info =
                    State.Info(
                        message = "The job has ended.",
                        appearance = State.Info.Appearance.WARNING,
                    ),
            ),
        )

        assertEquals(
            ContextCompat.getColor(card.context, R.color.connect_light_grey),
            box.cardBackgroundColor.defaultColor,
        )
        assertEquals(blue, text.currentTextColor)
        assertEquals(View.VISIBLE, icon.visibility)
        assertEquals(blue, ImageViewCompat.getImageTintList(icon)?.defaultColor)
    }

    @Test
    fun `rebinding switches the banner back out of the warning appearance`() {
        val card = newCard()
        val icon = card.findViewById<ImageView>(R.id.progress_card_info_icon)

        card.bind(State(info = State.Info(message = "Ended", appearance = State.Info.Appearance.WARNING)))
        assertEquals(View.VISIBLE, icon.visibility)

        card.bind(State(info = State.Info(message = "Ended")))
        assertEquals(View.GONE, icon.visibility)
    }

    @Test
    fun `the info banner sits behind the main card`() {
        val card = newCard()
        val box = card.findViewById<CardView>(R.id.progress_card_info_message)
        val main = card.findViewById<CardView>(R.id.progress_card_main)

        assertTrue(box.z < main.z)
    }

    @Test
    fun `attributes inflate into state`() {
        val attrs =
            Robolectric
                .buildAttributeSet()
                .addAttribute(R.attr.titleText, "Delivery Progress")
                .addAttribute(R.attr.linearProgressLabel, "Learning Progress")
                .addAttribute(R.attr.linearProgressMax, "5")
                .addAttribute(R.attr.linearProgressCurrent, "2")
                .addAttribute(R.attr.linearProgressCaption, "cap")
                .addAttribute(R.attr.semiCircleVisible, "true")
                .addAttribute(R.attr.semiCircleMax, "40")
                .addAttribute(R.attr.semiCircleCurrent, "17")
                .addAttribute(R.attr.semiCircleDescription, "visits")
                .addAttribute(R.attr.infoMessage, "Not available")
                .addAttribute(R.attr.infoCtaText, "Go")
                .build()

        val card =
            ConnectProgressCard(
                ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.CommonTheme),
                attrs,
            )

        val state = card.state
        assertEquals("Delivery Progress", state.title.toString())
        assertEquals("Learning Progress", state.linearProgress?.label.toString())
        assertEquals(5, state.linearProgress?.max)
        assertEquals(2, state.linearProgress?.current)
        assertEquals("cap", state.linearProgress?.caption.toString())
        assertEquals(40, state.semiCircle?.max)
        assertEquals(17, state.semiCircle?.current)
        assertEquals("visits", state.semiCircle?.description.toString())
        assertEquals("Not available", state.info?.message.toString())
        assertEquals("Go", state.info?.ctaText.toString())
        assertEquals(
            "2 of 5",
            card.findViewById<TextView>(R.id.progress_card_bar_count).text.toString(),
        )
    }

    @Test
    fun `color attributes override the default style`() {
        val overrideAccent = 0xFFFF00FF.toInt()
        val attrs =
            Robolectric
                .buildAttributeSet()
                .addAttribute(R.attr.linearProgressCaption, "cap")
                .addAttribute(R.attr.contentAccentColor, "#FFFF00FF")
                .build()

        val card =
            ConnectProgressCard(
                ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.CommonTheme),
                attrs,
            )

        val caption = card.findViewById<TextView>(R.id.progress_card_bar_caption)

        assertEquals(overrideAccent, caption.currentTextColor)
    }
}
