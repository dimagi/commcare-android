package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;

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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fragment for showing detailed info about an available job
 *
 * @author dviggiano
 */
public class ConnectJobIntroFragment extends ConnectJobFragment {
    private FragmentConnectJobIntroBinding binding;

    public ConnectJobIntroFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        requireActivity().setTitle(getString(R.string.connect_job_intro_title));
        binding = FragmentConnectJobIntroBinding.inflate(inflater, container, false);

        int totalHours = 0;
        List<String> lines = new ArrayList<>();
        List<ConnectLearnModuleSummaryRecord> modules = job.getLearnAppInfo().getLearnModules();
        for (int i = 0; i < modules.size(); i++) {
            lines.add(String.format(Locale.getDefault(), "%d. %s", (i + 1), modules.get(i).getName()));
            totalHours += modules.get(i).getTimeEstimate();
        }

        String toLearn = modules.isEmpty() ? getString(R.string.connect_job_no_learning_required) :
                String.join("\r\n\r\n", lines);

        binding.connectJobIntroLearning.setText(toLearn);

        binding.connectJobIntroLearningSummary.setText(getString(R.string.connect_job_learn_summary,
                modules.size(), totalHours));

        final boolean appInstalled = ConnectAppUtils.INSTANCE.isAppInstalled(job.getLearnAppInfo().getAppId());

        binding.connectJobIntroStartButton.setText(getString(appInstalled ? R.string.connect_job_go_to_learn_app
                : R.string.download_app));
        binding.connectJobIntroStartButton.setOnClickListener(v -> {
            //First, need to tell Connect we're starting learning so it can create a user on HQ
            startLearning(appInstalled);
        });

        jobCardDataHandle(job);

        return binding.getRoot();
    }

    private void jobCardDataHandle(ConnectJobRecord job) {
        binding.viewJobCard.tvViewMore.setOnClickListener(view1 -> {
            Navigation.findNavController(binding.viewJobCard.tvViewMore).navigate(
                    ConnectJobIntroFragmentDirections
                            .actionConnectJobIntroFragmentToConnectJobDetailBottomSheetDialogFragment());
        });

        binding.viewJobCard.tvJobTitle.setText(job.getTitle());
        binding.viewJobCard.tvJobDescription.setText(job.getDescription());
        binding.viewJobCard.connectJobEndDate.setText(getString(R.string.connect_learn_complete_by,
                ConnectDateUtils.INSTANCE.formatDate(job.getProjectEndDate())));

        String workingHours = job.getWorkingHours();
        boolean showHours = workingHours != null;
        binding.viewJobCard.tvJobTime.setVisibility(showHours ? View.VISIBLE : View.GONE);
        binding.viewJobCard.tvDailyVisitTitle.setVisibility(showHours ? View.VISIBLE : View.GONE);
        if(showHours) {
            binding.viewJobCard.tvJobTime.setText(workingHours);
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
                    Navigation.findNavController(binding.connectJobIntroStartButton).navigate(
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
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
