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

import org.commcare.adapters.JobListCombinedAdapter;
import org.commcare.dalvik.databinding.FragmentConnectLoginCombineAppsBinding;
import org.commcare.interfaces.JobListCallBack;
import org.commcare.models.connect.ConnectCombineJobListModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectLoginCombineAppsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConnectLoginCombineAppsFragment extends Fragment {

    private FragmentConnectLoginCombineAppsBinding binding;
    private static final String ARG_COMBINE_JOB_LIST = "combine_job_list";
    private ArrayList<ConnectCombineJobListModel> traditionalJobList;
    private JobListCallBack mCallback;

    public static ConnectLoginCombineAppsFragment newInstance(List<ConnectCombineJobListModel> traditionalJobList,JobListCallBack mCallback) {
        ConnectLoginCombineAppsFragment fragment = new ConnectLoginCombineAppsFragment();
        Bundle args = new Bundle();
        // Sort the list of ConnectCombineJobListModel based on the lastAccessed property
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            traditionalJobList.sort(Comparator.comparing(job -> job.getConnectLoginJobListModel().getLastAccessed()));
//        }
        args.putParcelableArrayList(ARG_COMBINE_JOB_LIST, (ArrayList<? extends Parcelable>) traditionalJobList);
        fragment.setArguments(args);
        fragment.setOnJobListClickedListener(mCallback);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentConnectLoginCombineAppsBinding.inflate(inflater, container, false);

        // Retrieve the job list from the arguments
        if (getArguments() != null) {
            traditionalJobList = getArguments().getParcelableArrayList(ARG_COMBINE_JOB_LIST);
        }

        initRecyclerView();
        return binding.getRoot();
    }

    private void initRecyclerView() {
        JobListCombinedAdapter adapter = new JobListCombinedAdapter(getContext(), traditionalJobList, (jobId,appId,jobName,appType) -> mCallback.onClick(jobId,appId,jobName,appType));
        binding.rcCombineApps.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rcCombineApps.setNestedScrollingEnabled(true);
        binding.rcCombineApps.setAdapter(adapter);
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
