package org.commcare.fragments.connect;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.commcaresupportlibrary.CommCareLauncher;
import org.commcare.dalvik.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import androidx.fragment.app.Fragment;

public class ConnectDeliveryProgressDeliveryFragment extends Fragment {
    private View view;
    private ConnectJobRecord job;
    public ConnectDeliveryProgressDeliveryFragment() {
        // Required empty public constructor
    }

    public static ConnectDeliveryProgressDeliveryFragment newInstance(ConnectJobRecord job) {
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
        view = inflater.inflate(R.layout.fragment_connect_progress_delivery, container, false);
        updateView();

        boolean expired = job.getDaysRemaining() < 0;
        Button button = view.findViewById(R.id.connect_progress_button);
        button.setOnClickListener(v -> {
            String title = null;
            String message = null;
            if(expired) {
                title = getString(R.string.connect_progress_expired_dialog_title);
                message = getString(R.string.connect_progress_expired);
            }
            else if(job.getCompletedVisits() >= job.getMaxVisits()) {
                title = getString(R.string.connect_progress_max_visits_dialog_title);
                message = getString(R.string.connect_progress_visits_completed);
            }

            if(title != null) {
                new AlertDialog.Builder(getContext())
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(R.string.proceed, (dialog, which) -> {
                            launchDeliveryApp();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
            else {
                launchDeliveryApp();
            }
        });

        return view;
    }

    private void launchDeliveryApp() {
        CommCareLauncher.launchCommCareForAppIdFromConnect(getContext(), job.getDeliveryAppInfo().getAppId());
    }

    public void updateView() {

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
    }
}