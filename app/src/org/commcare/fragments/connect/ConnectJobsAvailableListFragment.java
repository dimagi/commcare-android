package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.commcare.connect.IConnectAppLauncher;
import org.commcare.adapters.ConnectJobAdapter;
import org.commcare.dalvik.R;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Fragment for showing the available jobs list
 *
 * @author dviggiano
 */
public class ConnectJobsAvailableListFragment extends Fragment {
    private ConnectJobAdapter adapter;
    private IConnectAppLauncher launcher;
    public ConnectJobsAvailableListFragment() {
        // Required empty public constructor
    }

    public static ConnectJobsAvailableListFragment newInstance(IConnectAppLauncher appLauncher) {
        ConnectJobsAvailableListFragment fragment = new ConnectJobsAvailableListFragment();
        fragment.launcher = appLauncher;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connect_available_jobs_list, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.available_jobs_list);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(linearLayoutManager);

        recyclerView.addItemDecoration(new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));

        adapter = new ConnectJobAdapter(true, launcher);
        recyclerView.setAdapter(adapter);

        return view;
    }

    public void updateView() {
        adapter.notifyDataSetChanged();
    }
}
