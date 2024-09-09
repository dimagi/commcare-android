package org.commcare.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ItemLoginConnectHomeAppsBinding;
import org.commcare.models.connect.ConnectLoginJobListModel;

import java.util.ArrayList;

public class JobListConnectHomeAppsAdapter extends RecyclerView.Adapter<JobListConnectHomeAppsAdapter.ViewHolder> {

    private final Context mContext;
    private final ArrayList<ConnectLoginJobListModel> jobList;

    public JobListConnectHomeAppsAdapter(Context context, ArrayList<ConnectLoginJobListModel> jobList) {
        this.mContext = context;
        this.jobList = jobList;
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
        holder.bind(mContext, jobList.get(position));
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

        public void bind(Context mContext, ConnectLoginJobListModel connectLoginJobListModel) {
            binding.tvTitle.setText(connectLoginJobListModel.getName());
            configureJobType(mContext, connectLoginJobListModel);
        }

        private void configureJobType(Context context, ConnectLoginJobListModel item) {
            if (item.isNew()) {
                setJobType(context, R.drawable.connect_rounded_corner_orange_yellow,
                        "New Opportunity", R.drawable.ic_connect_new_opportunity,
                        R.color.connect_yellowish_orange_color);
            } else if (item.isLeaningApp()) {
                setJobType(context, R.drawable.connect_rounded_corner_teslish_blue,
                        "Learn", R.drawable.ic_connect_learning,
                        R.color.connect_blue_color);
            } else if (item.isDeliveryApp()) {
                setJobType(context, R.drawable.connect_rounded_corner_light_green,
                        "Delivery", R.drawable.ic_connect_delivery,
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
