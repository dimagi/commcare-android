package org.commcare.fragments.connect;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.connect.ConnectManager;
import org.commcare.activities.connect.ConnectNetworkHelper;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.MultipleAppsUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fragment for showing detailed info about an available job
 *
 * @author dviggiano
 */
public class ConnectJobIntroFragment extends Fragment {
    public ConnectJobIntroFragment() {
        // Required empty public constructor
    }

    public static ConnectJobIntroFragment newInstance() {
        return new ConnectJobIntroFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ConnectJobRecord job = ConnectJobIntroFragmentArgs.fromBundle(getArguments()).getJob();
        getActivity().setTitle(job.getTitle());

        View view = inflater.inflate(R.layout.fragment_connect_job_intro, container, false);

        TextView textView = view.findViewById(R.id.connect_job_intro_title);
        textView.setText(job.getTitle());

        String visitPayment = job.getMoneyString(job.getTotalBudget());
        String fullDescription = String.format(Locale.getDefault(), getString(R.string.connect_job_full_description), job.getDescription(), visitPayment);

        textView = view.findViewById(R.id.connect_job_intro_description);
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
        button.setText(getString(appInstalled ? R.string.connect_job_go_to_learn_app : R.string.connect_job_download_learn_app));
        button.setOnClickListener(v -> {
            //First, need to tell Connect we're starting learning so it can create a user on HQ
            ConnectNetworkHelper.startLearnApp(getContext(), job.getJobId(), new ConnectNetworkHelper.INetworkResultHandler() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    reportApiCall(true);
                    //TODO: Expecting to eventually get HQ username from server here
                    NavDirections directions;
                    if (appInstalled) {
                        directions = ConnectJobIntroFragmentDirections.actionConnectJobIntroFragmentToConnectJobLearningProgressFragment(job);
                    } else {
                        String title = getString(R.string.connect_downloading_learn);
                        directions = ConnectJobIntroFragmentDirections.actionConnectJobIntroFragmentToConnectDownloadingFragment(title, true, false, job);
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
                    Toast.makeText(getContext(), getString(R.string.recovery_network_unavailable),
                            Toast.LENGTH_SHORT).show();
                    reportApiCall(false);
                }
            });
        });

        return view;
    }

    private void reportApiCall(boolean success) {
        FirebaseAnalyticsUtil.reportCccApiStartLearning(success);
    }
}
