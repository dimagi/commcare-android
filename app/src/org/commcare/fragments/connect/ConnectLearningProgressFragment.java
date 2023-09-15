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

import org.commcare.activities.connect.ConnectIdManager;
import org.commcare.android.database.connect.models.ConnectJob;
import org.commcare.android.database.connect.models.ConnectJobLearningModule;
import org.commcare.dalvik.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.fragment.app.Fragment;

/**
 * Fragment for showing learning progress for a Connect job
 *
 * @author dviggiano
 */
public class ConnectLearningProgressFragment extends Fragment {
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
        ConnectJob job = ConnectLearningProgressFragmentArgs.fromBundle(getArguments()).getJob();
        getActivity().setTitle(job.getTitle());

        View view = inflater.inflate(R.layout.fragment_connect_learning_progress, container, false);

        int completed = 0;
        for (ConnectJobLearningModule module: job.getLearningModules()) {
            if(module.getCompletedDate() != null) {
                completed++;
            }
        }

        int numModules = job.getLearningModules().length;
        int percent = numModules > 0 ? (100 * completed / numModules) : 100;
        boolean learningFinished = percent >= 100;

        String status;
        String buttonText;
        if (learningFinished) {
            status = getString(R.string.connect_learn_finished);
            buttonText = getString(R.string.connect_learn_start_claim);
        } else if(percent > 0) {
            status = getString(R.string.connect_learn_status, completed, job.getLearningModules().length);
            buttonText = getString(R.string.connect_learn_continue);
        } else {
            status = getString(R.string.connect_learn_not_started);
            buttonText = getString(R.string.connect_learn_start);
        }

        TextView progressText = view.findViewById(R.id.connect_learning_progress_text);
        progressText.setVisibility(learningFinished ? View.GONE : View.VISIBLE);
        ProgressBar progressBar = view.findViewById(R.id.connect_learning_progress_bar);
        progressBar.setVisibility(learningFinished ? View.GONE : View.VISIBLE);
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

            Date latestDate = null;
            for (ConnectJobLearningModule module : job.getLearningModules()) {
                if(latestDate == null || latestDate.before(module.getCompletedDate())) {
                    latestDate = module.getCompletedDate();
                }
            }

            textView = view.findViewById(R.id.connect_learn_cert_date);
            textView.setText(getString(R.string.connect_learn_completed, df.format(latestDate)));
        } else {
            completeByText.setText(getString(R.string.connect_learn_complete_by, df.format(job.getLearnDeadline())));
        }

        Button button = view.findViewById(R.id.connect_learning_button);
        button.setText(buttonText);
        button.setOnClickListener(v -> Toast.makeText(getContext(), "Not ready yet...", Toast.LENGTH_SHORT).show());

        return view;
    }
}
