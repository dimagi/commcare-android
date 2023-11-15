package org.commcare.fragments.connect;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.FormEntryActivity;
import org.commcare.activities.connect.ConnectManager;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.commcaresupportlibrary.CommCareLauncher;
import org.commcare.dalvik.R;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.services.locale.Localization;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

public class ConnectDeliveryProgressDeliveryFragment extends Fragment {
    private ConnectJobRecord job;
    private View view;
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

//        //NOTE: The commented code attempts to warn the user when max visits has been exceeded
//        //But there's a bug where the buttons don't appear so the user gets stuck
//        //Just proceeding into the app instead.
//        boolean expired = job.getDaysRemaining() <= 0;
        Button button = view.findViewById(R.id.connect_progress_button);
        button.setOnClickListener(v -> {
//            String title = null;
//            String message = null;
//            if(expired) {
//                title = getString(R.string.connect_progress_expired_dialog_title);
//                message = getString(R.string.connect_progress_expired);
//            }
//            else if(job.getCompletedVisits() >= job.getMaxVisits()) {
//                title = getString(R.string.connect_progress_max_visits_dialog_title);
//                message = getString(R.string.connect_progress_visits_completed);
//            }
//
//            if(title != null) {
//                new AlertDialog.Builder(getContext())
//                        .setTitle(title)
//                        .setMessage(message)
//                        .setPositiveButton(R.string.proceed, (dialog, which) -> {
//                            launchDeliveryApp(button);
//                        })
//                        .setNegativeButton(R.string.cancel, null)
//                        .show();
//            }
//            else {
                launchDeliveryApp(button);
//            }
        });

        return view;
    }

    private void launchDeliveryApp(Button button) {
        if(ConnectManager.isAppInstalled(job.getDeliveryAppInfo().getAppId())) {
            ConnectManager.launchApp(getContext(), job.getDeliveryAppInfo().getAppId());
        }
        else {
            String title = getString(R.string.connect_downloading_delivery);
            Navigation.findNavController(button).navigate(ConnectDeliveryProgressFragmentDirections.actionConnectJobDeliveryProgressFragmentToConnectDownloadingFragment(title, false, true, job));
        }
    }

    public void updateView() {
        if(job == null || view == null) {
            return;
        }

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

        textView = view.findViewById(R.id.connect_progress_warning_learn_text);
        textView.setOnClickListener(v -> {
                StandardAlertDialog dialog = new StandardAlertDialog(
                        getContext(),
                        getString(R.string.connect_progress_warning),
                        getString(R.string.connect_progress_warning_full));
                dialog.setPositiveButton(Localization.get("dialog.ok"), (dialog1, which) -> {
                    dialog1.dismiss();
                });
            ((CommCareActivity<?>)getActivity()).showAlertDialog(dialog);
        });
    }
}
