package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.commcare.android.database.connect.models.ConnectJob;
import org.commcare.android.database.connect.models.ConnectJobLearningModule;
import org.commcare.android.database.connect.models.MockJobProvider;
import org.commcare.dalvik.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Fragment for showing the "My Jobs" list
 *
 * @author dviggiano
 */
public class ConnectJobsMyListFragment extends Fragment {
    public ConnectJobsMyListFragment() {
        // Required empty public constructor
    }

    public static ConnectJobsMyListFragment newInstance() {
        return new ConnectJobsMyListFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_connect_my_jobs_lists, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.my_jobs_list);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(linearLayoutManager);

        recyclerView.addItemDecoration(new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));

        recyclerView.setAdapter(new MyJobsAdapter());

        return view;
    }

    private static class MyJobsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int ViewTypeHeader = 1;
        private static final int ViewTypeLearning = 2;
        private static final int ViewTypeClaimed = 3;
        private static final int ViewTypeEmpty = 4;

        @Override
        public int getItemCount() {
            int numTraining = MockJobProvider.getTrainingJobs().length > 0 ? MockJobProvider.getTrainingJobs().length : 1;
            int numClaimed = MockJobProvider.getClaimedJobs().length > 0 ? MockJobProvider.getClaimedJobs().length : 1;
            return numTraining + numClaimed + 2;
        }

        @Override
        public int getItemViewType(int position) {
            int numTraining = MockJobProvider.getTrainingJobs().length > 0 ? MockJobProvider.getTrainingJobs().length : 1;
            if(position == 0 || position - 1 == numTraining) {
                return ViewTypeHeader;
            }

            if(position <= numTraining) {
                return MockJobProvider.getTrainingJobs().length == 0 ? ViewTypeEmpty : ViewTypeLearning;
            }

            return MockJobProvider.getClaimedJobs().length == 0 ? ViewTypeEmpty : ViewTypeClaimed;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            switch(viewType) {
                case ViewTypeClaimed, ViewTypeLearning -> {
                    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.connect_claimed_job_item, parent, false);
                    return new ClaimedJobViewHolder(view);
                }
                case ViewTypeEmpty -> {
                    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.connect_empty_job_list_item, parent, false);
                    return new EmptyJobListViewHolder(view);
                }
                case ViewTypeHeader -> {
                    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.connect_job_list_header_item, parent, false);
                    return new JobHeaderViewHolder(view);
                }
            }

            throw new RuntimeException("Not ready for requested viewType");
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if(holder instanceof ClaimedJobViewHolder claimedHolder) {
                boolean isTraining = position-1 < MockJobProvider.getTrainingJobs().length;
                ConnectJob job;
                if(isTraining) {
                    job = MockJobProvider.getTrainingJobs()[position - 1];
                }
                else {
                    int numTraining = MockJobProvider.getTrainingJobs().length > 0 ? MockJobProvider.getTrainingJobs().length : 1;
                    job = MockJobProvider.getClaimedJobs()[position - numTraining - 2];
                }

                claimedHolder.titleText.setText(job.getTitle());
                boolean finished = job.getDateCompleted() != null;
                String description;
                String remaining = null;
                if(finished) {
                    DateFormat df = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
                    description = String.format(Locale.getDefault(), "Completed on %s", df.format(job.getDateCompleted()));
                }
                else {
                    String extra = isTraining ? "Learning" : "Job";
                    int percent = job.getPercentComplete();
                    if(isTraining) {
                        int completed = 0;
                        for (ConnectJobLearningModule module: job.getLearningModules()) {
                            if(module.getCompletedDate() != null) {
                                completed++;
                            }
                         }

                        int numModules = job.getLearningModules().length;
                        percent = numModules > 0 ? (100 * completed / numModules) : 100;
                    }
                    description = String.format(Locale.getDefault(), "%s %d%% Complete", extra, percent);

                    extra = isTraining ? " to learn" : "";
                    double millis = job.getLearnDeadline().getTime() - (new Date()).getTime();
                    int daysRemaining = (int)(millis / 1000 / 3600 / 24);
                    remaining = String.format(Locale.getDefault(), "%d days remaining%s", daysRemaining, extra);
                }

                claimedHolder.descriptionText.setVisibility(View.VISIBLE);
                claimedHolder.descriptionText.setText(description);

                claimedHolder.remainingText.setVisibility(finished ? View.GONE : View.VISIBLE);
                claimedHolder.remainingText.setText(remaining != null ? remaining : "");

                claimedHolder.progressImage.setVisibility(finished ? View.GONE : View.VISIBLE);

                claimedHolder.continueImage.setVisibility(finished ? View.GONE : View.VISIBLE);
                claimedHolder.continueImage.setOnClickListener(v -> {
                    NavDirections directions;
                    if(isTraining) {
                        if(job.getPercentComplete() == 100) {
                            //TODO: Go to LearningComplete instead
                            directions = ConnectJobsListsFragmentDirections.actionConnectJobsListFragmentToConnectJobLearningProgressFragment(job);
                        }
                        else {
                            directions = ConnectJobsListsFragmentDirections.actionConnectJobsListFragmentToConnectJobLearningProgressFragment(job);
                        }
                    }
                    else {
                        if(job.getPercentComplete() > 0) {
                            directions = ConnectJobsListsFragmentDirections.actionConnectJobsListFragmentToConnectJobDeliveryProgressFragment(job);
                        }
                        else {
                            directions = ConnectJobsListsFragmentDirections.actionConnectJobsListFragmentToConnectJobDeliveryDetailsFragment(job);
                        }
                    }

                    Navigation.findNavController(claimedHolder.continueImage).navigate(directions);
                });
            }
            else if(holder instanceof EmptyJobListViewHolder emptyHolder) {
                String text = position == 1 ? "You're not training for any jobs right now" : "You don't have any active jobs right now";
                emptyHolder.image.setVisibility(position == 1 ? View.GONE : View.VISIBLE);
                emptyHolder.titleText.setText(text);
            }
            else if(holder instanceof JobHeaderViewHolder headerHolder) {
                String text = position == 0 ? "Jobs I'm Training For" : "My Claimed Jobs";
                headerHolder.titleText.setText(text);
            }
        }

        public static class ClaimedJobViewHolder extends RecyclerView.ViewHolder {
            ImageView progressImage;
            TextView titleText;
            TextView descriptionText;
            TextView remainingText;
            ImageView continueImage;
            public ClaimedJobViewHolder(@NonNull View itemView) {
                super(itemView);

                progressImage = itemView.findViewById(R.id.progress_image);
                titleText = itemView.findViewById(R.id.title_label);
                descriptionText = itemView.findViewById(R.id.description_label);
                remainingText = itemView.findViewById(R.id.remaining_label);
                continueImage = itemView.findViewById(R.id.button);
            }
        }

        public static class JobHeaderViewHolder extends RecyclerView.ViewHolder {
            TextView titleText;
            public JobHeaderViewHolder(@NonNull View itemView) {
                super(itemView);

                titleText = itemView.findViewById(R.id.title_label);
            }
        }

        public static class EmptyJobListViewHolder extends RecyclerView.ViewHolder {
            ImageView image;
            TextView titleText;
            public EmptyJobListViewHolder(@NonNull View itemView) {
                super(itemView);

                image = itemView.findViewById(R.id.empty_image);
                titleText = itemView.findViewById(R.id.title_label);
            }
        }
    }
}
