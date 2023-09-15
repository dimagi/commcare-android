package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.database.connect.models.ConnectJob;
import org.commcare.dalvik.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import androidx.fragment.app.Fragment;

public class ConnectDeliveryProgressDeliveryFragment extends Fragment {
    private ConnectJob job;
    public ConnectDeliveryProgressDeliveryFragment() {
        // Required empty public constructor
    }

    public static ConnectDeliveryProgressDeliveryFragment newInstance(ConnectJob job) {
        ConnectDeliveryProgressDeliveryFragment fragment = new ConnectDeliveryProgressDeliveryFragment();
        fragment.job = job;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connect_progress_delivery, container, false);

        int completed = job.getCompletedVisits();
        int total = job.getMaxVisits();
        int percent = total > 0 ? (100 * completed / total) : 100;

        ProgressBar progress = view.findViewById(R.id.connect_progress_progress_bar);
        progress.setProgress(percent);
        progress.setMax(100);

        TextView textView = view.findViewById(R.id.connect_progress_progress_text);
        textView.setText(String.format(Locale.getDefault(), "%d%%", percent));

        textView = view.findViewById(R.id.connect_progress_status_text);
        textView.setText(getString(R.string.connect_progress_status, completed, total));

        textView = view.findViewById(R.id.connect_progress_complete_by_text);
        DateFormat df = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        textView.setText(getString(R.string.connect_progress_complete_by, df.format(job.getProjectEndDate())));

        Button button = view.findViewById(R.id.connect_progress_button);
        button.setText(getString(R.string.connect_progress_launch));
        button.setOnClickListener(v -> Toast.makeText(getContext(), "Not ready yet...", Toast.LENGTH_SHORT).show());

        return view;
    }
}
