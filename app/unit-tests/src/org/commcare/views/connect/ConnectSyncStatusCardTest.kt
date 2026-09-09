package org.commcare.views.connect

import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectSyncStatusCardTest {
    private fun newCard() =
        ConnectSyncStatusCard(
            ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(),
                R.style.CommonTheme,
            ),
        )

    @Test
    fun `statusText binds to the main line`() {
        val card = newCard()
        val text = card.findViewById<TextView>(R.id.sync_card_text)

        card.bind(ConnectSyncStatusCard.State(statusText = "Everything is synced"))
        assertEquals("Everything is synced", text.text.toString())
    }

    @Test
    fun `statusSubtext shows and hides`() {
        val card = newCard()
        val subtext = card.findViewById<TextView>(R.id.sync_card_subtext)

        assertEquals(View.GONE, subtext.visibility)
        card.bind(ConnectSyncStatusCard.State(statusSubtext = "Synced just now"))
        assertEquals(View.VISIBLE, subtext.visibility)
        assertEquals("Synced just now", subtext.text.toString())

        card.bind(ConnectSyncStatusCard.State(statusSubtext = null))
        assertEquals(View.GONE, subtext.visibility)
    }

    @Test
    fun `default appearance is the ok badge`() {
        val card = newCard()
        val icon = card.findViewById<ImageView>(R.id.sync_card_icon)

        assertEquals(R.drawable.check_update, Shadows.shadowOf(icon.drawable).createdFromResId)
        assertEquals(
            ContextCompat.getColor(card.context, R.color.connect_light_green),
            icon.backgroundTintList?.defaultColor,
        )
        assertEquals(
            ContextCompat.getColor(card.context, R.color.connect_light_grey),
            card.strokeColorStateList?.defaultColor,
        )
    }

    @Test
    fun `warning switches the badge icon and colour`() {
        val card = newCard()
        val icon = card.findViewById<ImageView>(R.id.sync_card_icon)

        card.bind(ConnectSyncStatusCard.State(warning = true))

        assertEquals(R.drawable.ic_connect_directory_sync, Shadows.shadowOf(icon.drawable).createdFromResId)
        assertEquals(
            ContextCompat.getColor(card.context, R.color.pale_buttery_cream),
            icon.backgroundTintList?.defaultColor,
        )
    }

    @Test
    fun `warning switches the card outline`() {
        val card = newCard()

        card.bind(ConnectSyncStatusCard.State(warning = true))

        assertEquals(
            ContextCompat.getColor(card.context, R.color.burnt_amber),
            card.strokeColorStateList?.defaultColor,
        )
    }

    @Test
    fun `the badge trails the status text`() {
        val card = newCard()
        val row = card.findViewById<ImageView>(R.id.sync_card_icon).parent as ViewGroup

        assertEquals(row.childCount - 1, row.indexOfChild(card.findViewById(R.id.sync_card_icon)))
    }

    @Test
    fun `onCardClick makes the card tappable`() {
        val card = newCard()
        var clicks = 0

        assertEquals(false, card.isClickable)

        card.onCardClick = { clicks++ }
        assertEquals(true, card.isClickable)
        assertEquals(true, card.isFocusable)

        card.performClick()
        assertEquals(1, clicks)

        card.onCardClick = null
        assertEquals(false, card.isClickable)
        assertEquals(false, card.isFocusable)

        card.performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `color attributes override the default style`() {
        val overrideWarningBadge = 0xFF00FF00.toInt()
        val attrs =
            Robolectric
                .buildAttributeSet()
                .addAttribute(R.attr.warning, "true")
                .addAttribute(R.attr.syncWarningBadgeColor, "#FF00FF00")
                .build()

        val card =
            ConnectSyncStatusCard(
                ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.CommonTheme),
                attrs,
            )

        val icon = card.findViewById<ImageView>(R.id.sync_card_icon)
        assertEquals(overrideWarningBadge, icon.backgroundTintList?.defaultColor)
    }

    @Test
    fun `attributes inflate into state`() {
        val attrs =
            Robolectric
                .buildAttributeSet()
                .addAttribute(R.attr.statusText, "Everything is synced")
                .addAttribute(R.attr.statusSubtext, "Synced just now")
                .addAttribute(R.attr.warning, "false")
                .build()

        val card =
            ConnectSyncStatusCard(
                ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.CommonTheme),
                attrs,
            )

        assertEquals("Everything is synced", card.state.statusText.toString())
        assertEquals("Synced just now", card.state.statusSubtext.toString())
        assertEquals(false, card.state.warning)
    }
}
