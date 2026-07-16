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
    fun `null progress shows button and hides progress cluster`() {
        val bar = newBar()
        bar.progress = null
        assertEquals(View.VISIBLE, bar.findViewById<View>(R.id.cta_button).visibility)
        assertEquals(View.GONE, bar.findViewById<View>(R.id.cta_progress_cluster).visibility)
    }

    @Test
    fun `progress value shows cluster with percent and hides button`() {
        val bar = newBar()
        bar.progress = 25
        assertEquals(View.GONE, bar.findViewById<View>(R.id.cta_button).visibility)
        assertEquals(View.VISIBLE, bar.findViewById<View>(R.id.cta_progress_cluster).visibility)
        assertEquals("25%", bar.findViewById<TextView>(R.id.cta_progress_text).text.toString())
    }

    @Test
    fun `progress above max clamps to 100 percent`() {
        val bar = newBar()
        bar.progress = 150
        assertEquals("100%", bar.findViewById<TextView>(R.id.cta_progress_text).text.toString())
    }

    @Test
    fun `progress below min clamps to 0 percent`() {
        val bar = newBar()
        bar.progress = -5
        assertEquals("0%", bar.findViewById<TextView>(R.id.cta_progress_text).text.toString())
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
        assertEquals("25%", bar.findViewById<TextView>(R.id.cta_progress_text).text.toString())
    }
}
