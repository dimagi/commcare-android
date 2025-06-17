package org.commcare.fragments.connect;

import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectJobDetailBottomSheetDialogBinding;
import org.jetbrains.annotations.NotNull;

public class ConnectJobDetailBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private FragmentConnectJobDetailBottomSheetDialogBinding binding;

    @Override
    public void onViewCreated(@NotNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
//        setStyle(STYLE_NORMAL, R.style.TransparentBottomSheet);
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        bottomSheet.setBackground(new ColorDrawable(getResources().getColor(R.color.transparent, null)));
                    }
                }
            }
        });
    }


    @Nullable
    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentConnectJobDetailBottomSheetDialogBinding.inflate(inflater, container, false);
        binding.getRoot().setBackgroundResource(R.drawable.rounded_top_corners);

        ConnectJobRecord job = ConnectManager.getActiveJob();
        int maxPossibleVisits = job.getMaxPossibleVisits();
        int daysRemaining = job.getDaysRemaining();

        binding.connectDeliveryTotalVisitsText.setText(getString(R.string.connect_job_info_visit, maxPossibleVisits));
        binding.connectDeliveryDaysText.setText(getString(R.string.connect_job_info_days, daysRemaining));
        binding.connectDeliveryMaxDailyText.setText(getString(R.string.connect_job_info_max_visit, job.getMaxDailyVisits()));

        String paymentText = buildPaymentText(job);
        binding.connectDeliveryBudgetText.setText(paymentText);

        binding.imgCloseDialog.setOnClickListener(view -> dismiss());
        return binding.getRoot();
    }

    private String buildPaymentText(ConnectJobRecord job) {
        StringBuilder paymentTextBuilder = new StringBuilder();

        if (job.isMultiPayment()) {
            paymentTextBuilder.append(getString(R.string.connect_delivery_earn_multi));
            for (ConnectPaymentUnitRecord unit : job.getPaymentUnits()) {
                paymentTextBuilder.append(String.format("\n\u2022 %s: %s", unit.getName(), job.getMoneyString(unit.getAmount())));
            }
        } else if (!job.getPaymentUnits().isEmpty()) {
            String moneyValue = job.getMoneyString(job.getPaymentUnits().get(0).getAmount());
            paymentTextBuilder.append(getString(R.string.connect_job_info_visit_charge, moneyValue));
        }
        return paymentTextBuilder.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
