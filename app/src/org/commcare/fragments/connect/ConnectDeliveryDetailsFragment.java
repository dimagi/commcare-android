package org.commcare.fragments.connect;

import static org.commcare.utils.ViewUtils.showSnackBarWithDismissAction;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.connect.ConnectApiHandler;
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectDeliveryDetailsBinding;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.jetbrains.annotations.NotNull;

public class ConnectDeliveryDetailsFragment extends ConnectJobFragment<FragmentConnectDeliveryDetailsBinding> {

    public static ConnectDeliveryDetailsFragment newInstance() {
        return new ConnectDeliveryDetailsFragment();
    }

    @Override
    public @NotNull View onCreateView(@NotNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        ConnectDeliveryDetailsFragmentArgs args = ConnectDeliveryDetailsFragmentArgs.fromBundle(getArguments());
        requireActivity().setTitle(getString(R.string.connect_job_info_title));
        setupJobDetailsUI(job);
        setupButtonBehavior(job, args.getIsButtonVisible());
        return view;
    }

    private void setupJobDetailsUI(ConnectJobRecord job) {
        getBinding().connectDeliveryTotalVisitsText.setText(getString(R.string.connect_job_info_visit, job.getMaxPossibleVisits()));
        getBinding().connectDeliveryDaysText.setText(getString(R.string.connect_job_info_days, job.getDaysRemaining()));
        getBinding().connectDeliveryMaxDailyText.setText(getString(R.string.connect_job_info_max_visit, job.getMaxDailyVisits()));
        getBinding().connectDeliveryBudgetText.setText(buildPaymentInfoText(job));
    }

    private String buildPaymentInfoText(ConnectJobRecord job) {
        if (job.isMultiPayment()) {
            StringBuilder paymentText = new StringBuilder(getString(R.string.connect_delivery_earn_multi));
            for (ConnectPaymentUnitRecord unit : job.getPaymentUnits()) {
                paymentText.append(String.format("\n• %s: %s", unit.getName(), job.getMoneyString(unit.getAmount())));
            }
            return paymentText.toString();
        } else if (!job.getPaymentUnits().isEmpty()) {
            return getString(R.string.connect_job_info_visit_charge,
                    job.getMoneyString(job.getPaymentUnits().get(0).getAmount()));
        }
        return "";
    }

    private void setupButtonBehavior(ConnectJobRecord job, boolean isButtonVisible) {
        boolean jobClaimed = job.getStatus() == ConnectJobRecord.STATUS_DELIVERING;
        boolean appInstalled = isDeliveryAppInstalled(job);

        int buttonTextId = jobClaimed
                ? (appInstalled ? R.string.connect_delivery_go : R.string.connect_job_info_download)
                : R.string.connect_job_info_download;

        getBinding().cardButtonLayout.setVisibility(isButtonVisible ? View.VISIBLE : View.GONE);
        getBinding().connectDeliveryButton.setText(buttonTextId);

        getBinding().connectDeliveryButton.setOnClickListener(v -> {
            if (jobClaimed) {
                proceedAfterJobClaimed(getBinding().connectDeliveryButton, job, appInstalled);
            } else {
                claimJob(job, appInstalled);
            }
        });
    }

    private boolean isDeliveryAppInstalled(ConnectJobRecord job) {
        return AppUtils.isAppInstalled(job.getDeliveryAppInfo().getAppId());
    }

    private void claimJob(ConnectJobRecord job, boolean appInstalled) {
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getContext());

        new ConnectApiHandler<Boolean>() {

            @Override
            public void onSuccess(Boolean data) {
                proceedAfterJobClaimed(getBinding().connectDeliveryButton, job, appInstalled);
                FirebaseAnalyticsUtil.reportCccApiClaimJob(true);
            }

            @Override
            public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes errorCode, @Nullable Throwable t) {
                String message;
                if (errorCode == PersonalIdOrConnectApiErrorCodes.BAD_REQUEST_ERROR) {
                    message = getString(R.string.recovery_unable_to_claim_opportunity);
                } else {
                    message = PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), errorCode, t);
                }
                showSnackBarWithDismissAction(getBinding().getRoot(), message);
                FirebaseAnalyticsUtil.reportCccApiClaimJob(false);
            }
        }.claimJob(requireContext(), user, job.getJobId());
    }

    private void proceedAfterJobClaimed(Button button, ConnectJobRecord job, boolean installed) {
        job.setStatus(ConnectJobRecord.STATUS_DELIVERING);
        ConnectJobUtils.upsertJob(getContext(), job);
        CommCareApplication.instance().closeUserSession();

        NavDirections directions = installed
                ? navigateToDeliverProgress()
                : navigateToDownloadingPage();

        Navigation.findNavController(button).navigate(directions);
    }

    private NavDirections navigateToDeliverProgress() {
        return ConnectDeliveryDetailsFragmentDirections.actionConnectJobDeliveryDetailsFragmentToConnectJobDeliveryProgressFragment();
    }

    private NavDirections navigateToDownloadingPage() {
        return ConnectDeliveryDetailsFragmentDirections.actionConnectJobDeliveryDetailsFragmentToConnectDownloadingFragment(
                getString(R.string.connect_downloading_delivery), false);
    }

    @Override
    protected @NotNull FragmentConnectDeliveryDetailsBinding inflateBinding(@NotNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return FragmentConnectDeliveryDetailsBinding.inflate(inflater, container, false);
    }
}
