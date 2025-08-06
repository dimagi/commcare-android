package org.commcare.adapters;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ItemLoginConnectHomeAppsBinding;
import org.commcare.dalvik.databinding.ItemLoginConnectHomeCorruptAppsBinding;
import org.commcare.interfaces.OnJobSelectionClick;
import org.commcare.models.connect.ConnectLoginJobListModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class JobListConnectHomeAppsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context mContext;
    private final ArrayList<ConnectLoginJobListModel> jobList;
    private final OnJobSelectionClick launcher;
    private final ArrayList<ConnectLoginJobListModel> corruptJobs;
    private static final int NON_CORRUPT_JOB_VIEW = 1;
    private static final int CORRUPT_JOB_VIEW = 2;

    public JobListConnectHomeAppsAdapter(Context context, ArrayList<ConnectLoginJobListModel> jobList,
            ArrayList<ConnectLoginJobListModel> corruptJobs, OnJobSelectionClick launcher) {
        this.mContext = context;
        this.jobList = jobList;
        this.corruptJobs = corruptJobs;
        this.launcher = launcher;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the layout for each item using View Binding
        if (viewType == NON_CORRUPT_JOB_VIEW) {
            ItemLoginConnectHomeAppsBinding binding = ItemLoginConnectHomeAppsBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new NonCorruptJobViewHolder(binding);
        } else {
            ItemLoginConnectHomeCorruptAppsBinding binding = ItemLoginConnectHomeCorruptAppsBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new CorruptJobViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof NonCorruptJobViewHolder valid) {
            bind(mContext, valid.binding, jobList.get(position), launcher);
        } else if (holder instanceof CorruptJobViewHolder corrupt) {
            bind(corrupt.binding, corruptJobs.get(position - jobList.size()));
        }
    }

    @Override
    public int getItemCount() {
        return jobList.size() + corruptJobs.size();
    }

    @Override
    public int getItemViewType(int position) {
        return position < jobList.size() ? NON_CORRUPT_JOB_VIEW : CORRUPT_JOB_VIEW;
    }

    public static class NonCorruptJobViewHolder extends RecyclerView.ViewHolder {
        private final ItemLoginConnectHomeAppsBinding binding;

        public NonCorruptJobViewHolder(ItemLoginConnectHomeAppsBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    public static class CorruptJobViewHolder extends RecyclerView.ViewHolder {
        private final ItemLoginConnectHomeCorruptAppsBinding binding;

        public CorruptJobViewHolder(ItemLoginConnectHomeCorruptAppsBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static String formatDate(Date date) {
        SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM, yyyy", Locale.ENGLISH);
        return outputFormat.format(date);
    }

    public void bind(ItemLoginConnectHomeCorruptAppsBinding binding,
            ConnectLoginJobListModel connectLoginJobListModel) {
        binding.tvTitle.setText(connectLoginJobListModel.getName());
    }

    public void bind(Context mContext, ItemLoginConnectHomeAppsBinding binding,
            ConnectLoginJobListModel connectLoginJobListModel, OnJobSelectionClick launcher) {
        binding.tvTitle.setText(connectLoginJobListModel.getName());
        binding.tvDate.setText(formatDate(connectLoginJobListModel.getDate()));
        binding.imgDownload.setVisibility(connectLoginJobListModel.isAppInstalled() ? View.GONE : View.VISIBLE);
        handleProgressBarUI(mContext, connectLoginJobListModel, binding);
        configureJobType(mContext, connectLoginJobListModel, binding);

        clickListener(binding, connectLoginJobListModel, launcher);
    }

    private void clickListener(ItemLoginConnectHomeAppsBinding binding,
            ConnectLoginJobListModel connectLoginJobListModel, OnJobSelectionClick launcher) {
        binding.rootCardView.setOnClickListener(view -> {
            launcher.onClick(connectLoginJobListModel.getJob(), connectLoginJobListModel.isLearningApp(),
                    connectLoginJobListModel.getAppId(), connectLoginJobListModel.getJobType());
        });
    }

    public void handleProgressBarUI(Context context, ConnectLoginJobListModel item,
            ItemLoginConnectHomeAppsBinding binding) {
        int progress = 0;
        int progressColor = 0;
        ConnectLoginJobListModel.JobListEntryType jobType = item.getJobType();

        if (jobType == ConnectLoginJobListModel.JobListEntryType.LEARNING && !item.getJob().passedAssessment()) {
            progress = item.getLearningProgress();
            progressColor = ContextCompat.getColor(context, R.color.connect_blue_color);
        } else if (jobType == ConnectLoginJobListModel.JobListEntryType.DELIVERY && !item.getJob().isFinished()) {
            progress = item.getDeliveryProgress();
            progressColor = ContextCompat.getColor(context, R.color.connect_green);
        }

        if (progress > 0) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.progressBar.setProgress(progress);
            binding.progressBar.setProgressColor(progressColor);
        } else {
            binding.progressBar.setVisibility(View.GONE);
        }
    }

    private void configureJobType(Context context, ConnectLoginJobListModel item,
            ItemLoginConnectHomeAppsBinding binding) {
        if (item.isNew()) {
            setJobType(context, R.drawable.connect_rounded_corner_orange_yellow,
                    ContextCompat.getString(context, R.string.connect_new_opportunity),
                    R.drawable.ic_connect_new_opportunity,
                    255, R.color.connect_yellowish_orange_color, binding);
        } else if (item.isLearningApp()) {
            boolean passedAssessment = item.getJob().passedAssessment();
            int textId = passedAssessment ? R.string.connect_learn_review : R.string.connect_learn;
            int textColorId = passedAssessment ? R.color.connect_blue_color_50 : R.color.connect_blue_color;
            int iconAlpha = passedAssessment ? 128 : 255;
            setJobType(context, R.drawable.connect_rounded_corner_tealish_blue,
                    ContextCompat.getString(context, textId), R.drawable.ic_connect_learning, iconAlpha,
                    textColorId, binding);
        } else if (item.isDeliveryApp()) {
            boolean finished = item.getJob().isFinished();
            int textId = finished ? R.string.connect_expired : R.string.connect_delivery;
            int textColorId = finished ? R.color.connect_middle_grey : R.color.connect_green;
            int drawableId = finished ? R.drawable.connect_rounded_corner_lighter_grey
                    : R.drawable.connect_rounded_corner_light_green;
            int iconId = finished ? R.drawable.ic_connect_expired : R.drawable.ic_connect_delivery;

            setJobType(context, drawableId,
                    ContextCompat.getString(context, textId), iconId, 255,
                    textColorId, binding);
        }
    }

    private void setJobType(Context context, int backgroundResId, String jobTypeText,
            int iconResId, int iconAlpha, int textColorResId, ItemLoginConnectHomeAppsBinding binding) {
        binding.llOpportunity.setBackground(ContextCompat.getDrawable(context, backgroundResId));
        binding.tvJobType.setText(jobTypeText);
        binding.imgJobType.setImageDrawable(ContextCompat.getDrawable(context, iconResId));
        binding.imgJobType.setImageAlpha(iconAlpha);
        binding.tvJobType.setTextColor(ContextCompat.getColor(context, textColorResId));
    }
}

