package org.commcare.fragments.connect.login_job_fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.commcare.adapters.JobListCommCareAppsAdapter;
import org.commcare.adapters.JobListConnectHomeAppsAdapter;
import org.commcare.dalvik.databinding.FragmentConnectLoginCommcareAppsBinding;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectLoginCommcareAppsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConnectLoginCommcareAppsFragment extends Fragment {

    private FragmentConnectLoginCommcareAppsBinding binding;
    private JobListCommCareAppsAdapter adapter;

    public static ConnectLoginCommcareAppsFragment newInstance() {
        ConnectLoginCommcareAppsFragment fragment = new ConnectLoginCommcareAppsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentConnectLoginCommcareAppsBinding.inflate(inflater, container, false);
        initRecyclerView();
        return binding.getRoot();
    }

    private void initRecyclerView() {
        adapter = new JobListCommCareAppsAdapter(getContext());
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
