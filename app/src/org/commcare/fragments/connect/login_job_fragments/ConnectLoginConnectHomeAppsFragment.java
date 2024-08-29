package org.commcare.fragments.connect.login_job_fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.commcare.adapters.JobListConnectHomeAppsAdapter;
import org.commcare.dalvik.databinding.FragmentConnectLoginCommcareHomeBinding;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectLoginConnectHomeAppsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConnectLoginConnectHomeAppsFragment extends Fragment {

    private FragmentConnectLoginCommcareHomeBinding binding;
    private JobListConnectHomeAppsAdapter adapter;

    public static ConnectLoginConnectHomeAppsFragment newInstance() {
        ConnectLoginConnectHomeAppsFragment fragment = new ConnectLoginConnectHomeAppsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentConnectLoginCommcareHomeBinding.inflate(inflater, container, false);
        initRecyclerView();
        return binding.getRoot();
    }

    private void initRecyclerView() {
        adapter = new JobListConnectHomeAppsAdapter(getContext());
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
