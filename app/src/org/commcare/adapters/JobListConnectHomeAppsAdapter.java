package org.commcare.adapters;


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
import org.commcare.interfaces.OnJobSelectionClick;
import org.commcare.models.connect.ConnectLoginJobListModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import static org.commcare.connect.ConnectConstants.JOB_DELIVERY;
import static org.commcare.connect.ConnectConstants.JOB_LEARNING;

public class JobListConnectHomeAppsAdapter extends RecyclerView.Adapter<JobListConnectHomeAppsAdapter.ViewHolder> {

    private Context mContext;
    private final ArrayList<ConnectLoginJobListModel> jobList;
    private final OnJobSelectionClick launcher;

    public JobListConnectHomeAppsAdapter(Context context, ArrayList<ConnectLoginJobListModel> jobList, OnJobSelectionClick launcher) {
        this.mContext = context;
        this.jobList = jobList;
        this.launcher = launcher;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        mContext = parent.getContext();
        // Inflate the layout for each item using View Binding
        ItemLoginConnectHomeAppsBinding binding = ItemLoginConnectHomeAppsBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        bind(mContext, holder.binding, jobList.get(position), launcher);
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
    }

    private static String formatDate(String dateStr) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM, yyyy", Locale.ENGLISH);
            Date date = inputFormat.parse(dateStr);
            return outputFormat.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void bind(Context mContext, ItemLoginConnectHomeAppsBinding binding, ConnectLoginJobListModel connectLoginJobListModel, OnJobSelectionClick launcher) {
        binding.tvTitle.setText(connectLoginJobListModel.getName());
        binding.tvDate.setText(formatDate(connectLoginJobListModel.getLastAccessed().toString()));
        handleProgressBarUI(mContext, connectLoginJobListModel, binding);
        configureJobType(mContext, connectLoginJobListModel, binding);

        clickListener(binding, connectLoginJobListModel, launcher);
    }

    private void clickListener(ItemLoginConnectHomeAppsBinding binding, ConnectLoginJobListModel connectLoginJobListModel, OnJobSelectionClick launcher) {
        binding.rootCardView.setOnClickListener(view -> {
            launcher.onClick(connectLoginJobListModel.getJob(), connectLoginJobListModel.isLearningApp(), connectLoginJobListModel.getAppId(), connectLoginJobListModel.getJobType());
        });
    }

    public void handleProgressBarUI(Context context, ConnectLoginJobListModel item, ItemLoginConnectHomeAppsBinding binding) {
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

    private void configureJobType(Context context, ConnectLoginJobListModel item, ItemLoginConnectHomeAppsBinding binding) {
        if (item.isNew()) {
            setJobType(context, R.drawable.connect_rounded_corner_orange_yellow,
                    context.getResources().getString(R.string.connect_new_opportunity), R.drawable.ic_connect_new_opportunity,
                    R.color.connect_yellowish_orange_color, binding);
        } else if (item.isLearningApp()) {
            setJobType(context, R.drawable.connect_rounded_corner_teslish_blue,
                    context.getResources().getString(R.string.connect_learn), R.drawable.ic_connect_learning,
                    R.color.connect_blue_color, binding);
        } else if (item.isDeliveryApp()) {
            setJobType(context, R.drawable.connect_rounded_corner_light_green,
                    context.getResources().getString(R.string.connect_delivery), R.drawable.ic_connect_delivery,
                    R.color.connect_green, binding);
        }
    }

    private void setJobType(Context context, int backgroundResId, String jobTypeText,
                            int iconResId, int textColorResId, ItemLoginConnectHomeAppsBinding binding) {
        binding.llOpportunity.setBackground(ContextCompat.getDrawable(context, backgroundResId));
        binding.tvJobType.setText(jobTypeText);
        binding.imgJobType.setImageDrawable(ContextCompat.getDrawable(context, iconResId));
        binding.tvJobType.setTextColor(ContextCompat.getColor(context, textColorResId));
    }
}

