package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.database.connect.models.ConnectJob;
import org.commcare.android.database.connect.models.ConnectJobLearningModule;
import org.commcare.dalvik.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
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

        String status;
        int completed = 0;
        for (ConnectJobLearningModule module: job.getLearningModules()) {
            if(module.getCompletedDate() != null) {
                completed++;
            }
        }

        int numModules = job.getLearningModules().length;
        int percent = numModules > 0 ? (100 * completed / numModules) : 100;
        if(percent > 0) {
            status = getString(R.string.connect_learn_status, completed, job.getLearningModules().length);
        }
        else {
            status = getString(R.string.connect_learn_not_started);
        }

        TextView textView = view.findViewById(R.id.connect_learning_status_text);
        textView.setText(status);

        textView = view.findViewById(R.id.connect_learning_complete_by_text);
        DateFormat df = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        textView.setText(getString(R.string.connect_learn_complete_by, df.format(job.getLearnDeadline())));

        Button button = view.findViewById(R.id.connect_learning_button);
        button.setText(percent > 0 ?
                getString(R.string.connect_learn_continue) :
                getString(R.string.connect_learn_start));
        button.setOnClickListener(v -> Toast.makeText(getContext(), "Not ready yet...", Toast.LENGTH_SHORT).show());

        return view;
    }
}
