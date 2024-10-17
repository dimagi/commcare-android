package org.commcare.adapters;


import static org.commcare.activities.LoginActivity.JOB_DELIVERY;
import static org.commcare.activities.LoginActivity.JOB_LEARNING;
import static org.commcare.activities.LoginActivity.JOB_NEW_OPPORTUNITY;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.activities.LoginActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectJobListLauncher;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ItemLoginConnectHomeAppsBinding;
import org.commcare.models.connect.ConnectLoginJobListModel;

import java.util.ArrayList;
import java.util.List;

public class JobListConnectHomeAppsAdapter extends RecyclerView.Adapter<JobListConnectHomeAppsAdapter.ViewHolder> {

    private final Context mContext;
    private final ArrayList<ConnectLoginJobListModel> jobList;
    private final ConnectJobListLauncher launcher;

    public JobListConnectHomeAppsAdapter(Context context, ArrayList<ConnectLoginJobListModel> jobList, ConnectJobListLauncher launcher) {
        this.mContext = context;
        this.jobList = jobList;
        this.launcher = launcher;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the layout for each item using View Binding
        ItemLoginConnectHomeAppsBinding binding = ItemLoginConnectHomeAppsBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(mContext, position, jobList.get(position), launcher);
    }

    @Override
    public int getItemCount() {
        return jobList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemLoginConnectHomeAppsBinding binding;

        public ViewHolder(ItemLoginConnectHomeAppsBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Context mContext, int position, ConnectLoginJobListModel connectLoginJobListModel, ConnectJobListLauncher launcher) {
            binding.tvTitle.setText(connectLoginJobListModel.getName());
            binding.tvDate.setText(LoginActivity.formatDate(connectLoginJobListModel.getLastAccessed().toString()));
            handleProgressBarUI(mContext, connectLoginJobListModel);
            configureJobType(mContext, connectLoginJobListModel);

            clickListener(mContext, position, connectLoginJobListModel, launcher);
        }

        private void clickListener(Context mContext, int position, ConnectLoginJobListModel connectLoginJobListModel, ConnectJobListLauncher launcher) {
            binding.rootCardView.setOnClickListener(view -> {
                ConnectJobRecord job = null;
                Log.e("DEBUG_TESTING", "getJobType->: "+connectLoginJobListModel.getJobType());
                switch (connectLoginJobListModel.getJobType()) {
                    case JOB_NEW_OPPORTUNITY:
                        Log.e("DEBUG_TESTING", "JOB_NEW_OPPORTUNITY: ");
                        job = ConnectDatabaseHelper.getAvailableJobs(mContext).get(position);
                        break;
                    case JOB_LEARNING,JOB_DELIVERY:
                        // Handle claimed and ended jobs
                        List<ConnectJobRecord> claimedJobs = ConnectDatabaseHelper.getDeliveryJobs(mContext);
                        // Get the name to match
                        int jobNameToMatch = Integer.parseInt(connectLoginJobListModel.getId());
                        // Find the job with matching name in claimedJobs
                        for (ConnectJobRecord claimedJob : claimedJobs) {
                            Log.e("DEBUG_TESTING", "getTitle " +claimedJob.getJobId());
                            Log.e("DEBUG_TESTING", "jobNameToMatch " +jobNameToMatch);
                            if (claimedJob.getJobId() == jobNameToMatch) {
                                Log.e("DEBUG_TESTING", "Matched ");
                                job = claimedJob;  // Store the matching job
                                break;  // Exit loop once the job is found
                            }
                        }

                        break;
                }
                Log.e("DEBUG_TESTING", "Called ");
                launcher.launchApp(job, binding.rootCardView, connectLoginJobListModel.getJobType().equalsIgnoreCase(JOB_NEW_OPPORTUNITY));
            });
        }

        public void handleProgressBarUI(Context context, ConnectLoginJobListModel item) {
            int progress = 0;
            int progressColor = 0;
            String jobType = item.getJobType();

            if (jobType.equals(JOB_LEARNING)) {
                progress = item.getLearningProgress();
                progressColor = context.getResources().getColor(R.color.connect_blue_color);
            } else if (jobType.equals(JOB_DELIVERY)) {
                progress = item.getDeliveryProgress();
                progressColor = context.getResources().getColor(R.color.connect_green);
            }

            if (progress > 0) {
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.progressBar.setProgress(progress);
                binding.progressBar.setProgressColor(progressColor);
            } else {
                binding.progressBar.setVisibility(View.GONE);
            }
        }

        private void configureJobType(Context context, ConnectLoginJobListModel item) {
            if (item.isNew()) {
                setJobType(context, R.drawable.connect_rounded_corner_orange_yellow,
                        context.getResources().getString(R.string.connect_new_opportunity), R.drawable.ic_connect_new_opportunity,
                        R.color.connect_yellowish_orange_color);
            } else if (item.isLeaningApp()) {
                setJobType(context, R.drawable.connect_rounded_corner_teslish_blue,
                        context.getResources().getString(R.string.connect_learn), R.drawable.ic_connect_learning,
                        R.color.connect_blue_color);
            } else if (item.isDeliveryApp()) {
                setJobType(context, R.drawable.connect_rounded_corner_light_green,
                        context.getResources().getString(R.string.connect_delivery), R.drawable.ic_connect_delivery,
                        R.color.connect_green);
            }
        }

        private void setJobType(Context context, int backgroundResId, String jobTypeText,
                                int iconResId, int textColorResId) {
            binding.llOpportunity.setBackground(ContextCompat.getDrawable(context, backgroundResId));
            binding.tvJobType.setText(jobTypeText);
            binding.imgJobType.setImageDrawable(ContextCompat.getDrawable(context, iconResId));
            binding.tvJobType.setTextColor(ContextCompat.getColor(context, textColorResId));
        }
    }
}
