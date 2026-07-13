package org.commcare.views.connect

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.ImageViewCompat
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
class ConnectSuccessFailureCardTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun successColor() = context.getColor(R.color.connect_green)

    private fun failureColor() = context.getColor(R.color.connect_red)

    private fun ConnectSuccessFailureCard.icon() = findViewById<ImageView>(R.id.success_failure_card_icon)

    private fun ConnectSuccessFailureCard.message() = findViewById<TextView>(R.id.success_failure_card_text)

    private fun ConnectSuccessFailureCard.close() = findViewById<ImageView>(R.id.success_failure_card_close)

    private fun ImageView.tintColor() = ImageViewCompat.getImageTintList(this)!!.defaultColor

    @Test
    fun `success mode applies success styling`() {
        val card = ConnectSuccessFailureCard(context)

        card.show(ConnectSuccessFailureCard.Mode.SUCCESS, "done")

        assertEquals(context.getColor(R.color.connect_light_green), card.cardBackgroundColor.defaultColor)
        assertEquals(successColor(), card.message().currentTextColor)
        assertEquals(successColor(), card.icon().tintColor())
        assertEquals(successColor(), card.close().tintColor())
    }

    @Test
    fun `failure mode applies failure styling`() {
        val card = ConnectSuccessFailureCard(context)

        card.show(ConnectSuccessFailureCard.Mode.FAILURE, "done")

        assertEquals(context.getColor(R.color.pale_coral), card.cardBackgroundColor.defaultColor)
        assertEquals(failureColor(), card.message().currentTextColor)
        assertEquals(failureColor(), card.icon().tintColor())
        assertEquals(failureColor(), card.close().tintColor())
    }

    @Test
    fun `card is hidden by default`() {
        val card = ConnectSuccessFailureCard(context)

        assertEquals(View.GONE, card.visibility)
    }

    @Test
    fun `honors an explicit visibility declared in xml`() {
        val attrs =
            Robolectric
                .buildAttributeSet()
                .addAttribute(android.R.attr.visibility, "visible")
                .build()

        val card = ConnectSuccessFailureCard(context, attrs)

        assertEquals(View.VISIBLE, card.visibility)
    }

    @Test
    fun `stays hidden when xml omits visibility`() {
        val attrs =
            Robolectric
                .buildAttributeSet()
                .addAttribute(android.R.attr.contentDescription, "status")
                .build()

        val card = ConnectSuccessFailureCard(context, attrs)

        assertEquals(View.GONE, card.visibility)
    }

    @Test
    fun `method show configures mode and message and makes the card visible`() {
        val card = ConnectSuccessFailureCard(context)

        card.show(ConnectSuccessFailureCard.Mode.FAILURE, "Upload failed")

        assertEquals(View.VISIBLE, card.visibility)
        assertEquals(ConnectSuccessFailureCard.Mode.FAILURE, card.mode)
        assertEquals("Upload failed", card.message().text.toString())
        assertEquals(failureColor(), card.message().currentTextColor)
    }

    @Test
    fun `method show with a string resource resolves and displays it`() {
        val card = ConnectSuccessFailureCard(context)

        card.show(ConnectSuccessFailureCard.Mode.SUCCESS, android.R.string.ok)

        assertEquals(View.VISIBLE, card.visibility)
        assertEquals(context.getString(android.R.string.ok), card.message().text.toString())
    }

    @Test
    fun `tapping close hides the card and invokes the show onDismiss handler`() {
        val card = ConnectSuccessFailureCard(context)
        var dismissed = false
        card.show(ConnectSuccessFailureCard.Mode.SUCCESS, "done") { dismissed = true }

        card.close().performClick()

        assertEquals(View.GONE, card.visibility)
        assertTrue(dismissed)
    }

    @Test
    fun `tapping close without an onDismiss handler still hides the card`() {
        val card = ConnectSuccessFailureCard(context)
        card.show(ConnectSuccessFailureCard.Mode.SUCCESS, "done")

        card.close().performClick()

        assertEquals(View.GONE, card.visibility)
    }

    @Test
    fun `message stays vertically centered and bounded between the icons`() {
        val card = ConnectSuccessFailureCard(context)
        val params = card.message().layoutParams as ConstraintLayout.LayoutParams

        assertEquals(0, params.width)
        assertEquals(ConstraintLayout.LayoutParams.PARENT_ID, params.topToTop)
        assertEquals(ConstraintLayout.LayoutParams.PARENT_ID, params.bottomToBottom)
        assertEquals(R.id.success_failure_card_icon, params.startToEnd)
        assertEquals(R.id.success_failure_card_close, params.endToStart)
    }
}
