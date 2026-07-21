package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

import org.commcare.AppUtils;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectAppLaunchController;
import org.commcare.connect.ConnectDateUtils;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.connect.ConnectApiHandler;
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectJobIntroBinding;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Fragment for showing detailed info about an available job
 *
 * @author dviggiano
 */
public class ConnectJobIntroFragment extends ConnectJobFragment<FragmentConnectJobIntroBinding> {

    public ConnectJobIntroFragment() {
        // Required empty public constructor
    }

    @Override
    public @NotNull View onCreateView(@NotNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        requireActivity().setTitle(R.string.connect_job_info_view_opportunity);

        ActionBar actionBar = ((AppCompatActivity)requireActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_connect_close);
        }

        getBinding().tvJobTitle.setText(job.getTitle());
        getBinding().tvJobDescription.setText(job.getDescription());
        getBinding().tvExpiryValue.setText(ConnectDateUtils.formatShortDate(job.getProjectEndDate()));
        getBinding().tvMaxEarningsValue.setText(job.getMoneyStringWithSymbol(job.getTotalBudget()));

        getBinding().btnStart.setOnClickListener(v -> startLearning());

        populateLearnCard();
        populateDeliveryCards();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ActionBar actionBar = ((AppCompatActivity)requireActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(0);
        }
    }

    private void populateLearnCard() {
        List<ConnectLearnModuleSummaryRecord> modules = job.getLearnAppInfo().getLearnModules();
        int totalHours = 0;
        for (ConnectLearnModuleSummaryRecord module : modules) {
            totalHours += module.getTimeEstimate();
        }

        getBinding().cardLearnModules.setValueText(String.valueOf(modules.size()));
        getBinding().cardLearnModules.setSubtitleText(
                getString(R.string.connect_opportunity_learn_hours_total, totalHours));
        getBinding().cardLearnModules.setOnCardClick(() -> {
            NavHostFragment.findNavController(this).navigate(
                    ConnectJobIntroFragmentDirections
                            .actionConnectJobIntroFragmentToConnectLearnModulesBottomSheet());
            return null;
        });
    }

    private void populateDeliveryCards() {
        getBinding().cardMaxVisits.setValueText(String.valueOf(job.getMaxPossibleVisits()));
        getBinding().cardMaxVisits.setSubtitleText(
                getString(R.string.connect_opportunity_visits_per_day, job.getMaxDailyVisits()));

        getBinding().cardDays.setValueText(String.valueOf(job.getDaysRemaining()));

        getBinding().cardMaxEarnings.setValueText(job.getMoneyStringWithSymbol(job.getTotalBudget()));
        getBinding().cardMaxEarnings.setSubtitleText(
                getString(R.string.connect_opportunity_payment_units, job.getPaymentUnits().size()));
    }

    private void startLearning() {
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getContext());

        new ConnectApiHandler<Boolean>() {
            @Override
            public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes errorCode, @Nullable Throwable t) {
                reportApiCall(false);
                if (!isAdded()) {
                    return;
                }

                String error = PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), errorCode, t);
                if (PersonalIdOrConnectApiErrorHandler.isNetworkError(errorCode)) {
                    showError(getString(R.string.failed_to_start_learning));
                } else {
                    navigateToMessageDisplayDialog(
                            getString(R.string.error),
                            error,
                            false,
                            R.string.ok);
                }
            }

            @Override
            public void onSuccess(Boolean success) {
                hideError();
                reportApiCall(success);

                job.setStatus(ConnectJobRecord.STATUS_LEARNING);
                ConnectJobUtils.upsertJob(getContext(), job);

                if (!isAdded()) {
                    return;
                }

                String appId = job.getLearnAppInfo().getAppId();
                boolean appInstalled = AppUtils.isAppInstalled(appId);
                if (appInstalled) {
                    new ConnectAppLaunchController(ConnectJobIntroFragment.this)
                            .launchApp(appId, true, ConnectJobIntroFragment.this::popSelfOnceHidden);
                } else {
                    String title = getString(R.string.connect_downloading_learn);
                    NavHostFragment.findNavController(ConnectJobIntroFragment.this).navigate(
                            ConnectJobIntroFragmentDirections.
                                    actionConnectJobIntroFragmentToConnectDownloadingFragment(
                                            title, true));
                }
            }

        }.connectStartLearning(requireContext(), user, job.getJobUUID());
    }

    private void reportApiCall(boolean success) {
        FirebaseAnalyticsUtil.reportCccApiStartLearning(success);
    }

    @Override
    protected @NotNull FragmentConnectJobIntroBinding inflateBinding(@NotNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return FragmentConnectJobIntroBinding.inflate(inflater, container, false);
    }

    private void navigateToMessageDisplayDialog(@Nullable String title, @Nullable String message, boolean isCancellable, int buttonText) {
        NavDirections navDirections = ConnectJobIntroFragmentDirections.actionConnectJobIntroFragmentToPersonalidMessageDisplayDialog(
                title, message, getString(buttonText), null).setIsCancellable(isCancellable);
        NavHostFragment.findNavController(this).navigate(navDirections);
    }
}
