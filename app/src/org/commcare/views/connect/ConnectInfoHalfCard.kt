package org.commcare.views.connect

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectInfoHalfCardBinding

/**
 * Reusable half-width Connect info card.
 *
 * Stacks a large [valueText] at the top, a medium [titleText] in the middle, and an optional
 * smaller [subtitleText] at the bottom. An optional [icon] sits in the top-right. Setting
 * [navigable] makes the card clickable with a ripple foreground.
 */
class ConnectInfoHalfCard
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : CardView(context, attrs, defStyleAttr) {
        private val binding =
            ViewConnectInfoHalfCardBinding.inflate(LayoutInflater.from(context), this, true)

        var valueText: CharSequence?
            get() = binding.infoCardValueText.text
            set(value) = bindOptionalText(binding.infoCardValueText, value)

        var titleText: CharSequence?
            get() = binding.infoCardTitleText.text
            set(value) = bindOptionalText(binding.infoCardTitleText, value)

        var subtitleText: CharSequence?
            get() = binding.infoCardSubtitleText.text
            set(value) = bindOptionalText(binding.infoCardSubtitleText, value)

        var icon: Drawable? = null
            set(value) {
                field = value
                binding.infoCardIcon.setImageDrawable(value)
                binding.infoCardIcon.visibility = if (value == null) GONE else VISIBLE
            }

        var navigable: Boolean = false
            set(value) {
                field = value
                foreground = if (value) selectableItemForeground() else null
                isClickable = value
                isFocusable = value
            }

        init {
            radius = resources.getDimension(R.dimen.connect_info_card_corner_radius)
            cardElevation = resources.getDimension(R.dimen.connect_info_card_elevation)
            useCompatPadding = true
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))

            context.obtainStyledAttributes(attrs, R.styleable.ConnectInfoHalfCard).apply {
                valueText = getString(R.styleable.ConnectInfoHalfCard_valueText)
                titleText = getString(R.styleable.ConnectInfoHalfCard_titleText)
                subtitleText = getString(R.styleable.ConnectInfoHalfCard_subtitleText)
                icon = getDrawable(R.styleable.ConnectInfoHalfCard_icon)
                navigable = getBoolean(R.styleable.ConnectInfoHalfCard_navigable, false)
                recycle()
            }
        }

        private fun bindOptionalText(
            view: TextView,
            value: CharSequence?,
        ) {
            view.text = value
            view.visibility = if (value.isNullOrEmpty()) GONE else VISIBLE
        }

        private fun selectableItemForeground() =
            TypedValue().let {
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
                ContextCompat.getDrawable(context, it.resourceId)
            }
    }
