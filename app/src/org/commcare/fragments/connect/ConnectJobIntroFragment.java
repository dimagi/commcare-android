package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.navigation.Navigation;

import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.network.ApiConnect;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
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
    private boolean showLaunchButton = true;

    public ConnectJobIntroFragment() {
        // Required empty public constructor
    }

    public static ConnectJobIntroFragment newInstance(boolean showLaunchButton) {
        ConnectJobIntroFragment fragment = new ConnectJobIntroFragment();
        fragment.showLaunchButton = showLaunchButton;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getActivity().setTitle(getString(R.string.connect_job_intro_title));

        View view = inflater.inflate(R.layout.fragment_connect_job_intro, container, false);


        int totalHours = 0;
        List<String> lines = new ArrayList<>();
        List<ConnectLearnModuleSummaryRecord> modules = job.getLearnAppInfo().getLearnModules();
        for (int i = 0; i < modules.size(); i++) {
            lines.add(String.format(Locale.getDefault(), "%d. %s", (i + 1), modules.get(i).getName()));
            totalHours += modules.get(i).getTimeEstimate();
        }

        String toLearn = modules.size() > 0 ? String.join("\r\n\r\n", lines) : getString(R.string.connect_job_no_learning_required);

        TextView textView = view.findViewById(R.id.connect_job_intro_learning);
        textView.setText(toLearn);

        textView = view.findViewById(R.id.connect_job_intro_learning_summary);
        textView.setText(getString(R.string.connect_job_learn_summary, modules.size(), totalHours));

        final boolean appInstalled = ConnectManager.isAppInstalled(job.getLearnAppInfo().getAppId());

        Button button = view.findViewById(R.id.connect_job_intro_start_button);
        button.setVisibility(showLaunchButton ? View.VISIBLE : View.GONE);
        if (showLaunchButton) {
            button.setText(getString(appInstalled ? R.string.connect_job_go_to_learn_app : R.string.download_app));
            button.setOnClickListener(v -> {
                //First, need to tell Connect we're starting learning so it can create a user on HQ
                ConnectUserRecord user = ConnectManager.getUser(getContext());
                ApiConnect.startLearnApp(getContext(), user, job.getJobId(), new IApiCallback() {
                    @Override
                    public void processSuccess(int responseCode, InputStream responseData) {
                        reportApiCall(true);
                        //TODO: Expecting to eventually get HQ username from server here

                        job.setStatus(ConnectJobRecord.STATUS_LEARNING);
                        ConnectJobUtils.upsertJob(getContext(), job);

                        if (appInstalled) {
                            ConnectManager.launchApp(getActivity(), true, job.getLearnAppInfo().getAppId());
                        } else {
                            String title = getString(R.string.connect_downloading_learn);
                            Navigation.findNavController(button).navigate(ConnectJobIntroFragmentDirections.
                                    actionConnectJobIntroFragmentToConnectDownloadingFragment(title, true));
                        }
                    }

                    @Override
                    public void processFailure(int responseCode, @Nullable InputStream errorResponse) {
                        Toast.makeText(getContext(), "Connect: error starting learning", Toast.LENGTH_SHORT).show();
                        reportApiCall(false);
                    }

                    @Override
                    public void processNetworkFailure() {
                        ConnectNetworkHelper.showNetworkError(getContext());
                        reportApiCall(false);
                    }

                    @Override
                    public void processTokenUnavailableError() {
                        ConnectNetworkHelper.handleTokenUnavailableException(requireContext());
                        reportApiCall(false);
                    }

                    @Override
                    public void processTokenRequestDeniedError() {
                        ConnectNetworkHelper.handleTokenDeniedException();
                        reportApiCall(false);
                    }

                    @Override
                    public void processOldApiError() {
                        ConnectNetworkHelper.showOutdatedApiError(getContext());
                        reportApiCall(false);
                    }
                });
            });
        }

        jobCardDataHandle(view, job);
        return view;
    }

    private void jobCardDataHandle(View view, ConnectJobRecord job) {
        View viewJobCard = view.findViewById(R.id.viewJobCard);
        TextView viewMore = viewJobCard.findViewById(R.id.tv_view_more);
        TextView tvJobTitle = viewJobCard.findViewById(R.id.tv_job_title);
        TextView hoursTitle = viewJobCard.findViewById(R.id.tvDailyVisitTitle);
        TextView tv_job_time = viewJobCard.findViewById(R.id.tv_job_time);
        TextView tvJobDescription = viewJobCard.findViewById(R.id.tv_job_description);
        TextView connectJobEndDate = viewJobCard.findViewById(R.id.connect_job_end_date);

        viewMore.setOnClickListener(view1 -> {
            Navigation.findNavController(viewMore).navigate(ConnectJobIntroFragmentDirections.actionConnectJobIntroFragmentToConnectJobDetailBottomSheetDialogFragment());
        });

        tvJobTitle.setText(job.getTitle());
        tvJobDescription.setText(job.getDescription());
        connectJobEndDate.setText(getString(R.string.connect_learn_complete_by, ConnectManager.formatDate(job.getProjectEndDate())));

        String workingHours = job.getWorkingHours();
        boolean showHours = workingHours != null;
        tv_job_time.setVisibility(showHours ? View.VISIBLE : View.GONE);
        hoursTitle.setVisibility(showHours ? View.VISIBLE : View.GONE);
        if(showHours) {
            tv_job_time.setText(workingHours);
        }
    }

    private <T extends View> T findView(View parent, int id) {
        return parent.findViewById(id);
    }

    private void setText(TextView textView, String text) {
        textView.setText(text);
    }

    private void setVisibility(View view, int visibility) {
        view.setVisibility(visibility);
    }

    private void reportApiCall(boolean success) {
        FirebaseAnalyticsUtil.reportCccApiStartLearning(success);
    }
}
