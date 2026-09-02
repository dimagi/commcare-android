package org.commcare.views.connect

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectCtaBarBinding
import org.commcare.views.extensions.bindOptional

/**
 * Reusable Connect bottom action bar.
 *
 * Shows a [titleText]/[subtitleText] block on the left and, on the right, either a CTA button
 * (Main state) or a circular progress ring (In-progress state) depending on [progress]. An
 * optional [infoMessage] shows a non-dismissible banner above the bar.
 *
 * While an app install runs, [showInstallProgress] takes the bar over — its own wording replaces
 * the idle content until [clearInstallProgress] or [showInstallFailure] hands the bar back. Setting
 * [titleText] or [subtitleText] meanwhile updates what the bar returns to rather than what it shows,
 * so a sync re-binding the screen mid-install cannot overwrite the install wording.
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

        private var idleTitle: CharSequence? = null
        private var idleSubtitle: CharSequence? = null
        private var showingInstallProgress = false

        var titleText: CharSequence?
            get() = idleTitle
            set(value) {
                idleTitle = value
                if (!showingInstallProgress) {
                    binding.ctaTitleText.bindOptional(value)
                }
            }

        var subtitleText: CharSequence?
            get() = idleSubtitle
            set(value) {
                idleSubtitle = value
                if (!showingInstallProgress) {
                    binding.ctaSubtitleText.bindOptional(value)
                }
            }

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
            set(value) = binding.ctaInfoBanner.bindOptional(value)

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

        /** Replaces the bar's idle wording and CTA with install wording and a progress ring. */
        fun showInstallProgress(
            percent: Int,
            subtitle: CharSequence,
        ) {
            hideInstallFailure()
            showingInstallProgress = true
            binding.ctaTitleText.bindOptional(resources.getString(R.string.connect_cta_please_wait))
            binding.ctaSubtitleText.bindOptional(subtitle)
            progress = percent
        }

        /** Hands the bar back to its idle wording and CTA. */
        fun clearInstallProgress() {
            showingInstallProgress = false
            binding.ctaTitleText.bindOptional(idleTitle)
            binding.ctaSubtitleText.bindOptional(idleSubtitle)
            progress = null
        }

        /** Restores the CTA so the install can be retried, and explains why above the bar. */
        fun showInstallFailure(message: CharSequence) {
            clearInstallProgress()
            binding.ctaFailureCard.show(ConnectSuccessFailureCard.Mode.FAILURE, message)
        }

        fun hideInstallFailure() {
            binding.ctaFailureCard.visibility = GONE
        }

        private fun updateProgressState() {
            val value = progress
            if (value == null) {
                binding.ctaButton.visibility = VISIBLE
                binding.ctaProgressRing.visibility = GONE
            } else {
                binding.ctaButton.visibility = GONE
                binding.ctaProgressRing.visibility = VISIBLE
                binding.ctaProgressRing.setProgress(value.toFloat())
                // The design keeps the number off the ring, so the percentage is only announced.
                binding.ctaProgressRing.contentDescription =
                    resources.getString(R.string.connect_cta_progress_percent, value)
            }
        }
    }
