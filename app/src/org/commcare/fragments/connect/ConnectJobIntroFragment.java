package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.network.ApiConnect;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

/**
 * Fragment for showing detailed info about an available job
 *
 * @author dviggiano
 */
public class ConnectJobIntroFragment extends Fragment {
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
        ConnectJobRecord job = ConnectManager.getActiveJob();

        getActivity().setTitle(getString(R.string.connect_job_intro_title));

        View view = inflater.inflate(R.layout.fragment_connect_job_intro, container, false);

        TextView textView = view.findViewById(R.id.connect_job_intro_title);
        TextView payText = view.findViewById(R.id.connect_job_pay_title);
        TextView endDate = view.findViewById(R.id.connect_job_end_date);
        textView.setText(job.getTitle());
        endDate.setText( String.format(Locale.getDefault(), getString(R.string.connect_end_date), ConnectNetworkHelper.convertDateToLocalFormat(job.getProjectEndDate())));

        String visitPayment = job.getMoneyString(job.getTotalBudget());
        String fullDescription =  job.getDescription();

        textView = view.findViewById(R.id.connect_job_intro_description);
        payText.setText(job.getCurrency()+" "+job.getBudgetPerVisit());
        textView.setText(fullDescription);

        int totalHours = 0;
        List<String> lines = new ArrayList<>();
        List<ConnectLearnModuleSummaryRecord> modules = job.getLearnAppInfo().getLearnModules();
        for (int i = 0; i < modules.size(); i++) {
            lines.add(String.format(Locale.getDefault(), "%d. %s", (i + 1), modules.get(i).getName()));
            totalHours += modules.get(i).getTimeEstimate();
        }

        String toLearn = modules.size() > 0 ? String.join("\r\n\r\n", lines) : getString(R.string.connect_job_no_learning_required);

        textView = view.findViewById(R.id.connect_job_intro_learning);
        textView.setText(toLearn);

        textView = view.findViewById(R.id.connect_job_intro_learning_summary);
        textView.setText(getString(R.string.connect_job_learn_summary, modules.size(), totalHours));

        final boolean appInstalled = ConnectManager.isAppInstalled(job.getLearnAppInfo().getAppId());

        Button button = view.findViewById(R.id.connect_job_intro_start_button);
        button.setVisibility(showLaunchButton ? View.VISIBLE : View.GONE);
        if(showLaunchButton) {
            button.setText(getString(appInstalled ? R.string.connect_job_go_to_learn_app : R.string.download_app));
            button.setOnClickListener(v -> {
                //First, need to tell Connect we're starting learning so it can create a user on HQ
                ApiConnect.startLearnApp(getContext(), job.getJobId(), new IApiCallback() {
                    @Override
                    public void processSuccess(int responseCode, InputStream responseData) {
                        reportApiCall(true);
                        //TODO: Expecting to eventually get HQ username from server here

                        job.setStatus(ConnectJobRecord.STATUS_LEARNING);
                        ConnectDatabaseHelper.upsertJob(getContext(), job);

                        NavDirections directions;
                        if (appInstalled) {
                            directions = ConnectJobIntroFragmentDirections.actionConnectJobIntroFragmentToConnectJobLearningProgressFragment();
                        } else {
                            String title = getString(R.string.connect_downloading_learn);
                            directions = ConnectJobIntroFragmentDirections.actionConnectJobIntroFragmentToConnectDownloadingFragment(title, true, false);
                        }

                        Navigation.findNavController(button).navigate(directions);
                    }

                    @Override
                    public void processFailure(int responseCode, IOException e) {
                        Toast.makeText(getContext(), "Connect: error starting learning", Toast.LENGTH_SHORT).show();
                        reportApiCall(false);
                        //TODO: Log the message from the server
                    }

                    @Override
                    public void processNetworkFailure() {
                        ConnectNetworkHelper.showNetworkError(getContext());
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

        return view;
    }

    private void reportApiCall(boolean success) {
        FirebaseAnalyticsUtil.reportCccApiStartLearning(success);
    }
}
