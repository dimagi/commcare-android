package org.commcare.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.activities.connect.ConnectDatabaseHelper;
import org.commcare.activities.connect.ConnectManager;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.dalvik.R;
import org.commcare.fragments.connect.ConnectJobsListsFragmentDirections;

import java.util.Date;
import java.util.List;

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
    private static final int ViewTypeEnded = 6;

    private Context parentContext;
    private final boolean showAvailable;

    public ConnectJobAdapter(boolean showAvailable) {
        this.showAvailable = showAvailable;
    }

    @Override
    public int getItemCount() {
        if(showAvailable) {
            //1 section, no header
            int numAvailable = ConnectDatabaseHelper.getAvailableJobs(parentContext).size();
            return numAvailable > 0 ? numAvailable : 1;
        }

        //3 sections, each with a header and at least 1 row (for placeholder)

        List<ConnectJobRecord> training = ConnectDatabaseHelper.getTrainingJobs(parentContext);
        int numTrainingRows = training.size() > 0 ? training.size() : 1;

        List<ConnectJobRecord> claimed = ConnectDatabaseHelper.getDeliveryJobs(parentContext);
        int numClaimedRows = claimed.size() > 0 ? claimed.size() : 1;

        List<ConnectJobRecord> finished = ConnectDatabaseHelper.getFinishedJobs(parentContext);
        int numFinishedRows = finished.size() > 0 ? finished.size() : 1;

        return numTrainingRows + numClaimedRows + numFinishedRows + 3; //3 here is for headers
    }

    @Override
    public int getItemViewType(int position) {
        if(showAvailable) {
            int numAvailable = ConnectDatabaseHelper.getAvailableJobs(parentContext).size();
            if(numAvailable == 0) {
                return ViewTypeEmpty;
            }

            return ViewTypeAvailable;
        }

        List<ConnectJobRecord> training = ConnectDatabaseHelper.getTrainingJobs(parentContext);
        int numTraining = training.size() > 0 ? training.size() : 1;
        int totalTrainingRows = numTraining + 1;

        List<ConnectJobRecord> claimed = ConnectDatabaseHelper.getDeliveryJobs(parentContext);
        int numClaimed = claimed.size() > 0 ? claimed.size() : 1;
        int totalTrainingPlusClaimedRows = numTraining + numClaimed + 2;

        if(position == 0 || position == totalTrainingRows || position == totalTrainingPlusClaimedRows) {
            return ViewTypeHeader;
        }

        if(position < totalTrainingRows) {
            return training.size() == 0 ? ViewTypeEmpty : ViewTypeLearning;
        }

        if(position < totalTrainingPlusClaimedRows) {
            return claimed.size() == 0 ? ViewTypeEmpty : ViewTypeClaimed;
        }

        List<ConnectJobRecord> finished = ConnectDatabaseHelper.getFinishedJobs(parentContext);

        return finished.size() == 0 ? ViewTypeEmpty : ViewTypeEnded;
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
            case ViewTypeEnded -> {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.connect_claimed_job_item, parent, false);
                return new ConnectJobAdapter.EndedJobViewHolder(view);
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
            ConnectJobRecord job = ConnectDatabaseHelper.getAvailableJobs(parentContext).get(position);

            availableHolder.newText.setVisibility(job.getIsNew() ? View.VISIBLE : View.GONE);
            availableHolder.titleText.setText(job.getTitle());
            availableHolder.descriptionText.setText(job.getShortDescription());

            availableHolder.visitsText.setText(parentContext.getString(R.string.connect_job_visits,
                    job.getMaxPossibleVisits(), job.getDaysRemaining()));

            availableHolder.continueImage.setOnClickListener(v ->
                    enterJob(job, availableHolder.continueImage));
        }
        else if(holder instanceof ConnectJobAdapter.ClaimedJobViewHolder claimedHolder) {
            List<ConnectJobRecord> training = ConnectDatabaseHelper.getTrainingJobs(parentContext);
            boolean isTraining = position - 1 < training.size();
            ConnectJobRecord job;
            if (isTraining) {
                job = training.get(position - 1);
            } else {
                int numTraining = training.size() > 0 ? training.size() : 1;
                job = ConnectDatabaseHelper.getDeliveryJobs(parentContext).get(position - numTraining - 2);
            }

            claimedHolder.titleText.setText(job.getTitle());

            int percent = getJobPercentage(job, isTraining);
            claimedHolder.progressBar.setProgress(percent);
            claimedHolder.progressBar.setMax(100);

            claimedHolder.descriptionText.setVisibility(View.VISIBLE);
            claimedHolder.descriptionText.setText(getStatusDescription(job, isTraining, percent));

            String fromStr = "";
            if(job.getProjectStartDate().after(new Date())) {
                fromStr = parentContext.getString(R.string.connect_job_remaining_from,
                        ConnectManager.formatDate(job.getProjectStartDate()));
            }
            String remaining = parentContext.getString(R.string.connect_job_remaining, job.getDaysRemaining(), fromStr);
            claimedHolder.remainingText.setVisibility(View.VISIBLE);
            claimedHolder.remainingText.setText(remaining);

            claimedHolder.progressBar.setVisibility(View.VISIBLE);
            claimedHolder.progressImage.setVisibility(View.VISIBLE);

            claimedHolder.continueImage.setVisibility(View.VISIBLE);
            claimedHolder.continueImage.setOnClickListener(v -> {
                enterJob(job, claimedHolder.continueImage);
            });
        }
        else if(holder instanceof ConnectJobAdapter.EndedJobViewHolder endedHolder) {
            List<ConnectJobRecord> training = ConnectDatabaseHelper.getTrainingJobs(parentContext);
            int numTraining = training.size() > 0 ? training.size() : 1;
            List<ConnectJobRecord> claimed = ConnectDatabaseHelper.getDeliveryJobs(parentContext);
            int numClaimed = claimed.size() > 0 ? claimed.size() : 1;
            int totalTrainingPlusClaimedRows = numTraining + numClaimed + 2;
            int endedIndex = position - 1 - totalTrainingPlusClaimedRows;

            ConnectJobRecord job = ConnectDatabaseHelper.getFinishedJobs(parentContext).get(endedIndex);

            endedHolder.titleText.setText(job.getTitle());

            boolean isTraining = job.getStatus() == ConnectJobRecord.STATUS_LEARNING;
            int percent = getJobPercentage(job, isTraining);
            endedHolder.descriptionText.setVisibility(View.VISIBLE);
            endedHolder.descriptionText.setText(getStatusDescription(job, isTraining, percent));

            String endedText = parentContext.getString(R.string.connect_job_completed, ConnectManager.formatDate(job.getProjectEndDate()));
            endedHolder.remainingText.setVisibility(View.VISIBLE);
            endedHolder.remainingText.setText(endedText);

            endedHolder.progressBar.setVisibility(View.GONE);
            endedHolder.progressImage.setVisibility(View.GONE);

            endedHolder.continueImage.setVisibility(View.VISIBLE);
            endedHolder.continueImage.setOnClickListener(v -> {
                enterJob(job, endedHolder.continueImage);
            });
        }
        else if(holder instanceof ConnectJobAdapter.EmptyJobListViewHolder emptyHolder) {
            int textResource = position == 1 ? R.string.connect_job_none_training : R.string.connect_job_none_active;
            if(showAvailable) {
                textResource = R.string.connect_job_none_available;
            }

            emptyHolder.image.setVisibility(!showAvailable && position == 1 ? View.GONE : View.VISIBLE);
            emptyHolder.titleText.setText(parentContext.getString(textResource));
        }
        else if(holder instanceof ConnectJobAdapter.JobHeaderViewHolder headerHolder) {
            int textId = R.string.connect_job_ended;
            if(position == 0) {
                textId = R.string.connect_job_training;
            }
            else {
                List<ConnectJobRecord> training = ConnectDatabaseHelper.getTrainingJobs(parentContext);
                int numTraining = training.size() > 0 ? training.size() : 1;
                int totalTrainingRows = numTraining + 1;
                if(position == totalTrainingRows) {
                    textId = R.string.connect_job_claimed;
                }
            }

            headerHolder.titleText.setText(parentContext.getString(textId));
        }
    }

    int getJobPercentage(ConnectJobRecord job, boolean isTraining) {
        int percent = job.getPercentComplete();
        if (isTraining) {
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

        return percent;
    }

    private String getStatusDescription(ConnectJobRecord job, boolean isTraining, int percent) {
        String description;
        boolean finished = job.isFinished();
        if (isTraining) {
            //Started learning
            if(percent >= 100) {
                //Finished learning
                if (job.passedAssessment()) {
                    description = parentContext.getString(finished ?
                            R.string.connect_job_passed_assessment :
                            R.string.connect_job_training_complete);
                } else {
                    description = parentContext.getString(finished ?
                            R.string.connect_job_assessment_not_completed :
                            R.string.connect_job_needs_assessment);
                }
            } else {
                description = parentContext.getString(R.string.connect_job_learning_not_completed);
            }
        } else if(finished) {
            description = parentContext.getString(R.string.connect_job_visits_completed, job.getCompletedVisits());
        } else {
            String learningOrJob = parentContext.getString(isTraining ? R.string.connect_job_learning : R.string.connect_job);
            description = parentContext.getString(R.string.connect_job_training_progress, learningOrJob, percent);
        }

        return description;
    }

    private void enterJob(ConnectJobRecord job, View view) {
        NavDirections directions;

        switch(job.getStatus()) {
            case ConnectJobRecord.STATUS_AVAILABLE,
                    ConnectJobRecord.STATUS_AVAILABLE_NEW -> {
                directions = ConnectJobsListsFragmentDirections.actionConnectJobsListFragmentToConnectJobIntroFragment(job);
            }
            case ConnectJobRecord.STATUS_LEARNING -> {
                directions = ConnectJobsListsFragmentDirections.actionConnectJobsListFragmentToConnectJobLearningProgressFragment(job);
            }
            case ConnectJobRecord.STATUS_DELIVERING -> {
                directions = ConnectJobsListsFragmentDirections.actionConnectJobsListFragmentToConnectJobDeliveryProgressFragment(job);
            }
            default -> {
                throw new RuntimeException(String.format("Unexpected job status: %d", job.getStatus()));
            }
        }

        Navigation.findNavController(view).navigate(directions);
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

    public static class EndedJobViewHolder extends RecyclerView.ViewHolder {
        ProgressBar progressBar;
        ImageView progressImage;
        TextView titleText;
        TextView descriptionText;
        TextView remainingText;
        ImageView continueImage;
        public EndedJobViewHolder(@NonNull View itemView) {
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
