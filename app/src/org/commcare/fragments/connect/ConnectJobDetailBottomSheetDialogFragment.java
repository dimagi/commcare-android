package org.commcare.fragments.connect;

import static org.commcare.connect.ConnectDateUtils.formatDateDayMonthYear;
import static org.commcare.connect.ConnectDateUtils.shouldShowDateInRed;

import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.commcare.activities.connect.ConnectActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectJobDetailBottomSheetDialogBinding;
import org.commcare.views.connect.CircleProgressBar;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ConnectJobDetailBottomSheetDialogFragment extends BottomSheetDialogFragment {
    private FragmentConnectJobDetailBottomSheetDialogBinding binding;

    @Override
    public void onViewCreated(@NotNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.post(() -> {
            BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
            if (dialog != null) {
                FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (bottomSheet != null) {
                    BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
                    layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    bottomSheet.setLayoutParams(layoutParams);
                    bottomSheet.setBackground(new ColorDrawable(ContextCompat.getColor(requireContext(), R.color.transparent)));
                }
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentConnectJobDetailBottomSheetDialogBinding.inflate(inflater, container, false);
        binding.getRoot().setBackgroundResource(R.drawable.rounded_top_corners);

        ConnectJobRecord job = ((ConnectActivity)requireActivity()).getActiveJob();
        Objects.requireNonNull(job);

        binding.tvOpportunityName.setText(job.getTitle());
        boolean isCompleted = job.isFinished();
        int dateRes = isCompleted
                ? R.string.connect_expired_expired_on
                : R.string.connect_complete_by;
        binding.tvDate.setText(binding.tvDate.getContext().getString(dateRes, formatDateDayMonthYear(job.getProjectEndDate())));
        if (shouldShowDateInRed(job.getProjectEndDate())) {
            int redColor = ContextCompat.getColor(binding.tvDate.getContext(), R.color.dark_red_brick_red);

            binding.tvDate.setTextColor(redColor);
            binding.ivInfo.setColorFilter(redColor, PorterDuff.Mode.SRC_IN);
        }
        binding.connectDeliveryTotalVisitsText.setText(getString(R.string.connect_job_info_visit,
                job.getMaxPossibleVisits()));
        binding.connectDeliveryDaysText.setText(getString(R.string.connect_job_info_days,
                job.getDaysRemaining()));
        binding.connectDeliveryMaxDailyText.setText(getString(R.string.connect_job_info_max_visit,
                job.getMaxDailyVisits()));
        binding.connectDeliveryBudgetText.setText(buildPaymentText(job));
        binding.imgCloseDialog.setOnClickListener(view -> dismiss());
        handleProgressBarUI(job);
        return binding.getRoot();
    }
    public void handleProgressBarUI(ConnectJobRecord job) {

        applyProgressStep(
                binding.includeJobProgress.pbNewOpp,
                binding.includeJobProgress.ivNewOpp,
                true,
                100,
                ContextCompat.getColor(requireContext(), R.color.personal_id_work_history_yellow_pending),
                R.drawable.ic_connect_new_opportunity,
                R.drawable.ic_disabled_new_opportunity
        );

        boolean learnEnabled = job.getStatus() >= ConnectJobRecord.STATUS_LEARNING;
        applyProgressStep(
                binding.includeJobProgress.pbLearn,
                binding.includeJobProgress.ivLearn,
                learnEnabled,
                job.getLearningCompletePercentage(),
                ContextCompat.getColor(requireContext(), R.color.violet_blue),
                R.drawable.ic_connect_learning,
                R.drawable.ic_disabled_learn
        );

        boolean reviewEnabled = job.passedAssessment();
        applyProgressStep(
                binding.includeJobProgress.pbReview,
                binding.includeJobProgress.ivReview,
                reviewEnabled,
                reviewEnabled ? 100 : 0,
                ContextCompat.getColor(requireContext(), R.color.violet),
                R.drawable.ic_enabled_review,
                R.drawable.ic_disabled_review
        );

        boolean deliveryEnabled = job.getStatus() == ConnectJobRecord.STATUS_DELIVERING;
        applyProgressStep(
                binding.includeJobProgress.pbDelivery,
                binding.includeJobProgress.ivDelivery,
                deliveryEnabled,
                job.getDeliveryProgressPercentage(),
                ContextCompat.getColor(requireContext(), R.color.cyan),
                R.drawable.ic_enabled_delivery,
                R.drawable.ic_disabled_delivery
        );
    }

    private void applyProgressStep(
            CircleProgressBar progressBar,
            ImageView icon,
            boolean enabled,
            int progress,
            int color,
            int enabledIcon,
            int disabledIcon
    ) {
        if (enabled) {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(progress);
            progressBar.setProgressColor(color);
            icon.setImageResource(enabledIcon);

            int padding = dpToPx( 6);
            icon.setPadding(padding, padding, padding, padding);

            setIconSize(icon, 32);
        } else {
            progressBar.setVisibility(View.INVISIBLE);
            icon.setImageResource(disabledIcon);
            setIconSize(icon, 45);
        }
    }
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void setIconSize(ImageView icon, int sizeDp) {
        ViewGroup.LayoutParams params = icon.getLayoutParams();
        int sizePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                sizeDp,
                icon.getResources().getDisplayMetrics()
        );
        params.width = sizePx;
        params.height = sizePx;
        icon.setLayoutParams(params);
    }


    private String buildPaymentText(ConnectJobRecord job) {
        StringBuilder paymentTextBuilder = new StringBuilder();

        if (job.isMultiPayment()) {
            paymentTextBuilder.append(getString(R.string.connect_delivery_earn_multi));
            for (ConnectPaymentUnitRecord unit : job.getPaymentUnits()) {
                paymentTextBuilder.append(String.format("\n• %s: %s", unit.getName(),
                        job.getMoneyString(unit.getAmount())));
            }
        } else if (!job.getPaymentUnits().isEmpty()) {
            String moneyValue = job.getMoneyString(job.getPaymentUnits().get(0).getAmount());
            paymentTextBuilder.append(getString(R.string.connect_job_info_visit_charge, moneyValue));
        }
        return paymentTextBuilder.toString();
    }
}
