package org.commcare.adapters;

import static org.commcare.activities.LoginActivity.JOB_DELIVERY;
import static org.commcare.activities.LoginActivity.JOB_LEARNING;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.activities.LoginActivity;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ItemLoginConnectHomeAppsBinding;
import org.commcare.interfaces.JobListCallBack;
import org.commcare.models.connect.ConnectLoginJobListModel;

import java.util.ArrayList;

public class JobListConnectHomeAppsAdapter extends RecyclerView.Adapter<JobListConnectHomeAppsAdapter.ViewHolder> {

    private final Context mContext;
    private final ArrayList<ConnectLoginJobListModel> jobList;
    private JobListCallBack mCallback;

    public JobListConnectHomeAppsAdapter(Context context, ArrayList<ConnectLoginJobListModel> jobList,JobListCallBack mCallback) {
        this.mContext = context;
        this.jobList = jobList;
        this.mCallback = mCallback;
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
        holder.bind(mContext, jobList.get(position),mCallback);
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

        public void bind(Context mContext, ConnectLoginJobListModel connectLoginJobListModel, JobListCallBack mCallback) {
            binding.tvTitle.setText(connectLoginJobListModel.getName());
            binding.tvDate.setText(LoginActivity.formatDate(connectLoginJobListModel.getLastAccessed().toString()));
            handleProgressBarUI(mContext, connectLoginJobListModel);
            configureJobType(mContext, connectLoginJobListModel);

            clickListener(mContext,connectLoginJobListModel,mCallback);
        }

        private void clickListener(Context mContext, ConnectLoginJobListModel connectLoginJobListModel, JobListCallBack mCallback) {
            binding.rootCardView.setOnClickListener(view -> {
                mCallback.onClick(connectLoginJobListModel.getId(),connectLoginJobListModel.getName(),LoginActivity.SELECTED_CONNECT_JOB);
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
