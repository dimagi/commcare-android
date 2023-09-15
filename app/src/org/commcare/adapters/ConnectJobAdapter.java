package org.commcare.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.activities.connect.ConnectIdDatabaseHelper;
import org.commcare.android.database.connect.models.ConnectJob;
import org.commcare.android.database.connect.models.ConnectJobLearningModule;
import org.commcare.dalvik.R;
import org.commcare.fragments.connect.ConnectJobsAvailableListFragment;
import org.commcare.fragments.connect.ConnectJobsListsFragmentDirections;
import org.commcare.fragments.connect.ConnectJobsMyListFragment;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

public class ConnectJobAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int ViewTypeHeader = 1;
    private static final int ViewTypeLearning = 2;
    private static final int ViewTypeClaimed = 3;
    private static final int ViewTypeEmpty = 4;
    private static final int ViewTypeAvailable = 5;

    private Context parentContext;
    private final boolean showAvailable;

    public ConnectJobAdapter(boolean showAvailable) {
        this.showAvailable = showAvailable;
    }

    @Override
    public int getItemCount() {
        if(showAvailable) {
            return ConnectIdDatabaseHelper.getAvailableJobs(parentContext).size();
        }

        List<ConnectJob> training = ConnectIdDatabaseHelper.getTrainingJobs(parentContext);
        int numTraining = training.size() > 0 ? training.size() : 1;

        List<ConnectJob> claimed = ConnectIdDatabaseHelper.getClaimdeJobs(parentContext);
        int numClaimed = claimed.size() > 0 ? claimed.size() : 1;
        return numTraining + numClaimed + 2;
    }

    @Override
    public int getItemViewType(int position) {
        if(showAvailable) {
            return ViewTypeAvailable;
        }

        List<ConnectJob> training = ConnectIdDatabaseHelper.getTrainingJobs(parentContext);
        int numTraining = training.size() > 0 ? training.size() : 1;
        if(position == 0 || position - 1 == numTraining) {
            return ViewTypeHeader;
        }

        if(position <= numTraining) {
            return training.size() == 0 ? ViewTypeEmpty : ViewTypeLearning;
        }

        return ConnectIdDatabaseHelper.getClaimdeJobs(parentContext).size() == 0 ? ViewTypeEmpty : ViewTypeClaimed;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        parentContext = parent.getContext();
        switch(viewType) {
            case ViewTypeClaimed, ViewTypeLearning -> {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.connect_claimed_job_item, parent, false);
                return new ConnectJobAdapter.ClaimedJobViewHolder(view);
            }
            case ViewTypeEmpty -> {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.connect_empty_job_list_item, parent, false);
                return new ConnectJobAdapter.EmptyJobListViewHolder(view);
            }
            case ViewTypeHeader -> {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.connect_job_list_header_item, parent, false);
                return new ConnectJobAdapter.JobHeaderViewHolder(view);
            }
            case ViewTypeAvailable -> {
                View view = LayoutInflater.from(parentContext).inflate(R.layout.connect_available_job_item, parent, false);
                return new ConnectJobAdapter.AvailableJobViewHolder(view);
            }
        }

        throw new RuntimeException("Not ready for requested viewType");
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if(holder instanceof ConnectJobAdapter.AvailableJobViewHolder availableHolder) {
            ConnectJob job = ConnectIdDatabaseHelper.getAvailableJobs(parentContext).get(position);

            availableHolder.newText.setVisibility(job.getIsNew() ? View.VISIBLE : View.GONE);
            availableHolder.titleText.setText(job.getTitle());
            availableHolder.descriptionText.setText(job.getDescription());

            availableHolder.continueImage.setOnClickListener(v -> {
                Navigation.findNavController(availableHolder.continueImage).navigate(
                        ConnectJobsListsFragmentDirections.actionConnectJobsListFragmentToConnectJobIntroFragment(job));
            });
        }
        else if(holder instanceof ConnectJobAdapter.ClaimedJobViewHolder claimedHolder) {
            List<ConnectJob> training = ConnectIdDatabaseHelper.getTrainingJobs(parentContext);
            boolean isTraining = position-1 < training.size();
            ConnectJob job;
            if(isTraining) {
                job = training.get(position - 1);
            }
            else {
                int numTraining = training.size() > 0 ? training.size() : 1;
                job = ConnectIdDatabaseHelper.getClaimdeJobs(parentContext).get(position - numTraining - 2);
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

                claimedHolder.progressBar.setProgress(percent);
                claimedHolder.progressBar.setMax(100);

                extra = isTraining ? " to learn" : "";
                double millis = job.getLearnDeadline().getTime() - (new Date()).getTime();
                int daysRemaining = (int)(millis / 1000 / 3600 / 24);
                remaining = String.format(Locale.getDefault(), "%d days remaining%s", daysRemaining, extra);
            }

            claimedHolder.descriptionText.setVisibility(View.VISIBLE);
            claimedHolder.descriptionText.setText(description);

            claimedHolder.remainingText.setVisibility(finished ? View.GONE : View.VISIBLE);
            claimedHolder.remainingText.setText(remaining != null ? remaining : "");

            claimedHolder.progressBar.setVisibility(finished ? View.GONE : View.VISIBLE);
            claimedHolder.progressImage.setVisibility(finished ? View.GONE : View.VISIBLE);

            claimedHolder.continueImage.setVisibility(finished ? View.GONE : View.VISIBLE);
            claimedHolder.continueImage.setOnClickListener(v -> {
                NavDirections directions;
                if(isTraining) {
                    if(job.getPercentComplete() == 100) {
                        //TODO: Go to LearningComplete instead
                        directions = org.commcare.fragments.connect.ConnectJobsListsFragmentDirections.actionConnectJobsListFragmentToConnectJobLearningProgressFragment(job);
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
        else if(holder instanceof ConnectJobAdapter.EmptyJobListViewHolder emptyHolder) {
            String text = position == 1 ? "You're not training for any jobs right now" : "You don't have any active jobs right now";
            emptyHolder.image.setVisibility(position == 1 ? View.GONE : View.VISIBLE);
            emptyHolder.titleText.setText(text);
        }
        else if(holder instanceof ConnectJobAdapter.JobHeaderViewHolder headerHolder) {
            String text = position == 0 ? "Jobs I'm Training For" : "My Claimed Jobs";
            headerHolder.titleText.setText(text);
        }
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

    public static class ClaimedJobViewHolder extends RecyclerView.ViewHolder {
        ProgressBar progressBar;
        ImageView progressImage;
        TextView titleText;
        TextView descriptionText;
        TextView remainingText;
        ImageView continueImage;
        public ClaimedJobViewHolder(@NonNull View itemView) {
            super(itemView);

            progressBar = itemView.findViewById(R.id.progress_bar);
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
