package org.commcare.views.connect

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests the atomic bind contract of [ConnectTaskCard]: whatever the card was showing before, one
 * [ConnectTaskCard.bind] leaves it showing exactly the new state and nothing of the old one.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectTaskCardTest {
    private fun newCard() =
        ConnectTaskCard(
            ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(),
                R.style.ConnectTheme,
            ),
        )

    private fun ConnectTaskCard.title() = findViewById<TextView>(R.id.task_card_title)

    private fun ConnectTaskCard.expiry() = findViewById<TextView>(R.id.task_card_expiry)

    private fun ConnectTaskCard.icon() = findViewById<ImageView>(R.id.task_card_icon)

    private fun ConnectTaskCard.chevron() = findViewById<ImageView>(R.id.task_card_chevron)

    private fun ConnectTaskCard.backgroundColor() = cardBackgroundColor.defaultColor

    private fun color(colorRes: Int) = ContextCompat.getColor(ApplicationProvider.getApplicationContext(), colorRes)

    @Test
    fun `an unbound card paints itself rather than waiting for a first bind`() {
        val card = newCard()

        assertEquals(color(R.color.cool_gray_50), card.backgroundColor())
        assertEquals(View.GONE, card.icon().visibility)
        assertEquals(View.GONE, card.expiry().visibility)
        assertFalse(card.isClickable)
    }

    @Test
    fun `a bound card shows its title, expiry and icon`() {
        val card = newCard()

        card.bind(
            ConnectTaskCard.State(
                title = "Nutrition Coaching Check-in",
                iconRes = R.drawable.ic_connect_chat_bubble_outline,
                expiryLabel = "Expires on 24 July 2027",
            ),
        )

        assertEquals("Nutrition Coaching Check-in", card.title().text.toString())
        assertEquals("Expires on 24 July 2027", card.expiry().text.toString())
        assertEquals(View.VISIBLE, card.expiry().visibility)
        assertEquals(View.VISIBLE, card.icon().visibility)
    }

    @Test
    fun `the plain variant takes its colours from the surface roles`() {
        val card = newCard()

        card.bind(ConnectTaskCard.State(title = "Task", iconRes = R.drawable.ic_connect_local_library))

        assertEquals(color(R.color.cool_gray_50), card.backgroundColor())
        assertEquals(color(R.color.cool_gray_900), card.title().currentTextColor)
        assertEquals(color(R.color.connect_light_indigo), card.icon().backgroundTintList!!.defaultColor)
        assertEquals(color(R.color.neon_blue), card.icon().imageTintList!!.defaultColor)
        assertEquals(color(R.color.cool_gray_900), card.chevron().imageTintList!!.defaultColor)
    }

    @Test
    fun `the highlighted variant inverts onto the primary colour`() {
        val card = newCard()

        card.bind(
            ConnectTaskCard.State(
                title = "Task",
                iconRes = R.drawable.ic_connect_local_library,
                expiryLabel = "Expires on 24 July 2027",
                highlighted = true,
            ),
        )

        assertEquals(color(R.color.neon_blue), card.backgroundColor())
        assertEquals(color(R.color.white), card.title().currentTextColor)
        assertEquals(color(R.color.white), card.expiry().currentTextColor)
        assertEquals(color(R.color.cc_brand_color), card.icon().backgroundTintList!!.defaultColor)
        assertEquals(color(R.color.white), card.icon().imageTintList!!.defaultColor)
        assertEquals(color(R.color.white), card.chevron().imageTintList!!.defaultColor)
    }

    @Test
    fun `rebinding from highlighted to plain restores every colour`() {
        val card = newCard()
        val highlighted =
            ConnectTaskCard.State(
                title = "Task",
                iconRes = R.drawable.ic_connect_local_library,
                highlighted = true,
            )

        card.bind(highlighted)
        card.bind(ConnectTaskCard.State(title = "Task", iconRes = R.drawable.ic_connect_local_library))

        assertEquals(color(R.color.cool_gray_50), card.backgroundColor())
        assertEquals(color(R.color.cool_gray_900), card.title().currentTextColor)
        assertEquals(color(R.color.connect_light_indigo), card.icon().backgroundTintList!!.defaultColor)
        assertEquals(color(R.color.neon_blue), card.icon().imageTintList!!.defaultColor)
    }

    @Test
    fun `rebinding without an expiry hides the line again`() {
        val card = newCard()

        card.bind(ConnectTaskCard.State(title = "Task", expiryLabel = "Expires on 24 July 2027"))
        card.bind(ConnectTaskCard.State(title = "Task"))

        assertEquals(View.GONE, card.expiry().visibility)
    }

    @Test
    fun `rebinding without an icon hides the circle again`() {
        val card = newCard()

        card.bind(ConnectTaskCard.State(title = "Task", iconRes = R.drawable.ic_connect_local_library))
        card.bind(ConnectTaskCard.State(title = "Task"))

        assertEquals(View.GONE, card.icon().visibility)
    }

    @Test
    fun `a card with a click listener is clickable and shows touch feedback`() {
        val card = newCard()
        var clicks = 0

        card.bind(ConnectTaskCard.State(title = "Task", onClick = { clicks++ }))
        card.performClick()

        assertEquals(1, clicks)
        assertTrue(card.isClickable)
        assertTrue(card.isFocusable)
        assertNotNull("a clickable card carries a ripple foreground", card.foreground)
    }

    @Test
    fun `rebinding without a click listener leaves the card inert`() {
        val card = newCard()
        var clicks = 0

        card.bind(ConnectTaskCard.State(title = "Task", onClick = { clicks++ }))
        card.bind(ConnectTaskCard.State(title = "Task"))
        card.performClick()

        assertEquals(0, clicks)
        assertFalse(card.isClickable)
        assertFalse(card.isFocusable)
        assertNull(card.foreground)
    }

    @Test
    fun `the card reads out its task name before its expiry`() {
        val card = newCard()

        card.bind(
            ConnectTaskCard.State(
                title = "Nutrition Coaching Check-in",
                expiryLabel = "Expires on 24 July 2027",
            ),
        )

        assertEquals(
            "Nutrition Coaching Check-in, Expires on 24 July 2027",
            card.contentDescription.toString(),
        )
    }

    @Test
    fun `the state the card reports is the state last bound`() {
        val card = newCard()
        val state = ConnectTaskCard.State(title = "Task", highlighted = true)

        card.bind(state)

        assertEquals(state, card.state)
    }
}
