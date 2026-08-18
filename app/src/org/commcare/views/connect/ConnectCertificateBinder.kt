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

/**
 * Fills in the learning certificate. Shared so every screen that shows the certificate renders it the
 * same way — its date column is narrow, so the short format and the break before the date matter.
 */
fun ViewConnectLearnCertificateBinding.bindCertificate(
    job: ConnectJobRecord,
    learnerName: String,
    completedOn: Date,
) {
    val context = root.context
    certSubjectText.text = job.title
    certPersonText.text = learnerName
    certDateText.text = completedOnText(context, completedOn)
    certScoreText.apply {
        text = scoreText(context, job.assessmentScore)
        visibility = if (job.attemptedAssessment()) View.VISIBLE else View.GONE
    }
}

/**
 * "Completed on \<date\>", broken onto its own line so a long month name cannot squeeze the column
 * beside it.
 */
private fun completedOnText(
    context: Context,
    completedOn: Date,
): CharSequence {
    val date = ConnectDateUtils.formatDate(completedOn, DateFormat.SHORT)
    val full = context.getString(R.string.connect_learn_completed, date)
    val dateStart = full.lastIndexOf(date)

    return if (dateStart <= 0) {
        full
    } else {
        full.substring(0, dateStart).trimEnd() + "\n" + full.substring(dateStart)
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
