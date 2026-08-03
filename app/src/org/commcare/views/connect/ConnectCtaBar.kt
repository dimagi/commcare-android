package org.commcare.views.connect

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectCtaBarBinding

/**
 * Reusable Connect bottom action bar.
 *
 * Shows a [titleText]/[subtitleText] block on the left and, on the right, either a CTA button
 * (Main state) or a circular percent indicator (In-progress state) depending on [progress]. An
 * optional [infoMessage] shows a non-dismissible banner above the bar.
 */
class ConnectCtaBar
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : LinearLayout(context, attrs, defStyleAttr) {
        private val binding =
            ViewConnectCtaBarBinding.inflate(LayoutInflater.from(context), this)

        var titleText: CharSequence?
            get() = binding.ctaTitleText.text
            set(value) = bindOptionalText(binding.ctaTitleText, value)

        var subtitleText: CharSequence?
            get() = binding.ctaSubtitleText.text
            set(value) = bindOptionalText(binding.ctaSubtitleText, value)

        var buttonText: CharSequence?
            get() = binding.ctaButton.text
            set(value) {
                binding.ctaButton.text = value
            }

        var progress: Int? = null
            set(value) {
                field = value?.coerceIn(0, 100)
                updateProgressState()
            }

        var infoMessage: CharSequence?
            get() = binding.ctaInfoBanner.text
            set(value) = bindOptionalText(binding.ctaInfoBanner, value)

        var isCtaEnabled: Boolean
            get() = binding.ctaButton.isEnabled
            set(value) {
                binding.ctaButton.isEnabled = value
            }

        init {
            orientation = VERTICAL

            context.obtainStyledAttributes(attrs, R.styleable.ConnectCtaBar).apply {
                titleText = getString(R.styleable.ConnectCtaBar_titleText)
                subtitleText = getString(R.styleable.ConnectCtaBar_subtitleText)
                buttonText = getString(R.styleable.ConnectCtaBar_buttonText)
                infoMessage = getString(R.styleable.ConnectCtaBar_infoMessage)
                progress = getInt(R.styleable.ConnectCtaBar_progress, -1).takeIf { it >= 0 }
                recycle()
            }
        }

        fun setOnCtaClickListener(listener: OnClickListener?) {
            binding.ctaButton.setOnClickListener(listener)
        }

        private fun updateProgressState() {
            val value = progress
            if (value == null) {
                binding.ctaButton.visibility = VISIBLE
                binding.ctaProgressCluster.visibility = GONE
            } else {
                binding.ctaButton.visibility = GONE
                binding.ctaProgressCluster.visibility = VISIBLE
                binding.ctaProgressRing.setProgress(value.toFloat())
                binding.ctaProgressText.text =
                    resources.getString(R.string.connect_cta_progress_percent, value)
                binding.ctaProgressCluster.contentDescription = binding.ctaProgressText.text
            }
        }

        private fun bindOptionalText(
            view: TextView,
            value: CharSequence?,
        ) {
            view.text = value
            view.visibility = if (value.isNullOrEmpty()) GONE else VISIBLE
        }
    }
