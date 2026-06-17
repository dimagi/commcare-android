package org.commcare.views.connect

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectInfoCardBinding

/**
 * Reusable half-width Connect info card used in Connect pages
 *
 * The card stacks small [topText], a strong [centerText], and medium [bottomText]. The center
 * slot also holds a sync icon ([showSyncIcon]) that shares the same space as [centerText], so a
 * caller chooses whether to show the icon and may pass a blank [centerText] to hide it. Setting
 * [navigable] shows a diagonal arrow in the top-right corner and makes the card clickable.
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

        var topText: CharSequence?
            get() = binding.infoCardTopText.text
            set(value) = bindOptionalText(binding.infoCardTopText, value)

        var centerText: CharSequence?
            get() = binding.infoCardCenterText.text
            set(value) = bindOptionalText(binding.infoCardCenterText, value)

        var bottomText: CharSequence?
            get() = binding.infoCardBottomText.text
            set(value) = bindOptionalText(binding.infoCardBottomText, value)

        var showSyncIcon: Boolean = false
            set(value) {
                field = value
                binding.infoCardSyncIcon.visibility = if (value) VISIBLE else GONE
            }

        var navigable: Boolean = false
            set(value) {
                field = value
                binding.infoCardArrow.visibility = if (value) VISIBLE else GONE
                foreground = if (value) selectableItemForeground() else null
                isClickable = value
                isFocusable = value
            }

        init {
            radius = resources.getDimension(R.dimen.connect_info_card_corner_radius)
            cardElevation = resources.getDimension(R.dimen.connect_info_card_elevation)
            useCompatPadding = true
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))

            context.obtainStyledAttributes(attrs, R.styleable.ConnectInfoCard).apply {
                topText = getString(R.styleable.ConnectInfoCard_topText)
                centerText = getString(R.styleable.ConnectInfoCard_centerText)
                bottomText = getString(R.styleable.ConnectInfoCard_bottomText)
                showSyncIcon = getBoolean(R.styleable.ConnectInfoCard_showSyncIcon, false)
                navigable = getBoolean(R.styleable.ConnectInfoCard_navigable, false)
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
