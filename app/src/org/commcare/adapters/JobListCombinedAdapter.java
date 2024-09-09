package org.commcare.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ItemLoginCommcareAppsBinding;
import org.commcare.dalvik.databinding.ItemLoginConnectHomeAppsBinding;
import org.commcare.models.connect.ConnectCombineJobListModel;

import java.util.List;

public class JobListCombinedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_COMMCARE = 0;
    public static final int VIEW_TYPE_CONNECT_HOME = 1;
    private final List<ConnectCombineJobListModel> items;
    private final Context mContext;

    public JobListCombinedAdapter(Context context, List<ConnectCombineJobListModel> items) {
        this.mContext = context;
        this.items = items;
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
            ((CommCareViewHolder) holder).bind(item.getConnectLoginJobListModel().getName());
        } else if (holder instanceof ConnectHomeViewHolder) {
            ((ConnectHomeViewHolder) holder).bind(mContext, item);
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

        public void bind(String name) {
            binding.tvTitle.setText(name);
        }
    }

    // Applying SRP - ConnectHomeViewHolder only handles Connect Home item binding logic
    static class ConnectHomeViewHolder extends RecyclerView.ViewHolder {
        private final ItemLoginConnectHomeAppsBinding binding;

        public ConnectHomeViewHolder(ItemLoginConnectHomeAppsBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Context context, ConnectCombineJobListModel item) {
            binding.tvTitle.setText(item.getConnectLoginJobListModel().getName());
            configureJobType(context, item);
        }

        private void configureJobType(Context context, ConnectCombineJobListModel item) {
            if (item.getConnectLoginJobListModel().isNew()) {
                setJobType(context, R.drawable.connect_rounded_corner_orange_yellow,
                        "New Opportunity", R.drawable.ic_connect_new_opportunity,
                        R.color.connect_yellowish_orange_color);
            } else if (item.getConnectLoginJobListModel().isLeaningApp()) {
                setJobType(context, R.drawable.connect_rounded_corner_teslish_blue,
                        "Learn", R.drawable.ic_connect_learning,
                        R.color.connect_blue_color);
            } else if (item.getConnectLoginJobListModel().isDeliveryApp()) {
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