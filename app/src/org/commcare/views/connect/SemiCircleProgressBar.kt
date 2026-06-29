package org.commcare.views.connect

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewSemiCircleProgressBarBinding

/**
 * Reusable semi-circle (180°) progress indicator.
 *
 * A [SemiCircleArcView] draws the track and progress arcs while two centred text lines sit inside
 * the dome: a large value line formatted as "[current] of [max]" and a smaller description line
 * beneath it.
 *
 * The four colours used by the component ([progressColor], [trackColor], [valueTextColor] and
 * [descriptionTextColor]) are supplied through the [R.style.Widget_CommCare_SemiCircleProgressBar]
 * style so they can be themed in one place, while [current], [max] and [descriptionText] are the
 * per-instance customisable content.
 */
class SemiCircleProgressBar
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : ConstraintLayout(context, attrs, defStyleAttr) {
        private val binding =
            ViewSemiCircleProgressBarBinding.inflate(LayoutInflater.from(context), this, true)

        var progressColor: Int
            get() = binding.semiCircleArc.progressColor
            set(value) {
                binding.semiCircleArc.progressColor = value
            }

        var trackColor: Int
            get() = binding.semiCircleArc.trackColor
            set(value) {
                binding.semiCircleArc.trackColor = value
            }

        var valueTextColor: Int = 0
            set(value) {
                field = value
                binding.semiCircleValueText.setTextColor(value)
            }

        var descriptionTextColor: Int = 0
            set(value) {
                field = value
                binding.semiCircleDescriptionText.setTextColor(value)
            }

        var current: Int = 0
            set(value) {
                field = value.coerceIn(0, max)
                onValueChanged()
            }

        var max: Int = 100
            set(value) {
                field = value.coerceAtLeast(0)
                current = current.coerceIn(0, field)
                onValueChanged()
            }

        var descriptionText: CharSequence?
            get() = binding.semiCircleDescriptionText.text
            set(value) {
                binding.semiCircleDescriptionText.text = value
                binding.semiCircleDescriptionText.visibility = if (value.isNullOrEmpty()) GONE else VISIBLE
                updateContentDescription()
            }

        init {
            context
                .obtainStyledAttributes(
                    attrs,
                    R.styleable.SemiCircleProgressBar,
                    defStyleAttr,
                    R.style.Widget_CommCare_SemiCircleProgressBar,
                ).apply {
                    getDimension(R.styleable.SemiCircleProgressBar_strokeWidth, -1f).let {
                        if (it >= 0) binding.semiCircleArc.strokeWidth = it
                    }
                    progressColor =
                        getColor(
                            R.styleable.SemiCircleProgressBar_progressColor,
                            ContextCompat.getColor(context, R.color.connect_dark_blue_color),
                        )
                    trackColor =
                        getColor(
                            R.styleable.SemiCircleProgressBar_trackColor,
                            ContextCompat.getColor(context, R.color.connect_light_grey),
                        )
                    valueTextColor =
                        getColor(
                            R.styleable.SemiCircleProgressBar_valueTextColor,
                            ContextCompat.getColor(context, R.color.connect_dark_blue_color),
                        )
                    descriptionTextColor =
                        getColor(
                            R.styleable.SemiCircleProgressBar_descriptionTextColor,
                            ContextCompat.getColor(context, R.color.connect_text_color),
                        )
                    getDimension(R.styleable.SemiCircleProgressBar_valueTextSize, -1f).let {
                        if (it >= 0) binding.semiCircleValueText.setTextSize(TypedValue.COMPLEX_UNIT_PX, it)
                    }
                    getDimension(R.styleable.SemiCircleProgressBar_descriptionTextSize, -1f).let {
                        if (it >= 0) binding.semiCircleDescriptionText.setTextSize(TypedValue.COMPLEX_UNIT_PX, it)
                    }
                    max = getInt(R.styleable.SemiCircleProgressBar_maxValue, max)
                    current = getInt(R.styleable.SemiCircleProgressBar_currentValue, current)
                    descriptionText = getString(R.styleable.SemiCircleProgressBar_descriptionText)
                    recycle()
                }
        }

        private fun onValueChanged() {
            binding.semiCircleValueText.text = valueLabel()
            binding.semiCircleArc.progress = if (max > 0) current.toFloat() / max else 0f
            updateContentDescription()
        }

        private fun valueLabel() = resources.getString(R.string.semi_circle_progress_value_format, current, max)

        private fun updateContentDescription() {
            contentDescription =
                listOfNotNull(valueLabel(), descriptionText?.toString()).joinToString(" ")
        }
    }
