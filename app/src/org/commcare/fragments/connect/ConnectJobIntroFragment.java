package org.commcare.fragments.connect;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import org.commcare.AppUtils;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectAppUtils;
import org.commcare.connect.ConnectDateUtils;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.connect.ConnectApiHandler;
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectJobIntroBinding;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        getBinding().tvEndDate.setText(getString(R.string.connect_learn_complete_by,
                ConnectDateUtils.formatDate(job.getProjectEndDate())));

        getBinding().btnStart.setOnClickListener(v -> startLearning());

        populateLearnModules();
        populateDeliveryDetails();

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

    private void populateLearnModules() {
        List<String> lines = new ArrayList<>();
        List<ConnectLearnModuleSummaryRecord> modules = job.getLearnAppInfo().getLearnModules();
        for (int i = 0; i < modules.size(); i++) {
            lines.add(String.format(Locale.getDefault(), "%d. %s", (i + 1), modules.get(i).getName()));
        }

        String text = modules.isEmpty() ? getString(R.string.connect_job_no_learning_required) :
                TextUtils.join("\n\n", lines);
        getBinding().tvLearnModulesList.setText(text);
    }

    private void populateDeliveryDetails() {
        getBinding().connectDeliveryTotalVisitsText.setText(getString(R.string.connect_job_info_visit,
                job.getMaxPossibleVisits()));
        getBinding().connectDeliveryDaysText.setText(getString(R.string.connect_job_info_days,
                job.getDaysRemaining()));
        getBinding().connectDeliveryMaxDailyText.setText(getString(R.string.connect_job_info_max_visit,
                job.getMaxDailyVisits()));
        getBinding().connectDeliveryBudgetText.setText(buildPaymentText());
    }

    private String buildPaymentText() {
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

    private void startLearning() {
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getContext());

        new ConnectApiHandler<Boolean>() {
            @Override
            public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes errorCode, @Nullable Throwable t) {
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
                reportApiCall(false);
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
                    ConnectAppUtils.INSTANCE.launchApp(requireActivity(), true, appId);
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
