package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiConnect;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectDeliveryDetailsBinding;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.MultipleAppsUtil;

import java.io.InputStream;

public class ConnectDeliveryDetailsFragment extends ConnectJobFragment {

    private FragmentConnectDeliveryDetailsBinding binding;

    public static ConnectDeliveryDetailsFragment newInstance() {
        return new ConnectDeliveryDetailsFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentConnectDeliveryDetailsBinding.inflate(inflater, container, false);

        ConnectDeliveryDetailsFragmentArgs args = ConnectDeliveryDetailsFragmentArgs.fromBundle(getArguments());

        requireActivity().setTitle(getString(R.string.connect_job_info_title));
        setupJobDetailsUI(job);
        setupButtonBehavior(job, args.getIsButtonVisible());

        return binding.getRoot();
    }

    private void setupJobDetailsUI(ConnectJobRecord job) {
        binding.connectDeliveryTotalVisitsText.setText(getString(R.string.connect_job_info_visit, job.getMaxPossibleVisits()));
        binding.connectDeliveryDaysText.setText(getString(R.string.connect_job_info_days, job.getDaysRemaining()));
        binding.connectDeliveryMaxDailyText.setText(getString(R.string.connect_job_info_max_visit, job.getMaxDailyVisits()));
        binding.connectDeliveryBudgetText.setText(buildPaymentInfoText(job));
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

        binding.cardButtonLayout.setVisibility(isButtonVisible ? View.VISIBLE : View.GONE);
        binding.connectDeliveryButton.setText(buttonTextId);

        binding.connectDeliveryButton.setOnClickListener(v -> {
            if (jobClaimed) {
                proceedAfterJobClaimed(binding.connectDeliveryButton, job, appInstalled);
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
        ApiConnect.claimJob(getContext(), user, job.getJobId(), new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                proceedAfterJobClaimed(binding.connectDeliveryButton, job, appInstalled);
                FirebaseAnalyticsUtil.reportCccApiClaimJob(true);
            }

            @Override
            public void processFailure(int responseCode, @Nullable InputStream errorResponse, String url) {
                Toast.makeText(getContext(), R.string.connect_claim_job_error, Toast.LENGTH_SHORT).show();
                FirebaseAnalyticsUtil.reportCccApiClaimJob(false);
            }

            @Override
            public void processNetworkFailure() {
                ConnectNetworkHelper.showNetworkError(getContext());
                FirebaseAnalyticsUtil.reportCccApiClaimJob(false);
            }

            @Override
            public void processTokenUnavailableError() {
                ConnectNetworkHelper.handleTokenUnavailableException(requireContext());
                FirebaseAnalyticsUtil.reportCccApiClaimJob(false);
            }

            @Override
            public void processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenDeniedException();
                FirebaseAnalyticsUtil.reportCccApiClaimJob(false);
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(getContext());
                FirebaseAnalyticsUtil.reportCccApiClaimJob(false);
            }
        });
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
}
