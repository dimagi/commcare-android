package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.commcare.dalvik.R;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Fragment for showing the available jobs list
 *
 * @author dviggiano
 */
public class ConnectJobsAvailableListFragment extends Fragment {
    public ConnectJobsAvailableListFragment() {
        // Required empty public constructor
    }

    public static ConnectJobsAvailableListFragment newInstance() {
        return new ConnectJobsAvailableListFragment();
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

        recyclerView.setAdapter(new AvailableJobsAdapter());

        recyclerView.addItemDecoration(new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));

        return view;
    }

    private static class AvailableJobsAdapter extends RecyclerView.Adapter<AvailableJobsAdapter.AvailableJobViewHolder> {
        @NonNull
        @Override
        public AvailableJobsAdapter.AvailableJobViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.connect_available_job_item, parent, false);

            return new AvailableJobViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AvailableJobsAdapter.AvailableJobViewHolder holder, int position) {
            ConnectJob job = MockJobProvider.getAvailableJobs()[position];

            holder.newText.setVisibility(job.getIsNew() ? View.VISIBLE : View.GONE);
            holder.titleText.setText(job.getTitle());
            holder.descriptionText.setText(job.getDescription());

            holder.continueImage.setOnClickListener(v -> {
                Navigation.findNavController(holder.continueImage).navigate(
                        ConnectJobsListsFragmentDirections.actionConnectJobsListFragmentToConnectJobIntroFragment(job));
            });
        }

        @Override
        public int getItemCount() {
            return MockJobProvider.getAvailableJobs().length;
        }

        public static class AvailableJobViewHolder extends RecyclerView.ViewHolder {
            TextView newText;
            TextView titleText;
            TextView descriptionText;
            ImageView continueImage;
            public AvailableJobViewHolder(@NonNull View itemView) {
                super(itemView);

                newText = itemView.findViewById(R.id.new_label);
                titleText = itemView.findViewById(R.id.title_label);
                descriptionText = itemView.findViewById(R.id.description_label);
                continueImage = itemView.findViewById(R.id.button);
            }
        }
    }
}
