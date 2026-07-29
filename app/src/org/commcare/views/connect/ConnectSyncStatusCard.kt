package org.commcare.views.connect

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.widget.ImageViewCompat
import com.google.android.material.card.MaterialCardView
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectSyncStatusCardBinding
import org.commcare.views.extensions.bindOptional

/**
 * Reusable Connect card that displays sync status: a bold status line with an optional grey
 * subline, plus a trailing circular badge icon. [State.warning] selects the [Appearance] that
 * drives the badge icon, the badge colors and the card outline, switching between the OK (green
 * check, grey outline) and warning (amber refresh, amber outline) appearance. Registering an
 * [onCardClick] callback makes the card tappable with a ripple foreground.
 */
class ConnectSyncStatusCard
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : MaterialCardView(context, attrs, defStyleAttr) {
        private val binding =
            ViewConnectSyncStatusCardBinding.inflate(LayoutInflater.from(context), this, true)

        private var syncOkBadgeColor = 0
        private var syncOkIconColor = 0
        private var syncOkStrokeColor = 0
        private var syncWarningBadgeColor = 0
        private var syncWarningIconColor = 0
        private var syncWarningStrokeColor = 0
        private var syncOkIcon = 0
        private var syncWarningIcon = 0

        /** The state currently bound to the card. */
        var state: State = State()
            private set

        /**
         * Runs when the card is tapped. Setting it to null makes the card inert again. The ripple
         * comes from [MaterialCardView], which draws one whenever the card is clickable.
         */
        var onCardClick: (() -> Unit)? = null
            set(value) {
                field = value
                // setOnClickListener forces isClickable on, even for null, so reset it afterwards.
                setOnClickListener(value?.let { callback -> OnClickListener { callback() } })
                isClickable = value != null
                isFocusable = value != null
            }

        /** Complete, atomic description of everything the card renders. */
        data class State(
            val statusText: CharSequence? = null,
            val statusSubtext: CharSequence? = null,
            val warning: Boolean = false,
        )

        init {
            radius = resources.getDimension(R.dimen.connect_radius_card)
            cardElevation = resources.getDimension(R.dimen.connect_info_card_elevation)
            strokeWidth = resources.getDimensionPixelSize(R.dimen.connect_stroke_hairline)
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
                syncOkStrokeColor = getColor(R.styleable.ConnectSyncStatusCard_syncOkStrokeColor, 0)
                syncWarningBadgeColor = getColor(R.styleable.ConnectSyncStatusCard_syncWarningBadgeColor, 0)
                syncWarningIconColor = getColor(R.styleable.ConnectSyncStatusCard_syncWarningIconColor, 0)
                syncWarningStrokeColor = getColor(R.styleable.ConnectSyncStatusCard_syncWarningStrokeColor, 0)
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
                    Appearance.Ok -> {
                        AppearanceStyle(syncOkBadgeColor, syncOkIconColor, syncOkStrokeColor, syncOkIcon)
                    }

                    Appearance.Warning -> {
                        AppearanceStyle(
                            syncWarningBadgeColor,
                            syncWarningIconColor,
                            syncWarningStrokeColor,
                            syncWarningIcon,
                        )
                    }
                }
            binding.syncCardIcon.setImageResource(style.iconRes)
            ImageViewCompat.setImageTintList(binding.syncCardIcon, ColorStateList.valueOf(style.iconColor))
            binding.syncCardIcon.backgroundTintList = ColorStateList.valueOf(style.badgeColor)
            setStrokeColor(style.strokeColor)
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
            @ColorInt val strokeColor: Int,
            @DrawableRes val iconRes: Int,
        )
    }
