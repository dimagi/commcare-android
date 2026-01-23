package org.commcare.adapters;


import android.content.Context;
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
        binding.tvDate.setText(formatDate(connectLoginJobListModel.getDate()));
        binding.imgDownload.setVisibility(connectLoginJobListModel.isAppInstalled() ? View.GONE : View.VISIBLE);
        handleProgressBarUI(mContext, connectLoginJobListModel, binding);
        configureJobType(mContext, connectLoginJobListModel, binding);

        clickListener(binding, connectLoginJobListModel, launcher);
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
        binding.rootCardView.setOnClickListener(view -> {
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
        } else {
            binding.progressBar.setVisibility(View.GONE);
        }
    }

    private void configureJobType(
            Context context,
            ConnectLoginJobListModel item,
            ConnectJobListItemBinding binding
    ) {
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

    private void setJobType(
            Context context,
            int backgroundResId,
            String jobTypeText,
            int iconResId,
            int iconAlpha,
            int textColorResId,
            ConnectJobListItemBinding binding
    ) {
        binding.llOpportunity.setBackground(ContextCompat.getDrawable(context, backgroundResId));
        binding.tvJobType.setText(jobTypeText);
        binding.imgJobType.setImageDrawable(ContextCompat.getDrawable(context, iconResId));
        binding.imgJobType.setImageAlpha(iconAlpha);
        binding.tvJobType.setTextColor(ContextCompat.getColor(context, textColorResId));
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

