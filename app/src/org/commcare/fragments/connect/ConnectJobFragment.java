package org.commcare.fragments.connect;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.viewbinding.ViewBinding;

import org.commcare.AppUtils;
import org.commcare.activities.connect.ConnectActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectDateUtils;
import org.commcare.connect.PersonalIdManager;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ViewJobCardBinding;
import org.commcare.fragments.base.BaseConnectFragment;
import org.commcare.utils.ViewUtils;

import java.util.Objects;

public abstract class ConnectJobFragment<T extends ViewBinding> extends BaseConnectFragment<T> {
    protected ConnectJobRecord job;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        job = ((ConnectActivity)requireActivity()).getActiveJob();
        Objects.requireNonNull(job);
    }

    protected void refresh() {
    }

    @Override
    public void onResume() {
        super.onResume();
        if (PersonalIdManager.getInstance().isloggedIn()) {
            refresh();
        }
    }

    protected void setupCommonJobCardFields(ViewJobCardBinding jobCard) {
        jobCard.tvJobTitle.setText(job.getTitle());

        @StringRes int dateMessageStringRes;
        if (job.deliveryComplete()) {
            dateMessageStringRes = R.string.connect_job_ended;
        } else {
            dateMessageStringRes = R.string.connect_learn_complete_by;
        }

        jobCard.connectJobEndDateSubHeading.setText(
                getString(
                        dateMessageStringRes,
                        ConnectDateUtils.INSTANCE.formatDate(job.getProjectEndDate())
                )
        );

        String workingHours = job.getWorkingHours();
        boolean hasHours = workingHours != null;
        jobCard.tvJobTime.setVisibility(hasHours ? View.VISIBLE : View.GONE);
        jobCard.tvDailyVisitTitle.setVisibility(hasHours ? View.VISIBLE : View.GONE);
        jobCard.tvJobDescription.setVisibility(View.INVISIBLE);
        jobCard.connectJobEndDateSubHeading.setVisibility(View.VISIBLE);
        jobCard.connectJobEndDate.setVisibility(View.GONE);
        jobCard.tvViewMore.setVisibility(View.GONE);

        if (hasHours) {
            jobCard.tvJobTime.setText(workingHours);
        }
    }

    protected void setupJobCardButtons(
            ViewJobCardBinding jobCard,
            boolean appInstalled,
            View.OnClickListener resumeListener,
            View.OnClickListener viewInfoListener
    ) {
        @DrawableRes int resumeBackgroundDrawableRes;
        @ColorRes int resumeTextColorRes;
        @DrawableRes int viewInfoBackgroundDrawableRes;
        @ColorRes int viewInfoTextColorRes;
        if (job.isFinished()) {
            resumeBackgroundDrawableRes = R.drawable.bg_rounded_corner_lavender_70;
            resumeTextColorRes = R.color.connect_blue_color;
            viewInfoBackgroundDrawableRes = R.drawable.bg_rounded_blue_70;
            viewInfoTextColorRes = R.color.white;
        } else {
            resumeBackgroundDrawableRes = R.drawable.bg_rounded_blue_70;
            resumeTextColorRes = R.color.white;
            viewInfoBackgroundDrawableRes = R.drawable.bg_rounded_corner_lavender_70;
            viewInfoTextColorRes = R.color.connect_blue_color;
        }

        // Setup the resume button.
        Drawable downloadIcon = appInstalled
                ? null
                : ContextCompat.getDrawable(requireContext(), R.drawable.ic_download_circle);
        jobCard.acbResume.setCompoundDrawablesRelativeWithIntrinsicBounds(
                downloadIcon,
                null,
                null,
                null
        );
        jobCard.acbResume.setBackground(
                ContextCompat.getDrawable(requireContext(), resumeBackgroundDrawableRes)
        );
        jobCard.acbResume.setTextColor(
                ContextCompat.getColor(requireContext(), resumeTextColorRes)
        );
        jobCard.acbResume.setOnClickListener(resumeListener);
        int paddingHorizontalPx = ViewUtils.dpToPx(8, requireContext());
        jobCard.acbResume.setPaddingRelative(
                paddingHorizontalPx,
                0,
                paddingHorizontalPx,
                0
        );
        jobCard.acbResume.setVisibility(View.VISIBLE);

        // Setup the view info button.
        jobCard.acbViewInfo.setBackground(
                ContextCompat.getDrawable(requireContext(), viewInfoBackgroundDrawableRes)
        );
        jobCard.acbViewInfo.setTextColor(
                ContextCompat.getColor(requireContext(), viewInfoTextColorRes)
        );
        jobCard.acbViewInfo.setOnClickListener(viewInfoListener);
        jobCard.acbViewInfo.setVisibility(View.VISIBLE);
    }
}
