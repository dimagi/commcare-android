package org.commcare.fragments.connect;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.database.connect.models.ConnectJob;
import org.commcare.android.database.connect.models.ConnectJobLearningModule;
import org.commcare.dalvik.R;

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
        ConnectJob job = ConnectJobIntroFragmentArgs.fromBundle(getArguments()).getJob();
        getActivity().setTitle(job.getTitle());

        View view = inflater.inflate(R.layout.fragment_connect_job_intro, container, false);

        TextView textView = view.findViewById(R.id.connect_job_intro_title);
        textView.setText(job.getTitle());

        textView = view.findViewById(R.id.connect_job_intro_description);
        textView.setText(job.getDescription());

        int totalHours = 0;
        List<String> lines = new ArrayList<>();
        ConnectJobLearningModule[] modules = job.getLearningModules();
        for(int i=0; i<modules.length; i++) {
            lines.add(String.format(Locale.getDefault(), "%d. %s", (i+1), modules[i].getToLearn()));
            totalHours += modules[i].getEstimatedHours();
        }

        String toLearn = modules.length > 0 ? String.join("\r\n\r\n", lines) : getString(R.string.connect_job_no_learning_required);

        textView = view.findViewById(R.id.connect_job_intro_learning);
        textView.setText(toLearn);

        textView = view.findViewById(R.id.connect_job_intro_learning_summary);
        textView.setText(getString(R.string.connect_job_learn_summary, modules.length, totalHours));

        Button button = view.findViewById(R.id.connect_job_intro_start_button);
        button.setOnClickListener(v -> {
            String title = getString(R.string.connect_downloading_learn);
            NavDirections directions = ConnectJobIntroFragmentDirections.actionConnectJobIntroFragmentToConnectDownloadingFragment(title, job.getTitle());
            Navigation.findNavController(button).navigate(directions);
        });

        return view;
    }
}