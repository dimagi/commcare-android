package org.commcare.views.connect

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.core.content.ContextCompat
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectDateUtils
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectLearnCertificateBinding
import java.text.DateFormat
import java.util.Date

/** Fills in the learning certificate, so every screen that shows it renders it the same way. */
fun ViewConnectLearnCertificateBinding.bindCertificate(
    job: ConnectJobRecord,
    learnerName: String,
    learnCompletionDate: Date,
) {
    val context = root.context
    certSubjectText.text = job.title
    certPersonText.text = learnerName
    certDateText.text =
        context.getString(
            R.string.connect_learn_completed,
            ConnectDateUtils.formatDate(learnCompletionDate, DateFormat.SHORT),
        )
    certScoreText.apply {
        text = scoreText(context, job.assessmentScore)
        visibility = if (job.attemptedAssessment()) View.VISIBLE else View.GONE
    }
}

/** Picks the score out of its label so the number reads at full strength against the dark card. */
private fun scoreText(
    context: Context,
    score: Int,
): CharSequence {
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

private const val SCORE_VALUE_SEPARATOR = ':'
