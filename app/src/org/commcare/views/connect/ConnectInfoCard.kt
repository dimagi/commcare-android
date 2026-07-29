package org.commcare.views.connect

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectInfoCardBinding
import org.commcare.views.extensions.bindOptional

/**
 * Reusable full-width Connect info card.
 *
 * Lays out a large [valueText] on the left, a [titleText] with an optional [subtitleText] in the
 * middle, and a trailing arrow on the right. Setting [navigable] makes the card clickable with a
 * ripple foreground and reveals the arrow to signal that the card can be tapped. Registering an
 * [onCardClick] callback turns on that navigable appearance automatically and runs the callback
 * when the card is tapped.
 */
class ConnectInfoCard
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : CardView(context, attrs, defStyleAttr) {
        private val binding =
            ViewConnectInfoCardBinding.inflate(LayoutInflater.from(context), this, true)

        var valueText: CharSequence?
            get() = binding.infoCardValueText.text
            set(value) = binding.infoCardValueText.bindOptional(value)

        var titleText: CharSequence?
            get() = binding.infoCardTitleText.text
            set(value) = binding.infoCardTitleText.bindOptional(value)

        var subtitleText: CharSequence?
            get() = binding.infoCardSubtitleText.text
            set(value) = binding.infoCardSubtitleText.bindOptional(value)

        var navigable: Boolean = false
            set(value) {
                field = value
                foreground = if (value) selectableItemForeground() else null
                isClickable = value
                isFocusable = value
                binding.infoCardArrow.visibility = if (value) VISIBLE else GONE
            }

        var onCardClick: (() -> Unit)? = null
            set(value) {
                field = value
                // setOnClickListener forces isClickable on, even for null, so apply it first.
                setOnClickListener(value?.let { callback -> OnClickListener { callback() } })
                navigable = value != null
            }

        init {
            radius = resources.getDimension(R.dimen.connect_radius_card)
            cardElevation = resources.getDimension(R.dimen.connect_info_card_elevation)
            useCompatPadding = true
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))

            context.withStyledAttributes(attrs, R.styleable.ConnectInfoCard) {
                valueText = getString(R.styleable.ConnectInfoCard_valueText)
                titleText = getString(R.styleable.ConnectInfoCard_titleText)
                subtitleText = getString(R.styleable.ConnectInfoCard_subtitleText)
                navigable = getBoolean(R.styleable.ConnectInfoCard_navigable, false)
            }
        }

        private fun selectableItemForeground() =
            TypedValue().let {
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
                ContextCompat.getDrawable(context, it.resourceId)
            }
    }
