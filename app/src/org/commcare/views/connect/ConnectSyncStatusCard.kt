package org.commcare.views.connect

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.widget.ImageViewCompat
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectSyncStatusCardBinding

/**
 * Reusable Connect card that displays sync status: a circular badge icon plus a bold status line
 * and an optional grey subline. Appearance is driven by content properties; there is no state enum.
 * [State.warning] switches the badge between the OK (green check) and warning (amber refresh)
 * appearance.
 */
class ConnectSyncStatusCard
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : CardView(context, attrs, defStyleAttr) {
        private val binding =
            ViewConnectSyncStatusCardBinding.inflate(LayoutInflater.from(context), this, true)

        private var syncOkBadgeColor = 0
        private var syncOkIconColor = 0
        private var syncWarningBadgeColor = 0
        private var syncWarningIconColor = 0

        /** The state currently bound to the card. */
        var state: State = State()
            private set

        /** Complete, atomic description of everything the card renders. */
        data class State(
            val statusText: CharSequence? = null,
            val statusSubtext: CharSequence? = null,
            val warning: Boolean = false,
        )

        init {
            radius = resources.getDimension(R.dimen.connect_info_card_corner_radius)
            cardElevation = resources.getDimension(R.dimen.connect_info_card_elevation)
            useCompatPadding = true
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))

            var initialState = State()

            context.withStyledAttributes(
                attrs,
                R.styleable.ConnectSyncStatusCard,
                defStyleAttr,
                R.style.Widget_CommCare_ConnectSyncStatusCard,
            ) {
                syncOkBadgeColor = getColor(R.styleable.ConnectSyncStatusCard_syncOkBadgeColor, 0)
                syncOkIconColor = getColor(R.styleable.ConnectSyncStatusCard_syncOkIconColor, 0)
                syncWarningBadgeColor = getColor(R.styleable.ConnectSyncStatusCard_syncWarningBadgeColor, 0)
                syncWarningIconColor = getColor(R.styleable.ConnectSyncStatusCard_syncWarningIconColor, 0)
                initialState = readStateFromAttributes(this)
            }

            bind(initialState)
        }

        /** Render [state], replacing whatever was previously shown. */
        fun bind(state: State) {
            this.state = state
            binding.syncCardText.text = state.statusText
            bindOptionalText(binding.syncCardSubtext, state.statusSubtext)
            applyAppearance(state.warning)
        }

        private fun bindOptionalText(
            view: TextView,
            value: CharSequence?,
        ) {
            view.text = value
            view.visibility = if (value.isNullOrEmpty()) GONE else VISIBLE
        }

        private fun applyAppearance(warning: Boolean) {
            val badgeColor = if (warning) syncWarningBadgeColor else syncOkBadgeColor
            val iconColor = if (warning) syncWarningIconColor else syncOkIconColor
            val iconRes = if (warning) R.drawable.ic_connect_directory_sync else R.drawable.check_update
            binding.syncCardIcon.setImageResource(iconRes)
            ImageViewCompat.setImageTintList(binding.syncCardIcon, ColorStateList.valueOf(iconColor))
            binding.syncCardIcon.backgroundTintList = ColorStateList.valueOf(badgeColor)
        }

        private fun readStateFromAttributes(typedArray: TypedArray): State =
            State(
                statusText = typedArray.getString(R.styleable.ConnectSyncStatusCard_statusText),
                statusSubtext = typedArray.getString(R.styleable.ConnectSyncStatusCard_statusSubtext),
                warning = typedArray.getBoolean(R.styleable.ConnectSyncStatusCard_warning, false),
            )
    }
