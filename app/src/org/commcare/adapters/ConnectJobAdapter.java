package org.commcare.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.activities.connect.ConnectIdDatabaseHelper;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.dalvik.R;
import org.commcare.fragments.connect.ConnectJobsListsFragmentDirections;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
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

        List<ConnectJobRecord> training = ConnectIdDatabaseHelper.getTrainingJobs(parentContext);
        int numTraining = training.size() > 0 ? training.size() : 1;

        List<ConnectJobRecord> claimed = ConnectIdDatabaseHelper.getClaimedJobs(parentContext);
        int numClaimed = claimed.size() > 0 ? claimed.size() : 1;
        return numTraining + numClaimed + 2;
    }

    @Override
    public int getItemViewType(int position) {
        if(showAvailable) {
            return ViewTypeAvailable;
        }

        List<ConnectJobRecord> training = ConnectIdDatabaseHelper.getTrainingJobs(parentContext);
        int numTraining = training.size() > 0 ? training.size() : 1;
        if(position == 0 || position - 1 == numTraining) {
            return ViewTypeHeader;
        }

        if(position <= numTraining) {
            return training.size() == 0 ? ViewTypeEmpty : ViewTypeLearning;
        }

        return ConnectIdDatabaseHelper.getClaimedJobs(parentContext).size() == 0 ? ViewTypeEmpty : ViewTypeClaimed;
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
            ConnectJobRecord job = ConnectIdDatabaseHelper.getAvailableJobs(parentContext).get(position);

            availableHolder.newText.setVisibility(job.getIsNew() ? View.VISIBLE : View.GONE);
            availableHolder.titleText.setText(job.getTitle());
            availableHolder.descriptionText.setText(job.getDescription());

            availableHolder.visitsText.setText(parentContext.getString(R.string.connect_job_visits,
                    job.getMaxPossibleVisits(), job.getDaysRemaining()));

            availableHolder.continueImage.setOnClickListener(v ->
                Navigation.findNavController(availableHolder.continueImage).navigate(
                        ConnectJobsListsFragmentDirections.actionConnectJobsListFragmentToConnectJobIntroFragment(job)));
        }
        else if(holder instanceof ConnectJobAdapter.ClaimedJobViewHolder claimedHolder) {
            List<ConnectJobRecord> training = ConnectIdDatabaseHelper.getTrainingJobs(parentContext);
            boolean isTraining = position-1 < training.size();
            ConnectJobRecord job;
            if(isTraining) {
                job = training.get(position - 1);
            }
            else {
                int numTraining = training.size() > 0 ? training.size() : 1;
                job = ConnectIdDatabaseHelper.getClaimedJobs(parentContext).get(position - numTraining - 2);
            }

            claimedHolder.titleText.setText(job.getTitle());
            boolean finished = job.getStatus() == ConnectJobRecord.STATUS_COMPLETE;
            String description;
            String remaining = null;
            if(finished) {
                DateFormat df = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
                description = parentContext.getString(R.string.connect_job_completed, df.format(job.getDateCompleted()));
            }
            else {
                String extra = parentContext.getString(isTraining ? R.string.connect_job_learning : R.string.connect_job);
                int percent = job.getPercentComplete();
                if(isTraining) {
                    //NOTE: leaving other code here for now in case API changfes to give back the modules array
                    int completed = job.getCompletedLearningModules();//0;
//                    for (ConnectJobLearningModule module: job.getLearningModules()) {
//                        if(module.getCompletedDate() != null) {
//                            completed++;
//                        }
//                    }

                    int numModules = job.getNumLearningModules();// job.getLearningModules().length;
                    percent = numModules > 0 ? (100 * completed / numModules) : 100;
                }
                if(isTraining && percent >= 100) {
                    description = parentContext.getString(R.string.connect_job_training_complete);
                }
                else {
                    description = parentContext.getString(R.string.connect_job_training_progress, extra, percent);
                }

                claimedHolder.progressBar.setProgress(percent);
                claimedHolder.progressBar.setMax(100);

                remaining = parentContext.getString(R.string.connect_job_remaining, job.getDaysRemaining());
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
            String text = parentContext.getString(position == 1 ? R.string.connect_job_none_training : R.string.connect_job_none_active);
            emptyHolder.image.setVisibility(position == 1 ? View.GONE : View.VISIBLE);
            emptyHolder.titleText.setText(text);
        }
        else if(holder instanceof ConnectJobAdapter.JobHeaderViewHolder headerHolder) {
            String text = parentContext.getString(position == 0 ? R.string.connect_job_training : R.string.connect_job_claimed);
            headerHolder.titleText.setText(text);
        }
    }

    public static class AvailableJobViewHolder extends RecyclerView.ViewHolder {
        TextView newText;
        TextView titleText;
        TextView descriptionText;
        TextView visitsText;
        ImageView continueImage;
        public AvailableJobViewHolder(@NonNull View itemView) {
            super(itemView);

            newText = itemView.findViewById(R.id.new_label);
            titleText = itemView.findViewById(R.id.title_label);
            descriptionText = itemView.findViewById(R.id.description_label);
            visitsText = itemView.findViewById(R.id.visits_label);
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
