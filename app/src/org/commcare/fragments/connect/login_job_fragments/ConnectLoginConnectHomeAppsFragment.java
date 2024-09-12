package org.commcare.fragments.connect.login_job_fragments;

import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.commcare.adapters.JobListConnectHomeAppsAdapter;
import org.commcare.dalvik.databinding.FragmentConnectLoginCommcareHomeBinding;
import org.commcare.interfaces.JobListCallBack;
import org.commcare.models.connect.ConnectLoginJobListModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectLoginConnectHomeAppsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConnectLoginConnectHomeAppsFragment extends Fragment {

    private static final String ARG_JOB_LIST = "job_list";
    private FragmentConnectLoginCommcareHomeBinding binding;
    private ArrayList<ConnectLoginJobListModel> jobList;
    private JobListCallBack mCallback;

    public static ConnectLoginConnectHomeAppsFragment newInstance(List<ConnectLoginJobListModel> jobList,JobListCallBack mCallback) {
        ConnectLoginConnectHomeAppsFragment fragment = new ConnectLoginConnectHomeAppsFragment();
        Bundle args = new Bundle();
        // Sort the jobList by lastAccess date before passing it to the fragment
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            jobList.sort(Comparator.comparing(ConnectLoginJobListModel::getLastAccessed));
        }
        args.putParcelableArrayList(ARG_JOB_LIST, (ArrayList<? extends Parcelable>) jobList);
        fragment.setArguments(args);
        fragment.setOnJobListClickedListener(mCallback);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentConnectLoginCommcareHomeBinding.inflate(inflater, container, false);

        // Retrieve the job list from the arguments
        if (getArguments() != null) {
            jobList = getArguments().getParcelableArrayList(ARG_JOB_LIST);
        }

        initRecyclerView();
        return binding.getRoot();
    }

    private void initRecyclerView() {
        JobListConnectHomeAppsAdapter adapter = new JobListConnectHomeAppsAdapter(getContext(), jobList, (appId,jobName,jobType) -> mCallback.onClick(appId,jobName,jobType));
        binding.rcConnectHomeApps.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rcConnectHomeApps.setNestedScrollingEnabled(true);
        binding.rcConnectHomeApps.setAdapter(adapter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public void setOnJobListClickedListener(JobListCallBack mCallback) {
        this.mCallback = mCallback;
    }
}
