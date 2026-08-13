package org.commcare.views.connect

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import org.commcare.AppUtils
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectDateUtils
import org.commcare.connect.ConnectMoneyUtils
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectLearnCompleteBinding
import java.text.DateFormat
import java.util.Date

/**
 * Full-screen Connect view for an opportunity whose learning and assessment are both complete.
 *
 * Shows a Learn summary with a collapsible certificate, the delivery figures for the opportunity,
 * and a [ConnectCtaBar] pinned below the scrolling content. Call [bind] to populate it; the
 * certificate's expanded state is managed internally and starts expanded.
 *
 * Claiming is driven by the caller: toggle [isCtaEnabled] while a claim is in flight, and report the
 * outcome through [showClaimFailure] / [hideClaimFailure] — the failure message is supplied by the
 * caller rather than derived here.
 */
class ConnectLearnCompleteView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : LinearLayout(context, attrs, defStyleAttr) {
        private val binding =
            ViewConnectLearnCompleteBinding.inflate(LayoutInflater.from(context), this)

        private val certificateBottomPadding = binding.certificateContainer.paddingBottom

        var isCtaEnabled: Boolean
            get() = binding.learnCompleteCtaBar.isCtaEnabled
            set(value) {
                binding.learnCompleteCtaBar.isCtaEnabled = value
            }

        init {
            orientation = VERTICAL
            binding.certificateHeader.setOnClickListener { toggleCertificate() }
        }

        fun showClaimFailure(message: String) {
            binding.learnCompleteFailureCard.show(ConnectSuccessFailureCard.Mode.FAILURE, message)
        }

        fun hideClaimFailure() {
            binding.learnCompleteFailureCard.visibility = GONE
        }

        fun bind(
            job: ConnectJobRecord,
            completedOn: Date,
            learnerName: String,
            onCtaClick: OnClickListener,
        ) {
            binding.learnCompleteCompletedOn.text = completedOnText(completedOn)
            bindCertificate(job, learnerName, completedOn)
            bindDeliveryCards(job)
            bindCtaBar(job, onCtaClick)
        }

        private fun completedOnText(
            completedOn: Date,
            breakBeforeDate: Boolean = false,
        ): CharSequence {
            val date = ConnectDateUtils.formatDate(completedOn, DateFormat.SHORT)
            val full = context.getString(R.string.connect_learn_completed, date)
            val dateStart = full.lastIndexOf(date)

            return if (!breakBeforeDate || dateStart <= 0) {
                full
            } else {
                full.substring(0, dateStart).trimEnd() + "\n" + full.substring(dateStart)
            }
        }

        private fun bindCertificate(
            job: ConnectJobRecord,
            learnerName: String,
            completedOn: Date,
        ) {
            binding.certificate.certSubjectText.text = job.title
            binding.certificate.certPersonText.text = learnerName
            binding.certificate.certDateText.text =
                completedOnText(completedOn, breakBeforeDate = true)
            binding.certificate.certScoreText.apply {
                text = scoreText(job.assessmentScore)
                visibility = if (job.attemptedAssessment()) VISIBLE else GONE
            }
        }

        private fun scoreText(score: Int): CharSequence {
            val full = context.getString(R.string.connect_learn_cert_score, score.toString())
            val valueStart = full.indexOf(SCORE_VALUE_SEPARATOR)
            if (valueStart < 0) {
                return full
            }

            return SpannableString(full).apply {
                setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.white)),
                    valueStart,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }

        private fun bindDeliveryCards(job: ConnectJobRecord) {
            binding.cardTotalVisits.valueText = job.maxPossibleVisits.toString()
            binding.cardTotalVisits.subtitleText =
                context.getString(R.string.connect_opportunity_visits_per_day, job.maxDailyVisits)

            binding.cardDaysToComplete.valueText = job.daysRemaining.toString()

            binding.cardMaxEarnings.valueText =
                ConnectMoneyUtils.moneyStringWithSymbol(job.currency, job.totalBudget)
            binding.cardMaxEarnings.subtitleText =
                resources.getQuantityString(
                    R.plurals.connect_opportunity_payment_units,
                    job.paymentUnits.size,
                    job.paymentUnits.size,
                )
        }

        private fun bindCtaBar(
            job: ConnectJobRecord,
            onCtaClick: OnClickListener,
        ) {
            val deliveryAppInstalled = AppUtils.isAppInstalled(job.deliveryAppInfo.appId)

            binding.learnCompleteCtaBar.apply {
                if (deliveryAppInstalled) {
                    buttonText = context.getString(R.string.connect_delivery_go)
                    subtitleText = null
                } else {
                    buttonText = context.getString(R.string.connect_opportunity_footer_download_app)
                    subtitleText = context.getString(R.string.connect_job_info_download_delivery).trim()
                }
                infoMessage =
                    if (job.isFinished) context.getString(R.string.connect_learn_warning_ended) else null
                isCtaEnabled = true
                setOnCtaClickListener(onCtaClick)
            }
        }

        private fun toggleCertificate() {
            val expand = binding.certificate.root.visibility != VISIBLE

            if (expand) {
                binding.certificate.root.visibility = VISIBLE
                binding.certificateChevron.rotation = EXPANDED_CHEVRON_ROTATION
                binding.certificateContainer.updatePadding(bottom = certificateBottomPadding)
            } else {
                binding.certificate.root.visibility = GONE
                binding.certificateChevron.rotation = 0f
                binding.certificateContainer.updatePadding(bottom = 0)
            }
        }

        companion object {
            private const val EXPANDED_CHEVRON_ROTATION = 180f
            private const val SCORE_VALUE_SEPARATOR = ':'
        }
    }
