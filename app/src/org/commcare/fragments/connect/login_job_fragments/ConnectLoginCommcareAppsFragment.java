package org.commcare.fragments.connect.login_job_fragments;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareSetupActivity;
import org.commcare.activities.DispatchActivity;
import org.commcare.adapters.JobListCommCareAppsAdapter;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectLoginCommcareAppsBinding;
import org.commcare.interfaces.JobListCallBack;
import org.commcare.models.connect.ConnectLoginJobListModel;
import org.commcare.services.CommCareSessionService;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.dialogs.StandardAlertDialog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectLoginCommcareAppsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConnectLoginCommcareAppsFragment extends Fragment {

    private FragmentConnectLoginCommcareAppsBinding binding;
    private static final String ARG_TRADITIONAL_JOB_LIST = "traditional_job_list";
    public static final String KEY_LAUNCH_FROM_MANAGER = "from_manager";
    private ArrayList<ConnectLoginJobListModel> traditionalJobList;
    private JobListCallBack mCallback;

    public static ConnectLoginCommcareAppsFragment newInstance(List<ConnectLoginJobListModel> traditionalJobList,JobListCallBack mCallback) {
        ConnectLoginCommcareAppsFragment fragment = new ConnectLoginCommcareAppsFragment();
        Bundle args = new Bundle();
        // Sort the jobList by lastAccess date before passing it to the fragment
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            traditionalJobList.sort(Comparator.comparing(ConnectLoginJobListModel::getLastAccessed));
        }
        args.putParcelableArrayList(ARG_TRADITIONAL_JOB_LIST, (ArrayList<? extends Parcelable>) traditionalJobList);
        fragment.setArguments(args);
        fragment.setOnJobListClickedListener(mCallback);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentConnectLoginCommcareAppsBinding.inflate(inflater, container, false);

        // Retrieve the job list from the arguments
        if (getArguments() != null) {
            traditionalJobList = getArguments().getParcelableArrayList(ARG_TRADITIONAL_JOB_LIST);
        }

        initRecyclerView();
        clickListeners();
        return binding.getRoot();
    }

    private void clickListeners() {
        binding.lytInstallApps.btnInstallNow.setOnClickListener(view -> {
            installAppClicked();
        });
    }

    private void initRecyclerView() {
        JobListCommCareAppsAdapter adapter = new JobListCommCareAppsAdapter(getContext(), traditionalJobList, (appId,jobName) -> mCallback.onClick(appId,jobName));
        binding.rcCommCareApps.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rcCommCareApps.setNestedScrollingEnabled(true);
        binding.rcCommCareApps.setAdapter(adapter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Nullify the binding object to avoid memory leaks
        binding = null;
    }

    public void installAppClicked() {
        try {
            CommCareSessionService s = CommCareApplication.instance().getSession();
            if (s.isActive()) {
                triggerLogoutWarning();
            } else {
                installApp();
            }
        } catch (SessionUnavailableException e) {
            installApp();
        }
    }

    /**
     * Logs the user out and takes them to the app installation activity.
     */
    private void installApp() {
        Intent i = new Intent(getActivity(), CommCareSetupActivity.class);
        i.putExtra(KEY_LAUNCH_FROM_MANAGER, true);
        this.startActivityForResult(i, DispatchActivity.INIT_APP);
    }

    /**
     * Warns user that the action they are trying to conduct will result in the current
     * session being logged out
     */
    private void triggerLogoutWarning() {
        String title = getString(R.string.logging_out);
        String message = getString(R.string.logout_warning);
        StandardAlertDialog d = new StandardAlertDialog(getActivity(), title, message);
        DialogInterface.OnClickListener listener = (dialog, which) -> {
            if (which == AlertDialog.BUTTON_POSITIVE) {
                CommCareApplication.instance().expireUserSession();
                installApp();
            }
        };
        d.setPositiveButton(getString(R.string.ok), listener);
        d.setNegativeButton(getString(R.string.cancel), listener);
    }

    public void setOnJobListClickedListener(JobListCallBack mCallback) {
        this.mCallback = mCallback;
    }
}
