package org.commcare.adapters;

import static org.commcare.activities.LoginActivity.JOB_DELIVERY;
import static org.commcare.activities.LoginActivity.JOB_LEARNING;
import static org.commcare.activities.LoginActivity.JOB_NEW_OPPORTUNITY;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.activities.LoginActivity;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ItemLoginCommcareAppsBinding;
import org.commcare.dalvik.databinding.ItemLoginConnectHomeAppsBinding;
import org.commcare.interfaces.JobListCallBack;
import org.commcare.models.connect.ConnectCombineJobListModel;
import org.commcare.models.connect.ConnectLoginJobListModel;

import java.util.List;

public class JobListCombinedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_COMMCARE = 0;
    public static final int VIEW_TYPE_CONNECT_HOME = 1;
    private final List<ConnectCombineJobListModel> items;
    private final Context mContext;
    private final JobListCallBack mCallback;

    public JobListCombinedAdapter(Context context, List<ConnectCombineJobListModel> items, JobListCallBack mCallback) {
        this.mContext = context;
        this.items = items;
        this.mCallback = mCallback;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_COMMCARE) {
            return new CommCareViewHolder(ItemLoginCommcareAppsBinding.inflate(inflater, parent, false));
        } else {
            return new ConnectHomeViewHolder(ItemLoginConnectHomeAppsBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ConnectCombineJobListModel item = items.get(position);
        if (holder instanceof CommCareViewHolder) {
            ((CommCareViewHolder) holder).bind(item.getConnectLoginJobListModel(), mCallback);
        } else if (holder instanceof ConnectHomeViewHolder) {
            ((ConnectHomeViewHolder) holder).bind(mContext, item, mCallback);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getListType() == VIEW_TYPE_COMMCARE ? VIEW_TYPE_COMMCARE : VIEW_TYPE_CONNECT_HOME;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class CommCareViewHolder extends RecyclerView.ViewHolder {
        private final ItemLoginCommcareAppsBinding binding;

        public CommCareViewHolder(ItemLoginCommcareAppsBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ConnectLoginJobListModel item, JobListCallBack mCallback) {
            binding.tvTitle.setText(item.getName());

            binding.rootCardView.setOnClickListener(view -> {
                mCallback.onClick(item.getId(),item.getAppId(), item.getName(), LoginActivity.SELECTED_COMM_CARE_JOB);
            });
        }
    }

    // Applying SRP - ConnectHomeViewHolder only handles Connect Home item binding logic
    static class ConnectHomeViewHolder extends RecyclerView.ViewHolder {
        private final ItemLoginConnectHomeAppsBinding binding;

        public ConnectHomeViewHolder(ItemLoginConnectHomeAppsBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Context context, ConnectCombineJobListModel item, JobListCallBack mCallback) {
            binding.tvTitle.setText(item.getConnectLoginJobListModel().getName());
            binding.tvDate.setText(LoginActivity.formatDate(item.getConnectLoginJobListModel().getLastAccessed().toString()));
            handleProgressBarUI(context, item);
            configureJobType(context, item);

            clickListener(context, item, mCallback);
        }

        private void clickListener(Context context, ConnectCombineJobListModel item, JobListCallBack mCallback) {
            String jobType = item.getConnectLoginJobListModel().getJobType();
            String appType;
            if (jobType.equals(JOB_LEARNING) || jobType.equals(JOB_NEW_OPPORTUNITY)) {
                appType = LoginActivity.LEARN_APP;
            } else {
                appType = LoginActivity.DELIVERY_APP;
            }
            binding.rootCardView.setOnClickListener(view -> {
                mCallback.onClick(item.getConnectLoginJobListModel().getId(),item.getConnectLoginJobListModel().getAppId(), item.getConnectLoginJobListModel().getName(), appType);
            });
        }

        public void handleProgressBarUI(Context context, ConnectCombineJobListModel item) {
            int progress = 0;
            int progressColor = 0;
            String jobType = item.getConnectLoginJobListModel().getJobType();

            if (jobType.equals(JOB_LEARNING)) {
                progress = item.getConnectLoginJobListModel().getLearningProgress();
                progressColor = context.getResources().getColor(R.color.connect_blue_color);
            } else if (jobType.equals(JOB_DELIVERY)) {
                progress = item.getConnectLoginJobListModel().getDeliveryProgress();
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

        private void configureJobType(Context context, ConnectCombineJobListModel item) {
            if (item.getConnectLoginJobListModel().isNew()) {
                setJobType(context, R.drawable.connect_rounded_corner_orange_yellow,
                        context.getResources().getString(R.string.connect_new_opportunity), R.drawable.ic_connect_new_opportunity,
                        R.color.connect_yellowish_orange_color);
            } else if (item.getConnectLoginJobListModel().isLeaningApp()) {
                setJobType(context, R.drawable.connect_rounded_corner_teslish_blue,
                        context.getResources().getString(R.string.connect_learn), R.drawable.ic_connect_learning,
                        R.color.connect_blue_color);
            } else if (item.getConnectLoginJobListModel().isDeliveryApp()) {
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