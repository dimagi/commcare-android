package org.commcare.views.connect

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectSyncStatusCardBinding

/**
 * Reusable Connect card that displays sync status: a circular badge icon plus a bold status line
 * and an optional grey subline. Appearance is driven by content properties; there is no state enum.
 * [warning] switches the badge between the OK (green check) and warning (amber refresh) appearance.
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

        private var syncOkBadgeColor: Int = 0
        private var syncOkIconColor: Int = 0
        private var syncWarningBadgeColor: Int = 0
        private var syncWarningIconColor: Int = 0

        var statusText: CharSequence?
            get() = binding.syncCardText.text
            set(value) {
                binding.syncCardText.text = value
            }

        var statusSubtext: CharSequence?
            get() = binding.syncCardSubtext.text
            set(value) = bindOptionalText(binding.syncCardSubtext, value)

        var warning: Boolean = false
            set(value) {
                field = value
                applyAppearance()
            }

        init {
            radius = resources.getDimension(R.dimen.connect_info_card_corner_radius)
            cardElevation = resources.getDimension(R.dimen.connect_info_card_elevation)
            useCompatPadding = true
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))

            context.withStyledAttributes(
                attrs,
                R.styleable.ConnectSyncStatusCard,
                defStyleAttr,
                R.style.Widget_CommCare_ConnectSyncStatusCard,
            ) {
                syncOkBadgeColor =
                    getColor(
                        R.styleable.ConnectSyncStatusCard_syncOkBadgeColor,
                        ContextCompat.getColor(context, R.color.connect_light_green),
                    )
                syncOkIconColor =
                    getColor(
                        R.styleable.ConnectSyncStatusCard_syncOkIconColor,
                        ContextCompat.getColor(context, R.color.connect_green),
                    )
                syncWarningBadgeColor =
                    getColor(
                        R.styleable.ConnectSyncStatusCard_syncWarningBadgeColor,
                        ContextCompat.getColor(context, R.color.connect_light_amber),
                    )
                syncWarningIconColor =
                    getColor(
                        R.styleable.ConnectSyncStatusCard_syncWarningIconColor,
                        ContextCompat.getColor(context, R.color.connect_yellowish_orange_color),
                    )
                statusText = getString(R.styleable.ConnectSyncStatusCard_statusText)
                statusSubtext = getString(R.styleable.ConnectSyncStatusCard_statusSubtext)
                warning = getBoolean(R.styleable.ConnectSyncStatusCard_warning, false)
            }
        }

        private fun bindOptionalText(
            view: TextView,
            value: CharSequence?,
        ) {
            view.text = value
            view.visibility = if (value.isNullOrEmpty()) GONE else VISIBLE
        }

        private fun applyAppearance() {
            val badgeColor = if (warning) syncWarningBadgeColor else syncOkBadgeColor
            val iconColor = if (warning) syncWarningIconColor else syncOkIconColor
            val iconRes = if (warning) R.drawable.ic_connect_directory_sync else R.drawable.check_update
            binding.syncCardIcon.setImageResource(iconRes)
            binding.syncCardIcon.setColorFilter(iconColor)
            binding.syncCardIcon.backgroundTintList = ColorStateList.valueOf(badgeColor)
        }
    }
