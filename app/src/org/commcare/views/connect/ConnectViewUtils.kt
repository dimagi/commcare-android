package org.commcare.views.connect

import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectDateUtils.formatDate
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewJobCardBinding
import org.commcare.views.ViewUtil

object ConnectViewUtils {
    @JvmStatic
    fun setupCardViewForJob(
        jobCard: ViewJobCardBinding,
        job: ConnectJobRecord,
        appInstalled: Boolean,
        resumeButtonClickListener: View.OnClickListener,
        viewInfoButtonClickListener: View.OnClickListener,
    ) {
        val dateMessageStringRes =
            if (job.deliveryComplete()) {
                R.string.connect_job_ended
            } else {
                R.string.connect_learn_complete_by
            }
        jobCard.connectJobEndDateSubHeading.text =
            jobCard.root.context.getString(
                dateMessageStringRes,
                formatDate(job.projectEndDate),
            )

        val workingHours = job.getWorkingHours()
        if (workingHours != null) {
            jobCard.tvJobTime.visibility = View.VISIBLE
            jobCard.tvDailyVisitTitle.visibility = View.VISIBLE
            jobCard.tvJobTime.text = workingHours
        } else {
            jobCard.tvJobTime.visibility = View.GONE
            jobCard.tvDailyVisitTitle.visibility = View.GONE
        }

        jobCard.tvJobTitle.text = job.title
        jobCard.tvJobDescription.visibility = View.INVISIBLE
        jobCard.connectJobEndDateSubHeading.visibility = View.VISIBLE
        jobCard.connectJobEndDate.visibility = View.GONE
        jobCard.tvViewMore.visibility = View.GONE

        setupJobCardButtons(
            jobCard,
            job,
            appInstalled,
            resumeButtonClickListener,
            viewInfoButtonClickListener,
        )
    }

    private fun setupJobCardButtons(
        jobCard: ViewJobCardBinding,
        job: ConnectJobRecord,
        appInstalled: Boolean,
        resumeListener: View.OnClickListener,
        viewInfoListener: View.OnClickListener,
    ) {
        @DrawableRes val resumeBackgroundDrawableRes: Int

        @ColorRes val resumeTextColorRes: Int

        @DrawableRes val viewInfoBackgroundDrawableRes: Int

        @ColorRes val viewInfoTextColorRes: Int
        if (job.isFinished) {
            resumeBackgroundDrawableRes = R.drawable.bg_rounded_corner_lavender_70
            resumeTextColorRes = R.color.connect_blue_color
            viewInfoBackgroundDrawableRes = R.drawable.bg_rounded_blue_70
            viewInfoTextColorRes = R.color.white
        } else {
            resumeBackgroundDrawableRes = R.drawable.bg_rounded_blue_70
            resumeTextColorRes = R.color.white
            viewInfoBackgroundDrawableRes = R.drawable.bg_rounded_corner_lavender_70
            viewInfoTextColorRes = R.color.connect_blue_color
        }

        val context = jobCard.root.context

        // Setup the resume button.
        val downloadIcon =
            if (appInstalled) {
                null
            } else {
                ContextCompat.getDrawable(context, R.drawable.ic_download_circle)
            }
        jobCard.acbResume.setCompoundDrawablesRelativeWithIntrinsicBounds(
            downloadIcon,
            null,
            null,
            null,
        )
        jobCard.acbResume.background =
            ContextCompat.getDrawable(context, resumeBackgroundDrawableRes)
        jobCard.acbResume.setTextColor(
            ContextCompat.getColor(context, resumeTextColorRes),
        )
        jobCard.acbResume.setOnClickListener(resumeListener)
        val paddingHorizontalPx = ViewUtil.dpToPx(8, context)
        jobCard.acbResume.setPaddingRelative(
            paddingHorizontalPx,
            0,
            paddingHorizontalPx,
            0,
        )
        jobCard.acbResume.visibility = View.VISIBLE

        // Setup the view info button.
        jobCard.acbViewInfo.background =
            ContextCompat.getDrawable(context, viewInfoBackgroundDrawableRes)
        jobCard.acbViewInfo.setTextColor(
            ContextCompat.getColor(context, viewInfoTextColorRes),
        )
        jobCard.acbViewInfo.setOnClickListener(viewInfoListener)
        jobCard.acbViewInfo.visibility = View.VISIBLE
    }
}
