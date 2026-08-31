package org.commcare.views.connect

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.view.updateLayoutParams
import androidx.core.widget.ImageViewCompat
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectProgressCardBinding
import org.commcare.util.LogTypes
import org.commcare.utils.ProgressUtils
import org.javarosa.core.services.Logger

/**
 * Reusable Connect card composing a progress area (an optional semi-circle indicator plus a
 * horizontal progress bar with label, count and caption), with an independently controllable
 * info-message banner (with an optional call-to-action button) that tucks up behind the main card,
 * in either a blue informational or a grey warning appearance.
 * Used by both the Learning Progress and Delivery Progress views.
 *
 * The whole card is configured atomically through a single [State] passed to [bind]; there are no
 * per-property setters, so a caller cannot leave the view in a half-configured state. Appearance is
 * driven entirely by content; there is no state enum.
 */
class ConnectProgressCard
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : LinearLayout(context, attrs, defStyleAttr) {
        private val binding =
            ViewConnectProgressCardBinding.inflate(LayoutInflater.from(context), this)

        private val contentPrimaryColor: Int
        private val contentAccentColor: Int
        private val contentDisabledColor: Int

        /** The state currently bound to the card. */
        var state: State = State()
            private set

        /**
         * Complete, atomic description of everything the card renders. Absent sub-sections
         * ([linearProgress], [semiCircle], [info] left null) hide the corresponding area.
         */
        data class State(
            val title: CharSequence? = null,
            val contentEnabled: Boolean = true,
            val linearProgress: LinearProgress? = null,
            val semiCircle: SemiCircle? = null,
            val info: Info? = null,
        ) {
            data class LinearProgress(
                val label: CharSequence? = null,
                val current: Int = 0,
                val max: Int = 0,
                val caption: CharSequence? = null,
            )

            data class SemiCircle(
                val current: Int = 0,
                val max: Int = 0,
                val description: CharSequence? = null,
            )

            class Info(
                val message: CharSequence,
                val ctaText: CharSequence? = null,
                val onCtaClick: (() -> Unit)? = null,
                val appearance: Appearance = Appearance.INFO,
            ) {
                /** [WARNING] adds a warning icon and inverts the banner to blue-on-grey. */
                enum class Appearance { INFO, WARNING }
            }
        }

        init {
            orientation = VERTICAL

            var primary = ContextCompat.getColor(context, R.color.connect_text_color)
            var accent = ContextCompat.getColor(context, R.color.connect_dark_blue_color)
            var disabled = ContextCompat.getColor(context, R.color.connect_grey)
            var initialState = State()

            context.withStyledAttributes(
                attrs,
                R.styleable.ConnectProgressCard,
                defStyleAttr,
                R.style.Widget_CommCare_ConnectProgressCard,
            ) {
                primary = getColor(R.styleable.ConnectProgressCard_contentPrimaryColor, primary)
                accent = getColor(R.styleable.ConnectProgressCard_contentAccentColor, accent)
                disabled = getColor(R.styleable.ConnectProgressCard_contentDisabledColor, disabled)
                initialState = readStateFromAttributes(this)
            }

            contentPrimaryColor = primary
            contentAccentColor = accent
            contentDisabledColor = disabled

            bind(initialState)
        }

        /** Render [state], replacing whatever was previously shown. */
        fun bind(state: State) {
            this.state = state
            bindOptionalText(binding.progressCardTitle, state.title)
            bindSemiCircle(state.semiCircle)
            bindLinearProgress(state.linearProgress)
            bindBarRowSpacing()
            bindInfo(state.info)
            applyContentColors(state.contentEnabled)
        }

        /**
         * A GONE view's margins are ignored but the bar row's own top margin is not, so with no
         * title and no semi-circle the row would sit a margin's width below the card padding
         * instead of against it.
         */
        private fun bindBarRowSpacing() {
            val hasContentAbove =
                binding.progressCardTitle.visibility == VISIBLE ||
                    binding.progressCardSemiCircle.visibility == VISIBLE
            binding.progressCardBarRow.updateLayoutParams<MarginLayoutParams> {
                topMargin =
                    if (hasContentAbove) resources.getDimensionPixelSize(R.dimen.connect_space_lg) else 0
            }
        }

        private fun bindSemiCircle(semiCircle: State.SemiCircle?) {
            if (semiCircle == null) {
                binding.progressCardSemiCircle.visibility = GONE
                return
            }
            binding.progressCardSemiCircle.visibility = VISIBLE
            binding.progressCardSemiCircle.max = semiCircle.max
            binding.progressCardSemiCircle.current = semiCircle.current
            binding.progressCardSemiCircle.descriptionText = semiCircle.description
        }

        private fun bindLinearProgress(linearProgress: State.LinearProgress?) {
            bindOptionalText(binding.progressCardBarLabel, linearProgress?.label)
            bindOptionalText(binding.progressCardBarCaption, linearProgress?.caption)

            if (linearProgress == null) {
                binding.progressCardLinearBar.setProgress(0f)
                binding.progressCardBarCount.visibility = GONE
                return
            }

            val (current, max) = coerceProgress(linearProgress.current, linearProgress.max)
            binding.progressCardLinearBar.setProgress(ProgressUtils.calculateProgress(current, max) * 100f)
            if (max > 0) {
                binding.progressCardBarCount.text =
                    resources.getString(R.string.connect_progress_count_format, current, max)
                binding.progressCardBarCount.visibility = VISIBLE
            } else {
                binding.progressCardBarCount.visibility = GONE
            }
        }

        /**
         * The card is only raised while the banner is showing, since the elevation exists solely to
         * draw over the part of it that tucks behind. Flat at rest, so no shadow shows at the corners.
         */
        private fun bindInfo(info: State.Info?) {
            binding.progressCardMain.cardElevation =
                if (info == null) 0f else resources.getDimension(R.dimen.connect_progress_card_elevation)

            if (info == null) {
                binding.progressCardInfoMessage.visibility = GONE
                return
            }
            binding.progressCardInfoText.text = info.message
            binding.progressCardInfoMessage.visibility = VISIBLE
            bindOptionalText(binding.progressCardInfoCta, info.ctaText)
            binding.progressCardInfoCta.setOnClickListener(
                info.onCtaClick?.let { callback -> OnClickListener { callback() } },
            )
            applyInfoAppearance(info.appearance)
        }

        private fun applyInfoAppearance(appearance: State.Info.Appearance) {
            val isWarning = appearance == State.Info.Appearance.WARNING
            val background =
                ContextCompat.getColor(
                    context,
                    if (isWarning) R.color.connect_light_grey else R.color.connect_dark_blue_color,
                )
            val foreground =
                ContextCompat.getColor(
                    context,
                    if (isWarning) R.color.connect_dark_blue_color else R.color.white,
                )

            binding.progressCardInfoMessage.setCardBackgroundColor(background)
            binding.progressCardInfoText.setTextColor(foreground)
            binding.progressCardInfoCta.setTextColor(foreground)
            binding.progressCardInfoIcon.visibility = if (isWarning) VISIBLE else GONE
            ImageViewCompat.setImageTintList(
                binding.progressCardInfoIcon,
                ColorStateList.valueOf(foreground),
            )
        }

        /**
         * Clamps progress to a valid range. Out-of-range input signals a programmatic error, so it
         * is logged rather than silently swallowed while still keeping the UI from breaking.
         */
        private fun coerceProgress(
            current: Int,
            max: Int,
        ): Pair<Int, Int> {
            val safeMax = max.coerceAtLeast(0)
            val safeCurrent = current.coerceIn(0, safeMax)
            if (safeMax != max || safeCurrent != current) {
                Logger.log(
                    LogTypes.TYPE_ERROR_ASSERTION,
                    "ConnectProgressCard bound with out-of-range progress " +
                        "(current=$current, max=$max); coerced to (current=$safeCurrent, max=$safeMax)",
                )
            }
            return safeCurrent to safeMax
        }

        private fun bindOptionalText(
            view: TextView,
            value: CharSequence?,
        ) {
            view.text = value
            view.visibility = if (value.isNullOrEmpty()) GONE else VISIBLE
        }

        /** Only the accent is disabled: the primary text carries the same weight either way. */
        private fun applyContentColors(contentEnabled: Boolean) {
            val primary = contentPrimaryColor
            val accent = if (contentEnabled) contentAccentColor else contentDisabledColor

            binding.progressCardTitle.setTextColor(primary)
            binding.progressCardBarLabel.setTextColor(primary)
            binding.progressCardBarCount.setTextColor(accent)
            binding.progressCardBarCaption.setTextColor(accent)
            binding.progressCardLinearBar.setProgressColor(accent)
            binding.progressCardSemiCircle.progressColor = accent
            binding.progressCardSemiCircle.valueTextColor = accent
            binding.progressCardSemiCircle.descriptionTextColor = primary
        }

        private fun readStateFromAttributes(typedArray: TypedArray): State {
            val semiCircle =
                if (typedArray.getBoolean(R.styleable.ConnectProgressCard_semiCircleVisible, false)) {
                    State.SemiCircle(
                        current = typedArray.getInt(R.styleable.ConnectProgressCard_semiCircleCurrent, 0),
                        max = typedArray.getInt(R.styleable.ConnectProgressCard_semiCircleMax, 0),
                        description = typedArray.getString(R.styleable.ConnectProgressCard_semiCircleDescription),
                    )
                } else {
                    null
                }

            val linearProgress =
                State.LinearProgress(
                    label = typedArray.getString(R.styleable.ConnectProgressCard_linearProgressLabel),
                    current = typedArray.getInt(R.styleable.ConnectProgressCard_linearProgressCurrent, 0),
                    max = typedArray.getInt(R.styleable.ConnectProgressCard_linearProgressMax, 0),
                    caption = typedArray.getString(R.styleable.ConnectProgressCard_linearProgressCaption),
                )

            val info =
                typedArray.getString(R.styleable.ConnectProgressCard_infoMessage)?.let { message ->
                    State.Info(
                        message = message,
                        ctaText = typedArray.getString(R.styleable.ConnectProgressCard_infoCtaText),
                    )
                }

            return State(
                title = typedArray.getString(R.styleable.ConnectProgressCard_titleText),
                contentEnabled = typedArray.getBoolean(R.styleable.ConnectProgressCard_contentEnabled, true),
                linearProgress = linearProgress,
                semiCircle = semiCircle,
                info = info,
            )
        }
    }
