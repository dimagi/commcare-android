package org.commcare.views.connect

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import androidx.annotation.DrawableRes
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectTaskCardBinding
import org.commcare.views.extensions.bindOptional
import org.commcare.views.extensions.themeColor
import org.commcare.views.extensions.tint
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

/**
 * Reusable Connect card for a single actionable task, inverted onto the primary color when it is the
 * screen's main action.
 */
class ConnectTaskCard(
    context: Context,
) : CardView(context) {
    private val binding =
        ViewConnectTaskCardBinding.inflate(LayoutInflater.from(context), this, true)

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
        cardElevation = resources.getDimension(R.dimen.connect_card_elevation_low)
        // Compat padding reserves room around the shadow, which would break the even gaps the list
        // sets between cards.
        useCompatPadding = false
        bind(state)
    }

    fun bind(state: State) {
        this.state = state

        binding.taskCardTitle.text = state.title
        binding.taskCardExpiry.bindOptional(state.expiryLabel)
        binding.taskCardIcon.setImageResource(state.iconRes)
        binding.taskCardIcon.isVisible = state.iconRes != 0

        applyAppearance(state.highlighted)
        applyClickListener(state.onClick)
    }

    private fun applyAppearance(highlighted: Boolean) {
        if (highlighted) {
            setCardBackgroundColor(themeColor(AppCompatR.attr.colorPrimary))
            binding.taskCardTitle.setTextColor(themeColor(MaterialR.attr.colorOnPrimary))
            binding.taskCardExpiry.setTextColor(themeColor(MaterialR.attr.colorOnPrimary))
            binding.taskCardChevron.tint(themeColor(MaterialR.attr.colorOnPrimary))
            binding.taskCardIcon.backgroundTintList =
                ColorStateList.valueOf(themeColor(R.attr.connectInversePrimary))
            binding.taskCardIcon.tint(themeColor(MaterialR.attr.colorOnPrimary))
        } else {
            setCardBackgroundColor(themeColor(R.attr.connectSurfaceContainerLow))
            binding.taskCardTitle.setTextColor(themeColor(R.attr.connectOnSurfaceEmphasis))
            binding.taskCardExpiry.setTextColor(themeColor(R.attr.connectOnSurfaceVariant))
            binding.taskCardChevron.tint(themeColor(R.attr.connectOnSurfaceEmphasis))
            binding.taskCardIcon.backgroundTintList =
                ColorStateList.valueOf(themeColor(R.attr.connectPrimaryContainer))
            binding.taskCardIcon.tint(themeColor(R.attr.connectOnPrimaryContainer))
        }
    }

    private fun applyClickListener(onClick: (() -> Unit)?) {
        if (onClick != null) {
            setOnClickListener { onClick() }
            foreground = ContextCompat.getDrawable(context, R.drawable.bg_connect_task_card_ripple)
            isClickable = true
            isFocusable = true
        } else {
            setOnClickListener(null)
            foreground = null
            isClickable = false
            isFocusable = false
        }
    }

    private fun themeColor(attr: Int): Int = context.themeColor(attr)
}
