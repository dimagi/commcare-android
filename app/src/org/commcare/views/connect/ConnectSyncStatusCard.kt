package org.commcare.views.connect

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.widget.ImageViewCompat
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectSyncStatusCardBinding
import org.commcare.views.extensions.bindOptional

/**
 * Reusable Connect card that displays sync status: a circular badge icon plus a bold status line
 * and an optional grey subline. [State.warning] selects the [Appearance] that drives the badge
 * icon and colors, switching between the OK (green check) and warning (amber refresh) appearance.
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
        private var syncOkIcon = 0
        private var syncWarningIcon = 0

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
                syncOkIcon = getResourceId(R.styleable.ConnectSyncStatusCard_syncOkIcon, 0)
                syncWarningIcon = getResourceId(R.styleable.ConnectSyncStatusCard_syncWarningIcon, 0)
                initialState = readStateFromAttributes(this)
            }

            bind(initialState)
        }

        /** Render [state], replacing whatever was previously shown. */
        fun bind(state: State) {
            this.state = state
            binding.syncCardText.text = state.statusText
            binding.syncCardSubtext.bindOptional(state.statusSubtext)
            applyAppearance(if (state.warning) Appearance.Warning else Appearance.Ok)
        }

        private fun applyAppearance(appearance: Appearance) {
            val style =
                when (appearance) {
                    Appearance.Ok -> AppearanceStyle(syncOkBadgeColor, syncOkIconColor, syncOkIcon)
                    Appearance.Warning -> AppearanceStyle(syncWarningBadgeColor, syncWarningIconColor, syncWarningIcon)
                }
            binding.syncCardIcon.setImageResource(style.iconRes)
            ImageViewCompat.setImageTintList(binding.syncCardIcon, ColorStateList.valueOf(style.iconColor))
            binding.syncCardIcon.backgroundTintList = ColorStateList.valueOf(style.badgeColor)
        }

        private fun readStateFromAttributes(typedArray: TypedArray): State =
            State(
                statusText = typedArray.getString(R.styleable.ConnectSyncStatusCard_statusText),
                statusSubtext = typedArray.getString(R.styleable.ConnectSyncStatusCard_statusSubtext),
                warning = typedArray.getBoolean(R.styleable.ConnectSyncStatusCard_warning, false),
            )

        /** Visual states the card can render. Add new states (e.g. an error state) here. */
        private sealed interface Appearance {
            object Ok : Appearance

            object Warning : Appearance
        }

        private data class AppearanceStyle(
            @ColorInt val badgeColor: Int,
            @ColorInt val iconColor: Int,
            @DrawableRes val iconRes: Int,
        )
    }
