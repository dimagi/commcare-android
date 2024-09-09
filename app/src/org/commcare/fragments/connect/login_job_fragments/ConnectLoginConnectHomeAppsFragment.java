package org.commcare.fragments.connect.login_job_fragments;

import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.commcare.adapters.JobListConnectHomeAppsAdapter;
import org.commcare.dalvik.databinding.FragmentConnectLoginCommcareHomeBinding;
import org.commcare.models.connect.ConnectLoginJobListModel;

import java.util.ArrayList;
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

    public static ConnectLoginConnectHomeAppsFragment newInstance(List<ConnectLoginJobListModel> jobList) {
        ConnectLoginConnectHomeAppsFragment fragment = new ConnectLoginConnectHomeAppsFragment();
        Bundle args = new Bundle();
        args.putParcelableArrayList(ARG_JOB_LIST, (ArrayList<? extends Parcelable>) jobList);
        fragment.setArguments(args);
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
        JobListConnectHomeAppsAdapter adapter = new JobListConnectHomeAppsAdapter(getContext(), jobList);
        binding.rcConnectHomeApps.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rcConnectHomeApps.setNestedScrollingEnabled(true);
        binding.rcConnectHomeApps.setAdapter(adapter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
