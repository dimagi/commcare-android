package org.commcare.fragments.connect.login_job_fragments;

import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.commcare.adapters.JobListCommCareAppsAdapter;
import org.commcare.dalvik.databinding.FragmentConnectLoginCommcareAppsBinding;
import org.commcare.models.connect.ConnectLoginJobListModel;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectLoginCommcareAppsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConnectLoginCommcareAppsFragment extends Fragment {

    private FragmentConnectLoginCommcareAppsBinding binding;
    private static final String ARG_TRADITIONAL_JOB_LIST = "traditional_job_list";
    private ArrayList<ConnectLoginJobListModel> traditionalJobList;

    public static ConnectLoginCommcareAppsFragment newInstance(List<ConnectLoginJobListModel> traditionalJobList) {
        ConnectLoginCommcareAppsFragment fragment = new ConnectLoginCommcareAppsFragment();
        Bundle args = new Bundle();
        args.putParcelableArrayList(ARG_TRADITIONAL_JOB_LIST, (ArrayList<? extends Parcelable>) traditionalJobList);
        fragment.setArguments(args);
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
        return binding.getRoot();
    }

    private void initRecyclerView() {
        JobListCommCareAppsAdapter adapter = new JobListCommCareAppsAdapter(getContext(), traditionalJobList);
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
}
