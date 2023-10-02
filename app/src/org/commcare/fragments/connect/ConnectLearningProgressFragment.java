package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.connect.ConnectIdDatabaseHelper;
import org.commcare.activities.connect.ConnectIdManager;
import org.commcare.activities.connect.ConnectIdNetworkHelper;
import org.commcare.adapters.ConnectJobAdapter;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.commcaresupportlibrary.CommCareLauncher;
import org.commcare.dalvik.R;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

/**
 * Fragment for showing learning progress for a Connect job
 *
 * @author dviggiano
 */
public class ConnectLearningProgressFragment extends Fragment {
    private View view;
    private ConnectJobRecord job;

    public ConnectLearningProgressFragment() {
        // Required empty public constructor
    }

    public static ConnectLearningProgressFragment newInstance() {
        return new ConnectLearningProgressFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        job = ConnectLearningProgressFragmentArgs.fromBundle(getArguments()).getJob();
        getActivity().setTitle(job.getTitle());

        view = inflater.inflate(R.layout.fragment_connect_learning_progress, container, false);

        ImageView refreshButton = view.findViewById(R.id.connect_learning_refresh);
        refreshButton.setOnClickListener(v -> {
            refreshData();
        });

        updateUi(view, job);
        refreshData();

        return view;
    }

    private void refreshData() {
        ConnectIdNetworkHelper.getLearnProgress(getContext(), job.getJobId(), new ConnectIdNetworkHelper.INetworkResultHandler() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        //Parse the JSON
                        JSONArray json = new JSONArray(responseAsString);
//                        List<ConnectJobRecord> jobs = new ArrayList<>(json.length());
//                        for(int i=0; i<json.length(); i++) {
//                            JSONObject obj = (JSONObject)json.get(i);
//                            jobs.add(ConnectJobRecord.fromJson(obj));
//                        }

                        job.setComletedLearningModules(json.length());
                        ConnectIdDatabaseHelper.updateJobLearnProgress(getContext(), job);
                    }
                } catch (IOException | JSONException e) {
                    Logger.exception("Parsing return from learn_progress request", e);
                }

                updateUi(view, job);
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Logger.log("ERROR", String.format(Locale.getDefault(), "Failed: %d", responseCode));
            }

            @Override
            public void processNetworkFailure() {
                Logger.log("ERROR", "Failed (network)");
            }
        });
    }

    private void updateUi(View view, ConnectJobRecord job) {
        //NOTE: Leaving old logic here in case we go back to array
        int completed = job.getCompletedLearningModules();// 0;
//        for (ConnectJobLearningModule module: job.getLearningModules()) {
//            if(module.getCompletedDate() != null) {
//                completed++;
//            }
//        }

        updateUpdatedDate();

        int numModules = job.getNumLearningModules();// job.getLearningModules().length;
        int percent = numModules > 0 ? (100 * completed / numModules) : 100;
        boolean learningFinished = percent >= 100;

        String status;
        String buttonText;
        if (learningFinished) {
            status = getString(R.string.connect_learn_finished);
            buttonText = getString(R.string.connect_learn_view_details);
        } else if(percent > 0) {
            status = getString(R.string.connect_learn_status, completed, numModules);
            buttonText = getString(R.string.connect_learn_continue);
        } else {
            status = getString(R.string.connect_learn_not_started);
            buttonText = getString(R.string.connect_learn_start);
        }

        TextView progressText = view.findViewById(R.id.connect_learning_progress_text);
        progressText.setVisibility(learningFinished ? View.GONE : View.VISIBLE);
        ProgressBar progressBar = view.findViewById(R.id.connect_learning_progress_bar);
        progressBar.setVisibility(learningFinished ? View.GONE : View.VISIBLE);
        LinearLayout progressBarTextContainer = view.findViewById(R.id.connect_learn_progress_bar_text_container);
        progressBarTextContainer.setVisibility(learningFinished ? View.GONE : View.VISIBLE);
        if(!learningFinished) {
            progressBar.setProgress(percent);
            progressBar.setMax(100);

            progressText.setText(String.format(Locale.getDefault(), "%d%%", percent));
        }

        LinearLayout certContainer = view.findViewById(R.id.connect_learning_certificate_container);
        certContainer.setVisibility(learningFinished ? View.VISIBLE : View.GONE);

        TextView textView = view.findViewById(R.id.connect_learn_progress_title);
        textView.setText(getString(learningFinished ? R.string.connect_learn_complete_title :
                R.string.connect_learn_progress_title));

        textView = view.findViewById(R.id.connect_learning_claim_label);
        textView.setVisibility(learningFinished ? View.VISIBLE : View.GONE);

        textView = view.findViewById(R.id.connect_learning_status_text);
        textView.setText(status);

        TextView completeByText = view.findViewById(R.id.connect_learning_complete_by_text);
        completeByText.setVisibility(learningFinished ? View.GONE : View.VISIBLE);

        DateFormat df = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        if(learningFinished) {
            textView = view.findViewById(R.id.connect_learn_cert_subject);
            textView.setText(job.getTitle());

            textView = view.findViewById(R.id.connect_learn_cert_person);
            textView.setText(ConnectIdManager.getUser(getContext()).getName());

            //TODO DAV: get from server somehow
            Date latestDate = new Date();//null;
//            for (ConnectJobLearningModule module : job.getLearningModules()) {
//                if(latestDate == null || latestDate.before(module.getCompletedDate())) {
//                    latestDate = module.getCompletedDate();
//                }
//            }

            textView = view.findViewById(R.id.connect_learn_cert_date);
            textView.setText(getString(R.string.connect_learn_completed, df.format(latestDate)));
        } else {
            completeByText.setText(getString(R.string.connect_learn_complete_by, df.format(job.getProjectEndDate())));
        }

        final Button button = view.findViewById(R.id.connect_learning_button);
        button.setText(buttonText);
        button.setOnClickListener(v -> {
            if(learningFinished) {
                NavDirections directions = ConnectLearningProgressFragmentDirections.actionConnectJobLearningProgressFragmentToConnectJobDeliveryDetailsFragment(job);
                Navigation.findNavController(button).navigate(directions);
            }
            else {
                CommCareLauncher.launchCommCareForAppIdFromConnect(getContext(), job.getLearnAppInfo().getAppId());
            }
        });
    }

    private void updateUpdatedDate() {
        Date lastUpdate = new Date(); //TODO DAV: Determine last update date
        DateFormat df = SimpleDateFormat.getDateTimeInstance();
        TextView updateText = view.findViewById(R.id.connect_learning_last_update);
        updateText.setText(getString(R.string.connect_last_update, df.format(lastUpdate)));
    }
}
