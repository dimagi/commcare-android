package org.commcare.fragments.connect;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;

import org.commcare.AppUtils;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectAppUtils;
import org.commcare.connect.ConnectDateUtils;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiConnect;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.connect.network.connect.ConnectApiHandler;
import org.commcare.connect.network.connectId.PersonalIdApiErrorHandler;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectJobIntroBinding;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
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
        requireActivity().setTitle(getString(R.string.connect_job_intro_title));

        int totalHours = getTotalLearningHours();
        String toLearn = getLearnModuleSummaryLines();

        getBinding().connectJobIntroLearning.setText(toLearn);

        getBinding().connectJobIntroLearningSummary.setText(getString(R.string.connect_job_learn_summary,
                job.getLearnAppInfo().getLearnModules().size(), totalHours));

        final boolean appInstalled = AppUtils.isAppInstalled(job.getLearnAppInfo().getAppId());

        getBinding().connectJobIntroStartButton.setText(getString(appInstalled ? R.string.connect_job_go_to_learn_app
                : R.string.download_app));
        getBinding().connectJobIntroStartButton.setOnClickListener(v -> {
            //First, need to tell Connect we're starting learning so it can create a user on HQ
            startLearning(appInstalled);
        });

        setupJobCard(job);
        return view;
    }

    private int getTotalLearningHours() {
        int totalHours = 0;
        List<ConnectLearnModuleSummaryRecord> modules = job.getLearnAppInfo().getLearnModules();
        for (int i = 0; i < modules.size(); i++) {
            totalHours += modules.get(i).getTimeEstimate();
        }

        return totalHours;
    }

    private String getLearnModuleSummaryLines() {
        List<String> lines = new ArrayList<>();
        List<ConnectLearnModuleSummaryRecord> modules = job.getLearnAppInfo().getLearnModules();
        for (int i = 0; i < modules.size(); i++) {
            lines.add(String.format(Locale.getDefault(), "%d. %s", (i + 1), modules.get(i).getName()));
        }

        return modules.isEmpty() ? getString(R.string.connect_job_no_learning_required) :
                TextUtils.join("\r\n\r\n", lines);
    }

    private void setupJobCard(ConnectJobRecord job) {
        getBinding().viewJobCard.tvViewMore.setOnClickListener(view1 -> {
            Navigation.findNavController(getBinding().viewJobCard.tvViewMore).navigate(
                    ConnectJobIntroFragmentDirections
                            .actionConnectJobIntroFragmentToConnectJobDetailBottomSheetDialogFragment());
        });

        getBinding().viewJobCard.tvJobTitle.setText(job.getTitle());
        getBinding().viewJobCard.tvJobDescription.setText(job.getDescription());
        getBinding().viewJobCard.connectJobEndDate.setText(getString(R.string.connect_learn_complete_by,
                ConnectDateUtils.INSTANCE.formatDate(job.getProjectEndDate())));

        String workingHours = job.getWorkingHours();
        boolean showHours = workingHours != null;
        getBinding().viewJobCard.tvJobTime.setVisibility(showHours ? View.VISIBLE : View.GONE);
        getBinding().viewJobCard.tvDailyVisitTitle.setVisibility(showHours ? View.VISIBLE : View.GONE);
        if(showHours) {
            getBinding().viewJobCard.tvJobTime.setText(workingHours);
        }
    }

    private void startLearning(boolean appInstalled) {
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getContext());
        new ConnectApiHandler<Boolean>(){

            @Override
            public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes errorCode, @Nullable Throwable t) {
                Toast.makeText(requireContext(), PersonalIdApiErrorHandler.handle(requireActivity(), errorCode, t),Toast.LENGTH_LONG).show();
                reportApiCall(false);
            }

            @Override
            public void onSuccess(Boolean success) {
                reportApiCall(success);

                job.setStatus(ConnectJobRecord.STATUS_LEARNING);
                ConnectJobUtils.upsertJob(getContext(), job);

                if (appInstalled) {
                    ConnectAppUtils.INSTANCE.launchApp(requireActivity(), true,
                            job.getLearnAppInfo().getAppId());
                } else {
                    String title = getString(R.string.connect_downloading_learn);
                    Navigation.findNavController(getBinding().connectJobIntroStartButton).navigate(
                            ConnectJobIntroFragmentDirections.
                                    actionConnectJobIntroFragmentToConnectDownloadingFragment(
                                            title, true));
                }

            }

        }.connectStartLearning(requireContext(), user, job.getJobId());
    }

    private void reportApiCall(boolean success) {
        FirebaseAnalyticsUtil.reportCccApiStartLearning(success);
    }

    @Override
    protected @NotNull FragmentConnectJobIntroBinding inflateBinding(@NotNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return FragmentConnectJobIntroBinding.inflate(inflater, container, false);
    }
}
