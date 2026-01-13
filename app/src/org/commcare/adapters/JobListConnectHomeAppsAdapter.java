package org.commcare.adapters;


import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ConnectJobListItemBinding;
import org.commcare.dalvik.databinding.ConnectJobListItemCorruptBinding;
import org.commcare.dalvik.databinding.ConnectJobListItemSectionHeaderBinding;
import org.commcare.interfaces.OnJobSelectionClick;
import org.commcare.models.connect.ConnectJobListItem;
import org.commcare.models.connect.ConnectLoginJobListModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class JobListConnectHomeAppsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context mContext;
    private final OnJobSelectionClick launcher;

    private final ArrayList<ConnectJobListItem> displayItems = new ArrayList<>();

    private static final int VIEW_TYPE_SECTION_HEADER = 0;
    private static final int VIEW_TYPE_CORRUPT_JOB = 1;
    private static final int VIEW_TYPE_NON_CORRUPT_JOB = 2;

    public JobListConnectHomeAppsAdapter(
            Context context,
            ArrayList<ConnectLoginJobListModel> inProgressJobs,
            ArrayList<ConnectLoginJobListModel> newJobs,
            ArrayList<ConnectLoginJobListModel> completedJobs,
            ArrayList<ConnectLoginJobListModel> corruptJobs,
            OnJobSelectionClick launcher
    ) {
        this.mContext = context;
        this.launcher = launcher;
        buildDisplayList(inProgressJobs, newJobs, completedJobs, corruptJobs);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        return switch (viewType) {
            case VIEW_TYPE_SECTION_HEADER -> new SectionHeaderViewHolder(
                    ConnectJobListItemSectionHeaderBinding.inflate(inflater, parent, false)
            );
            case VIEW_TYPE_CORRUPT_JOB -> new CorruptJobViewHolder(
                    ConnectJobListItemCorruptBinding.inflate(inflater, parent, false)
            );
            default -> new NonCorruptJobViewHolder(
                    ConnectJobListItemBinding.inflate(inflater, parent, false)
            );
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ConnectJobListItem displayItem = displayItems.get(position);

        // Handle the section headers.
        if (holder instanceof SectionHeaderViewHolder sectionHeaderViewHolder) {
            ConnectJobListItem.SectionHeader header = (ConnectJobListItem.SectionHeader) displayItem;
            boolean showSectionDivider = position > 0;
            bind(sectionHeaderViewHolder.binding, header.getTextResID(), showSectionDivider);
            return;
        }

        // Handle the job items.
        ConnectJobListItem.JobItem jobItem = (ConnectJobListItem.JobItem) displayItem;
        if (holder instanceof CorruptJobViewHolder corruptJobViewHolder) {
            bind(corruptJobViewHolder.binding, jobItem.getJobModel());
        } else if (holder instanceof NonCorruptJobViewHolder nonCorruptJobViewHolder) {
            bind(mContext, nonCorruptJobViewHolder.binding, jobItem.getJobModel(), launcher);
        }
    }

    @Override
    public int getItemCount() {
        return displayItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        ConnectJobListItem displayItem = displayItems.get(position);

        // Handle the section headers.
        if (displayItem instanceof ConnectJobListItem.SectionHeader) {
            return VIEW_TYPE_SECTION_HEADER;
        }

        // Handle the job items.
        if (((ConnectJobListItem.JobItem) displayItem).isCorrupt()) {
            return VIEW_TYPE_CORRUPT_JOB;
        } else {
            return VIEW_TYPE_NON_CORRUPT_JOB;
        }
    }

    public static class NonCorruptJobViewHolder extends RecyclerView.ViewHolder {
        private final ConnectJobListItemBinding binding;

        public NonCorruptJobViewHolder(ConnectJobListItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    public static class CorruptJobViewHolder extends RecyclerView.ViewHolder {
        private final ConnectJobListItemCorruptBinding binding;

        public CorruptJobViewHolder(ConnectJobListItemCorruptBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    public static class SectionHeaderViewHolder extends RecyclerView.ViewHolder {
        private final ConnectJobListItemSectionHeaderBinding binding;

        public SectionHeaderViewHolder(ConnectJobListItemSectionHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static String formatDate(Date date) {
        SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM, yyyy", Locale.ENGLISH);
        return outputFormat.format(date);
    }

    public void bind(
            ConnectJobListItemCorruptBinding binding,
            ConnectLoginJobListModel connectLoginJobListModel
    ) {
        binding.tvTitle.setText(connectLoginJobListModel.getName());
    }

    public void bind(
            Context mContext,
            ConnectJobListItemBinding binding,
            ConnectLoginJobListModel connectLoginJobListModel,
            OnJobSelectionClick launcher
    ) {
        binding.tvTitle.setText(connectLoginJobListModel.getName());
        if (shouldShowDateInRed(connectLoginJobListModel.getDate())) {
            int redColor = ContextCompat.getColor(mContext, R.color.dark_red_brick_red);

            binding.tvDate.setTextColor(redColor);
            binding.ivInfo.setColorFilter(redColor, PorterDuff.Mode.SRC_IN);
        }
        boolean isCompleted = connectLoginJobListModel.getJob().isFinished();
        int dateRes = isCompleted
                ? R.string.personalid_expired_expired_on
                : R.string.personalid_complete_by;
        binding.tvDate.setText(
                mContext.getString(dateRes, formatDate(connectLoginJobListModel.getDate()))
        );

        Drawable startDrawable = connectLoginJobListModel.isAppInstalled()
                ? null
                : ContextCompat.getDrawable(mContext, R.drawable.ic_download_circle);

        binding.btnResume.setCompoundDrawablesRelativeWithIntrinsicBounds(
                startDrawable, null, null, null
        );

        handleProgressBarUI(mContext, connectLoginJobListModel, binding);
        configureJobType(mContext, connectLoginJobListModel, binding);

        clickListener(binding, connectLoginJobListModel, launcher);
    }

    private boolean shouldShowDateInRed(Date expiryDate) {
        long now = System.currentTimeMillis();
        long diffMillis = expiryDate.getTime() - now;

        long daysRemaining = TimeUnit.MILLISECONDS.toDays(diffMillis);

        return daysRemaining >= 0 && daysRemaining <= 5;
    }


    public void bind(
            ConnectJobListItemSectionHeaderBinding binding,
            @StringRes int headerTextResId,
            boolean showSectionDivider
    ) {
        binding.tvSectionHeader.setText(mContext.getString(headerTextResId));
        binding.vSectionDivider.setVisibility(showSectionDivider ? View.VISIBLE : View.GONE);
    }

    private void clickListener(
            ConnectJobListItemBinding binding,
            ConnectLoginJobListModel connectLoginJobListModel,
            OnJobSelectionClick launcher
    ) {
        binding.btnResume.setOnClickListener(view -> {
            launcher.onClick(connectLoginJobListModel.getJob(), connectLoginJobListModel.isLearningApp(),
                    connectLoginJobListModel.getAppId(), connectLoginJobListModel.getJobType());
        });
    }

    public void handleProgressBarUI(
            Context context,
            ConnectLoginJobListModel item,
            ConnectJobListItemBinding binding
    ) {
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
            binding.groupProgress.setVisibility(View.VISIBLE);
            binding.tvProgressPercent.setText(progress +" %");
        } else {
            binding.progressBar.setVisibility(View.GONE);
            binding.groupProgress.setVisibility(View.GONE);
        }
    }

    private void configureJobType(
            Context context,
            ConnectLoginJobListModel item,
            ConnectJobListItemBinding binding
    ) {
        if (item.isNew()) {
            setJobType(context, 255, binding);
        } else if (item.isLearningApp()) {
            setJobType(context, R.drawable.ic_connect_learning, binding);
        } else if (item.isDeliveryApp()) {
            boolean finished = item.getJob().isFinished();
            int iconId = finished ? R.drawable.ic_connect_expired : R.drawable.ic_connect_delivery;

            setJobType(context, iconId, binding);
        }
    }

    private void setJobType(Context context,
                            int iconResId, ConnectJobListItemBinding binding) {
        binding.imgJobType.setImageDrawable(ContextCompat.getDrawable(context, iconResId));
    }

    private void buildDisplayList(
            List<ConnectLoginJobListModel> inProgressJobs,
            List<ConnectLoginJobListModel> newJobs,
            List<ConnectLoginJobListModel> completedJobs,
            List<ConnectLoginJobListModel> corruptJobs
    ) {
        if (!inProgressJobs.isEmpty()) {
            displayItems.add(new ConnectJobListItem.SectionHeader(R.string.connect_in_progress));
            for (ConnectLoginJobListModel inProgressJob : inProgressJobs) {
                displayItems.add(new ConnectJobListItem.JobItem(inProgressJob, false));
            }
        }

        if (!newJobs.isEmpty()) {
            displayItems.add(new ConnectJobListItem.SectionHeader(R.string.connect_new_opportunities));
            for (ConnectLoginJobListModel newJob : newJobs) {
                displayItems.add(new ConnectJobListItem.JobItem(newJob, false));
            }
        }

        if (!completedJobs.isEmpty()) {
            displayItems.add(new ConnectJobListItem.SectionHeader(R.string.connect_completed));
            for (ConnectLoginJobListModel completedJob : completedJobs) {
                displayItems.add(new ConnectJobListItem.JobItem(completedJob, false));
            }
        }

        for (ConnectLoginJobListModel corruptJob : corruptJobs) {
            displayItems.add(new ConnectJobListItem.JobItem(corruptJob, true));
        }
    }
}

