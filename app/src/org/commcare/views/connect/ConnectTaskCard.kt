package org.commcare.views.connect

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectTaskCardBinding
import org.commcare.views.extensions.bindOptional
import org.commcare.views.extensions.themeColor
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

/**
 * Reusable Connect card for a single actionable task: a circled icon, an optional expiry line, the
 * task name and a chevron.
 *
 * The card is configured atomically through [bind]. A [State] with [State.highlighted] set inverts
 * the card onto the primary colour, marking it as the screen's primary action.
 *
 * Built in code rather than declared in a layout, so it takes no attribute set: every colour it
 * paints comes from a role on [org.commcare.dalvik.R.style.ConnectTheme], and a host theme without
 * those roles is a programming error rather than a card that renders blank.
 */
class ConnectTaskCard(
    context: Context,
) : CardView(context) {
    private val binding =
        ViewConnectTaskCardBinding.inflate(LayoutInflater.from(context), this, true)

    /** The state currently bound to the card. */
    var state: State = State()
        private set

    /**
     * Complete description of what the card renders. An absent [expiryLabel] hides that line, and an
     * absent [onClick] leaves the card inert.
     */
    class State(
        val title: CharSequence = "",
        @DrawableRes val iconRes: Int = 0,
        val expiryLabel: CharSequence? = null,
        val highlighted: Boolean = false,
        val onClick: (() -> Unit)? = null,
    )

    init {
        radius = resources.getDimension(R.dimen.connect_radius_sm)
        // Compat padding reserves room around the shadow, which would break the even gaps the list
        // sets between cards; the list opts out of clipping its children instead.
        cardElevation = resources.getDimension(R.dimen.connect_card_elevation_low)
        useCompatPadding = false
        bind(state)
    }

    fun bind(state: State) {
        this.state = state

        binding.taskCardTitle.text = state.title
        binding.taskCardExpiry.bindOptional(state.expiryLabel)
        binding.taskCardIcon.setImageResource(state.iconRes)
        binding.taskCardIcon.isVisible = state.iconRes != 0
        // Read out title first: laid out for the eye, the expiry sits above the name it belongs to.
        contentDescription = listOfNotNull(state.title, state.expiryLabel).joinToString(", ")

        applyAppearance(state.highlighted)
        applyClickListener(state.onClick, state.highlighted)
    }

    /**
     * The highlighted card sits on the primary colour, so every foreground role flips to its
     * on-primary counterpart.
     */
    private fun applyAppearance(highlighted: Boolean) {
        val onCardColor =
            themeColor(if (highlighted) MaterialR.attr.colorOnPrimary else R.attr.connectOnSurfaceEmphasis)

        setCardBackgroundColor(
            themeColor(if (highlighted) AppCompatR.attr.colorPrimary else R.attr.connectSurfaceContainerLow),
        )
        binding.taskCardTitle.setTextColor(onCardColor)
        binding.taskCardExpiry.setTextColor(
            themeColor(if (highlighted) MaterialR.attr.colorOnPrimary else R.attr.connectOnSurfaceVariant),
        )
        binding.taskCardChevron.tint(onCardColor)
        binding.taskCardIcon.backgroundTintList =
            ColorStateList.valueOf(
                themeColor(if (highlighted) R.attr.connectInversePrimary else R.attr.connectPrimaryContainer),
            )
        binding.taskCardIcon.tint(
            themeColor(if (highlighted) MaterialR.attr.colorOnPrimary else R.attr.connectOnPrimaryContainer),
        )
    }

    private fun applyClickListener(
        onClick: (() -> Unit)?,
        highlighted: Boolean,
    ) {
        val clickable = onClick != null

        setOnClickListener(if (clickable) OnClickListener { onClick?.invoke() } else null)
        foreground =
            if (clickable) ContextCompat.getDrawable(context, R.drawable.bg_connect_task_card_ripple) else null
        (foreground as? RippleDrawable)?.setColor(ColorStateList.valueOf(rippleColor(highlighted)))
        isClickable = clickable
        isFocusable = clickable
    }

    /**
     * The theme's single control highlight is tuned for light surfaces and all but disappears on the
     * highlighted card, so that one gets a translucent on-primary ripple instead.
     */
    private fun rippleColor(highlighted: Boolean): Int =
        if (highlighted) {
            ColorUtils.setAlphaComponent(
                themeColor(MaterialR.attr.colorOnPrimary),
                HIGHLIGHTED_RIPPLE_ALPHA,
            )
        } else {
            themeColor(AppCompatR.attr.colorControlHighlight)
        }

    private fun themeColor(attr: Int): Int = context.themeColor(attr)

    private fun ImageView.tint(color: Int) = ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(color))

    private companion object {
        /** Matches the 20% the platform's own light-surface control highlight uses. */
        const val HIGHLIGHTED_RIPPLE_ALPHA = 0x33
    }
}
