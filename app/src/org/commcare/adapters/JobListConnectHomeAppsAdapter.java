package org.commcare.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DimenRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ConnectJobListItemBinding;
import org.commcare.dalvik.databinding.ConnectJobListItemSectionHeaderBinding;
import org.commcare.interfaces.OnJobCardClick;
import org.commcare.models.connect.ConnectJobListItem;
import org.commcare.models.connect.ConnectLoginJobListModel;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import static org.commcare.connect.ConnectDateUtils.formatDate;
import static org.commcare.connect.database.ConnectJobUtils.isExpiryDateUnderFiveDays;

public class JobListConnectHomeAppsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context mContext;
    private final OnJobCardClick launcher;

    private final ArrayList<ConnectJobListItem> displayItems = new ArrayList<>();

    private static final int VIEW_TYPE_SECTION_HEADER = 0;
    private static final int VIEW_TYPE_OPPORTUNITY = 1;

    public JobListConnectHomeAppsAdapter(
            Context context,
            ArrayList<ConnectLoginJobListModel> inProgressJobs,
            ArrayList<ConnectLoginJobListModel> newJobs,
            ArrayList<ConnectLoginJobListModel> completedJobs,
            OnJobCardClick launcher
    ) {
        this.mContext = context;
        this.launcher = launcher;
        buildDisplayList(inProgressJobs, newJobs, completedJobs);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        return switch (viewType) {
            case VIEW_TYPE_SECTION_HEADER -> new SectionHeaderViewHolder(
                    ConnectJobListItemSectionHeaderBinding
                            .inflate(inflater, parent, false)
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
            ConnectJobListItem.SectionHeader header =
                    (ConnectJobListItem.SectionHeader) displayItem;
            bind(sectionHeaderViewHolder.binding, header.getTextResID());
            return;
        }

        // Handle the job items.
        ConnectJobListItem.JobItem jobItem = (ConnectJobListItem.JobItem) displayItem;
        if (holder instanceof NonCorruptJobViewHolder nonCorruptJobViewHolder) {
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

        return VIEW_TYPE_OPPORTUNITY;
    }

    public static class NonCorruptJobViewHolder extends RecyclerView.ViewHolder {
        private final ConnectJobListItemBinding binding;

        public NonCorruptJobViewHolder(ConnectJobListItemBinding binding) {
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
    public void bind(
            Context mContext,
            ConnectJobListItemBinding binding,
            ConnectLoginJobListModel connectLoginJobListModel,
            OnJobCardClick launcher
    ) {
        binding.getRoot().setTag("opp_uuid: " + connectLoginJobListModel.getUuid());
        binding.tvTitle.setText(connectLoginJobListModel.getName());

        bindDate(binding, connectLoginJobListModel);
        bindBadge(mContext, binding, connectLoginJobListModel);

        binding.getRoot().setOnClickListener(view -> launcher.onClick(connectLoginJobListModel));
    }

    public void bind(
            ConnectJobListItemSectionHeaderBinding binding,
            @StringRes int headerTextResId
    ) {
        binding.tvSectionHeader.setText(mContext.getString(headerTextResId));
    }

    private void bindDate(
            ConnectJobListItemBinding binding,
            ConnectLoginJobListModel item
    ) {
        int labelRes;
        if (!item.getJobFinished()) {
            labelRes = R.string.connect_label_expiry;
        } else if (item.getUserCompletedDelivery()) {
            labelRes = R.string.connect_label_completed_on;
        } else {
            labelRes = R.string.connect_label_expired_on;
        }
        binding.tvDateLabel.setText(labelRes);
        binding.tvDate.setText(formatDate(item.getDate(), DateFormat.SHORT));

        boolean expiringSoon = isExpiryDateUnderFiveDays(item.getDate());
        int dateColor = MaterialColors.getColor(
                binding.getRoot(),
                expiringSoon ? R.attr.connectStatusNegative : R.attr.connectOnSurfaceVariant
        );
        binding.tvDate.setTextColor(dateColor);
        binding.ivInfo.setVisibility(expiringSoon ? View.VISIBLE : View.GONE);
    }

    private void bindBadge(
            Context context,
            ConnectJobListItemBinding binding,
            ConnectLoginJobListModel item
    ) {
        if (item.isNew()) {
            showBadgeWithoutRing(context, binding, R.drawable.ic_connect_new_opportunity);
            return;
        }

        if (item.getJobFinished()) {
            showBadgeWithoutRing(context, binding, item.getUserCompletedDelivery()
                    ? R.drawable.ic_connect_completed_badge
                    : R.drawable.ic_connect_expired_badge);
            return;
        }

        int progress;
        int progressColor;
        int iconRes;
        if (item.isLearningApp()) {
            progress = item.getLearningProgress();
            progressColor = MaterialColors.getColor(
                    binding.getRoot(),
                    com.google.android.material.R.attr.colorPrimary
            );
            iconRes = R.drawable.ic_connect_learning;
        } else {
            progress = item.getDeliveryProgress();
            progressColor = MaterialColors.getColor(binding.getRoot(), R.attr.connectStatusPositive);
            iconRes = R.drawable.ic_connect_delivery;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.progressBar.setStrokeWidth(
                context.getResources().getDimensionPixelSize(R.dimen.connect_job_badge_ring_stroke)
        );
        binding.progressBar.setProgress(progress);
        binding.progressBar.setProgressColor(progressColor);

        setBadgeIcon(context, binding, iconRes, R.dimen.connect_job_badge_icon_in_ring);
    }

    private void showBadgeWithoutRing(
            Context context,
            ConnectJobListItemBinding binding,
            @DrawableRes int iconRes
    ) {
        // INVISIBLE rather than GONE: the icon is centred on the ring, so the ring has to keep
        // occupying its box for the icon to stay put and for card heights to match across states.
        binding.progressBar.setVisibility(View.INVISIBLE);
        setBadgeIcon(context, binding, iconRes, R.dimen.connect_job_badge_icon_standalone);
    }

    private void setBadgeIcon(
            Context context,
            ConnectJobListItemBinding binding,
            @DrawableRes int iconRes,
            @DimenRes int sizeRes
    ) {
        int size = context.getResources().getDimensionPixelSize(sizeRes);
        ViewGroup.LayoutParams params = binding.imgJobType.getLayoutParams();
        params.width = size;
        params.height = size;
        binding.imgJobType.setLayoutParams(params);
        binding.imgJobType.setImageDrawable(ContextCompat.getDrawable(context, iconRes));
    }

    private void buildDisplayList(
            List<ConnectLoginJobListModel> inProgressJobs,
            List<ConnectLoginJobListModel> newJobs,
            List<ConnectLoginJobListModel> completedJobs
    ) {
        displayItems.clear();

        if (!inProgressJobs.isEmpty()) {
            displayItems.add(new ConnectJobListItem.SectionHeader(R.string.connect_in_progress));
            for (ConnectLoginJobListModel jobListModel : inProgressJobs) {
                displayItems.add(new ConnectJobListItem.JobItem(jobListModel));
            }
        }

        if (!newJobs.isEmpty()) {
            displayItems.add(
                    new ConnectJobListItem.SectionHeader(R.string.connect_new_opportunities));
            for (ConnectLoginJobListModel jobListModel : newJobs) {
                displayItems.add(new ConnectJobListItem.JobItem(jobListModel));
            }
        }

        if (!completedJobs.isEmpty()) {
            displayItems.add(new ConnectJobListItem.SectionHeader(R.string.connect_completed_expired_label));
            for (ConnectLoginJobListModel jobListModel : completedJobs) {
                displayItems.add(new ConnectJobListItem.JobItem(jobListModel));
            }
        }
    }
}
