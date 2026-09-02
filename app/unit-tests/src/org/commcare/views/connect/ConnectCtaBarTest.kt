package org.commcare.views.connect

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
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
class ConnectCtaBarTest {
    // MaterialButton requires a Theme.MaterialComponents descendant; the bare application context
    // isn't one, so inflate through the app's Material theme as the hosting activity does in production.
    private fun themedContext(): Context =
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.CommonTheme,
        )

    private fun newBar() = ConnectCtaBar(themedContext())

    @Test
    fun `title and subtitle bind to text views`() {
        val bar = newBar()
        bar.titleText = "Continue"
        bar.subtitleText = "Learning modules"

        assertEquals("Continue", bar.findViewById<TextView>(R.id.cta_title_text).text.toString())
        val subtitle = bar.findViewById<TextView>(R.id.cta_subtitle_text)
        assertEquals("Learning modules", subtitle.text.toString())
        assertEquals(View.VISIBLE, subtitle.visibility)
    }

    @Test
    fun `empty subtitle is gone`() {
        val bar = newBar()
        bar.subtitleText = null
        assertEquals(View.GONE, bar.findViewById<TextView>(R.id.cta_subtitle_text).visibility)
    }

    @Test
    fun `empty title is gone`() {
        val bar = newBar()
        bar.titleText = null
        assertEquals(View.GONE, bar.findViewById<TextView>(R.id.cta_title_text).visibility)
    }

    @Test
    fun `button text binds and click listener fires`() {
        val bar = newBar()
        bar.buttonText = "Start"
        val button = bar.findViewById<MaterialButton>(R.id.cta_button)
        assertEquals("Start", button.text.toString())

        var clicked = false
        bar.setOnCtaClickListener { clicked = true }
        button.performClick()
        assertTrue(clicked)
    }

    @Test
    fun `null progress shows button and hides progress ring`() {
        val bar = newBar()
        bar.progress = null
        assertEquals(View.VISIBLE, bar.findViewById<View>(R.id.cta_button).visibility)
        assertEquals(View.GONE, bar.findViewById<View>(R.id.cta_progress_ring).visibility)
    }

    @Test
    fun `progress value shows the ring and hides the button`() {
        val bar = newBar()
        bar.progress = 25
        assertEquals(View.GONE, bar.findViewById<View>(R.id.cta_button).visibility)
        assertEquals(View.VISIBLE, bar.findViewById<View>(R.id.cta_progress_ring).visibility)
    }

    @Test
    fun `progress is announced rather than drawn on the ring`() {
        val bar = newBar()
        bar.progress = 25
        assertEquals("25%", progressDescription(bar))
    }

    @Test
    fun `progress above max clamps to 100 percent`() {
        val bar = newBar()
        bar.progress = 150
        assertEquals("100%", progressDescription(bar))
    }

    @Test
    fun `progress below min clamps to 0 percent`() {
        val bar = newBar()
        bar.progress = -5
        assertEquals("0%", progressDescription(bar))
    }

    @Test
    fun `install progress replaces the bar wording and shows the ring`() {
        val bar = newBar()
        bar.titleText = "Start Visits"
        bar.subtitleText = "Download Delivery"

        bar.showInstallProgress(40, "Downloading Delivery App")

        assertEquals("Please wait …", bar.findViewById<TextView>(R.id.cta_title_text).text.toString())
        assertEquals(
            "Downloading Delivery App",
            bar.findViewById<TextView>(R.id.cta_subtitle_text).text.toString(),
        )
        assertEquals(View.VISIBLE, bar.findViewById<View>(R.id.cta_progress_ring).visibility)
        assertEquals("40%", progressDescription(bar))
    }

    @Test
    fun `rebinding during an install updates what the bar returns to, not what it shows`() {
        val bar = newBar()
        bar.showInstallProgress(40, "Downloading Delivery App")

        bar.titleText = "Continue visits"
        bar.subtitleText = "Submit delivery forms"

        assertEquals("Please wait …", bar.findViewById<TextView>(R.id.cta_title_text).text.toString())

        bar.clearInstallProgress()

        assertEquals("Continue visits", bar.findViewById<TextView>(R.id.cta_title_text).text.toString())
        assertEquals(
            "Submit delivery forms",
            bar.findViewById<TextView>(R.id.cta_subtitle_text).text.toString(),
        )
    }

    @Test
    fun `clearing install progress restores the button`() {
        val bar = newBar()
        bar.titleText = "Start Visits"
        bar.showInstallProgress(40, "Downloading Delivery App")

        bar.clearInstallProgress()

        assertEquals("Start Visits", bar.findViewById<TextView>(R.id.cta_title_text).text.toString())
        assertEquals(View.VISIBLE, bar.findViewById<View>(R.id.cta_button).visibility)
        assertEquals(View.GONE, bar.findViewById<View>(R.id.cta_progress_ring).visibility)
    }

    @Test
    fun `an install failure restores the button and explains itself above the bar`() {
        val bar = newBar()
        bar.titleText = "Start Visits"
        bar.showInstallProgress(40, "Downloading Delivery App")

        bar.showInstallFailure("Download failed. Please try again.")

        val card = bar.findViewById<View>(R.id.cta_failure_card)
        assertEquals(View.VISIBLE, card.visibility)
        assertEquals(
            "Download failed. Please try again.",
            bar.findViewById<TextView>(R.id.success_failure_card_text).text.toString(),
        )
        assertEquals("Start Visits", bar.findViewById<TextView>(R.id.cta_title_text).text.toString())
        assertEquals(View.VISIBLE, bar.findViewById<View>(R.id.cta_button).visibility)
    }

    @Test
    fun `starting an install clears a previous failure`() {
        val bar = newBar()
        bar.showInstallFailure("Download failed. Please try again.")

        bar.showInstallProgress(0, "Downloading Delivery App")

        assertEquals(View.GONE, bar.findViewById<View>(R.id.cta_failure_card).visibility)
    }

    @Test
    fun `info message shows banner with text`() {
        val bar = newBar()
        bar.infoMessage = "Further visits will not count."
        val banner = bar.findViewById<TextView>(R.id.cta_info_banner)
        assertEquals(View.VISIBLE, banner.visibility)
        assertEquals("Further visits will not count.", banner.text.toString())
    }

    @Test
    fun `empty info message hides banner`() {
        val bar = newBar()
        bar.infoMessage = null
        assertEquals(View.GONE, bar.findViewById<View>(R.id.cta_info_banner).visibility)
    }

    @Test
    fun `attributes inflate content`() {
        val attrs =
            Robolectric
                .buildAttributeSet()
                .addAttribute(R.attr.titleText, "Continue")
                .addAttribute(R.attr.subtitleText, "Learning modules")
                .addAttribute(R.attr.buttonText, "Start")
                .addAttribute(R.attr.progress, "25")
                .build()
        val bar = ConnectCtaBar(themedContext(), attrs)

        assertEquals("Continue", bar.findViewById<TextView>(R.id.cta_title_text).text.toString())
        assertEquals("Learning modules", bar.findViewById<TextView>(R.id.cta_subtitle_text).text.toString())
        assertEquals("Start", bar.findViewById<MaterialButton>(R.id.cta_button).text.toString())
        assertEquals("25%", progressDescription(bar))
    }

    private fun progressDescription(bar: ConnectCtaBar) = bar.findViewById<View>(R.id.cta_progress_ring).contentDescription.toString()
}
