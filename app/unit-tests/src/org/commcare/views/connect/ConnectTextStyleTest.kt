package org.commcare.views.connect

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The type scale is only useful if it survives XML inflation. An earlier version routed every
 * style through android:textAppearance, which a TextView does not pick up from a style, so every
 * view silently fell back to the framework default.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = CommCareTestApplication::class)
class ConnectTextStyleTest {
    private val context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.ConnectTheme)

    private fun inflate(layout: Int): View = LayoutInflater.from(context).inflate(layout, null)

    private fun textViews(root: View): List<TextView> =
        buildList {
            fun walk(v: View) {
                if (v is TextView) add(v)
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(root)
        }

    private fun sizeOf(dimen: Int) = context.resources.getDimension(dimen)

    @Test
    fun `each style carries its own size rather than the framework default`() {
        val expected =
            mapOf(
                R.style.TextStyle_Connect_TitleXl to R.dimen.connect_text_headline,
                R.style.TextStyle_Connect_TitleM to R.dimen.connect_text_title,
                R.style.TextStyle_Connect_TitleS to R.dimen.connect_text_body,
                R.style.TextStyle_Connect_LabelM to R.dimen.connect_text_caption,
                R.style.TextStyle_Connect_LabelS to R.dimen.connect_text_label,
                R.style.TextStyle_Connect_BodyLg to R.dimen.connect_text_title,
                R.style.TextStyle_Connect_BodyM to R.dimen.connect_text_body,
                R.style.TextStyle_Connect_BodyS to R.dimen.connect_text_caption,
                R.style.TextStyle_Connect_BodyXs to R.dimen.connect_text_label,
            )
        expected.forEach { (style, dimen) ->
            val attrs = context.obtainStyledAttributes(style, intArrayOf(android.R.attr.textSize))
            assertEquals(sizeOf(dimen), attrs.getDimension(0, -1f), 0.01f)
            attrs.recycle()
        }
    }

    @Test
    fun `a styled view inflated from XML takes the style's size, not the default`() {
        val root = inflate(R.layout.fragment_connect_job_intro)
        val title = root.findViewById<TextView>(R.id.tv_job_title)
        val description = root.findViewById<TextView>(R.id.tv_job_description)

        assertEquals(sizeOf(R.dimen.connect_text_headline), title.textSize, 0.01f)
        assertEquals(sizeOf(R.dimen.connect_text_body), description.textSize, 0.01f)
    }

    @Test
    fun `the button styles stay on the type scale`() {
        mapOf(
            R.style.Widget_Connect_CtaBarButton to R.dimen.connect_text_caption,
            R.style.Widget_Connect_ProgressCardButton to R.dimen.connect_text_body,
        ).forEach { (style, dimen) ->
            val attrs = context.obtainStyledAttributes(style, intArrayOf(android.R.attr.textSize))
            assertEquals(sizeOf(dimen), attrs.getDimension(0, -1f), 0.01f)
            attrs.recycle()
        }
    }

    @Test
    fun `a screen does not render every label at one size`() {
        val sizes = textViews(inflate(R.layout.fragment_connect_job_intro)).map { it.textSize }
        assertTrue("expected a range of sizes, got $sizes", sizes.distinct().size > 1)
    }
}
