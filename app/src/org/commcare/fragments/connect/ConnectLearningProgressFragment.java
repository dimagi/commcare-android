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

import org.commcare.activities.connect.ConnectDatabaseHelper;
import org.commcare.activities.connect.ConnectManager;
import org.commcare.activities.connect.ConnectNetworkHelper;
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord;
import org.commcare.android.database.connect.models.ConnectJobLearningRecord;
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

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

/**
 * Fragment for showing learning progress for a Connect job
 *
 * @author dviggiano
 */
public class ConnectLearningProgressFragment extends Fragment {
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

        View view = inflater.inflate(R.layout.fragment_connect_learning_progress, container, false);

        ImageView refreshButton = view.findViewById(R.id.connect_learning_refresh);
        refreshButton.setOnClickListener(v -> {
            refreshData();
        });

        updateUpdatedDate(ConnectDatabaseHelper.getLastLearnProgressUpdate(getContext()));
        updateUi();
        refreshData();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    private void refreshData() {
        ConnectNetworkHelper.getLearnProgress(getContext(), job.getJobId(), new ConnectNetworkHelper.INetworkResultHandler() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        //Parse the JSON
                        JSONObject json = new JSONObject(responseAsString);

                        String key = "completed_modules";
                        JSONArray modules = json.getJSONArray(key);
                        List<ConnectJobLearningRecord> learningRecords = new ArrayList<>(modules.length());
                        for(int i=0; i<modules.length(); i++) {
                            JSONObject obj = (JSONObject)modules.get(i);
                            ConnectJobLearningRecord record = ConnectJobLearningRecord.fromJson(obj, job.getJobId());
                            learningRecords.add(record);
                        }
                        job.setLearnings(learningRecords);

                        key = "assessments";
                        JSONArray assessments = json.getJSONArray(key);
                        List<ConnectJobAssessmentRecord> assessmentRecords = new ArrayList<>(assessments.length());
                        for(int i=0; i<assessments.length(); i++) {
                            JSONObject obj = (JSONObject)assessments.get(i);
                            ConnectJobAssessmentRecord record = ConnectJobAssessmentRecord.fromJson(obj, job.getJobId());
                            assessmentRecords.add(record);
                        }
                        job.setAssessments(assessmentRecords);

                        job.setComletedLearningModules(learningRecords.size());
                        ConnectDatabaseHelper.updateJobLearnProgress(getContext(), job);
                    }
                } catch (IOException | JSONException | ParseException e) {
                    Logger.exception("Parsing return from learn_progress request", e);
                }

                updateUpdatedDate(new Date());
                updateUi();
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

    private void updateUi() {
        View view = getView();
        if(view == null) {
            return;
        }

        //NOTE: Leaving old logic here in case we go back to array
        int completed = job.getCompletedLearningModules();
//        for (ConnectJobLearningModule module: job.getLearningModules()) {
//            if(module.getCompletedDate() != null) {
//                completed++;
//            }
//        }

        int numModules = job.getNumLearningModules();// job.getLearningModules().length;
        int percent = numModules > 0 ? (100 * completed / numModules) : 100;
        boolean learningFinished = percent >= 100;
        boolean assessmentAttempted = job.attemptedAssessment();
        boolean assessmentPassed = job.passedAssessment();

        String status;
        String buttonText;
        if (learningFinished) {
            if(assessmentAttempted) {
                if(assessmentPassed) {
                    status = getString(R.string.connect_learn_finished);
                    buttonText = getString(R.string.connect_learn_view_details);
                }
                else {
                    status = getString(R.string.connect_learn_failed);
                    buttonText = getString(R.string.connect_learn_try_again);
                }
            }
            else {
                status = getString(R.string.connect_learn_need_assessment);
                buttonText = getString(R.string.connect_learn_go_to_assessment);
            }
        } else if(percent > 0) {
            status = getString(R.string.connect_learn_status, completed, numModules);
            buttonText = getString(R.string.connect_learn_continue);
        } else {
            status = getString(R.string.connect_learn_not_started);
            buttonText = getString(R.string.connect_learn_start);
        }

        TextView progressText = view.findViewById(R.id.connect_learning_progress_text);
        ProgressBar progressBar = view.findViewById(R.id.connect_learning_progress_bar);
        LinearLayout progressBarTextContainer = view.findViewById(R.id.connect_learn_progress_bar_text_container);

        progressText.setVisibility(learningFinished ? View.GONE : View.VISIBLE);
        progressBar.setVisibility(learningFinished ? View.GONE : View.VISIBLE);
        progressBarTextContainer.setVisibility(learningFinished ? View.GONE : View.VISIBLE);
        if(!learningFinished) {
            progressBar.setProgress(percent);
            progressBar.setMax(100);

            progressText.setText(String.format(Locale.getDefault(), "%d%%", percent));
        }

        LinearLayout certContainer = view.findViewById(R.id.connect_learning_certificate_container);
        certContainer.setVisibility(learningFinished && assessmentPassed ? View.VISIBLE : View.GONE);

        int titleResource;
        if(learningFinished) {
            if(assessmentAttempted) {
                if(assessmentPassed) {
                    titleResource = R.string.connect_learn_complete_title;
                }
                else {
                    titleResource = R.string.connect_learn_failed_title;
                }
            }
            else {
                titleResource = R.string.connect_learn_need_assessment_title;
            }
        }
        else {
            titleResource = R.string.connect_learn_progress_title;
        }

        TextView textView = view.findViewById(R.id.connect_learn_progress_title);
        textView.setText(getString(titleResource));

        textView = view.findViewById(R.id.connect_learning_claim_label);
        textView.setVisibility(learningFinished && assessmentPassed ? View.VISIBLE : View.GONE);

        textView = view.findViewById(R.id.connect_learning_status_text);
        textView.setText(status);

        TextView completeByText = view.findViewById(R.id.connect_learning_complete_by_text);
        completeByText.setVisibility(learningFinished && assessmentPassed ? View.GONE : View.VISIBLE);

        DateFormat df = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        if(learningFinished) {
            textView = view.findViewById(R.id.connect_learn_cert_subject);
            textView.setText(job.getTitle());

            textView = view.findViewById(R.id.connect_learn_cert_person);
            textView.setText(ConnectManager.getUser(getContext()).getName());

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
            NavDirections directions = null;
            if(learningFinished && assessmentPassed) {
                directions = ConnectLearningProgressFragmentDirections.actionConnectJobLearningProgressFragmentToConnectJobDeliveryDetailsFragment(job);
            } else if(ConnectManager.isAppInstalled(job.getDeliveryAppInfo().getAppId())) {
                CommCareLauncher.launchCommCareForAppIdFromConnect(getContext(), job.getLearnAppInfo().getAppId());
            } else {
                String title = getString(R.string.connect_downloading_learn);
                directions = ConnectLearningProgressFragmentDirections.actionConnectJobLearningProgressFragmentToConnectDownloadingFragment(title, true, job);
            }

            if(directions != null) {
                Navigation.findNavController(button).navigate(directions);
            }
        });
    }

    private void updateUpdatedDate(Date lastUpdate) {
        View view = getView();
        if(view == null) {
            return;
        }

        DateFormat df = SimpleDateFormat.getDateTimeInstance();
        TextView updateText = view.findViewById(R.id.connect_learning_last_update);
        updateText.setText(getString(R.string.connect_last_update, df.format(lastUpdate)));
    }
}
