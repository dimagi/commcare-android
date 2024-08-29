package org.commcare.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.dalvik.databinding.ItemLoginCommcareAppsBinding;
import org.commcare.dalvik.databinding.ItemLoginConnectHomeAppsBinding;
import org.commcare.testingModel.CommCareItem;

import java.util.List;

public class JobListCombinedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_COMMCARE = 0;
    private static final int VIEW_TYPE_CONNECT_HOME = 1;
    private List<Object> items; // Your data source
    private Context mContext;

    public JobListCombinedAdapter(Context context, List<Object> items) {
        this.mContext = context;
        this.items = items;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == VIEW_TYPE_COMMCARE) {
            ItemLoginCommcareAppsBinding binding = ItemLoginCommcareAppsBinding.inflate(inflater, parent, false);
            return new CommCareViewHolder(binding);
        } else {
            ItemLoginConnectHomeAppsBinding binding = ItemLoginConnectHomeAppsBinding.inflate(inflater, parent, false);
            return new ConnectHomeViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int viewType = getItemViewType(position);

        if (viewType == VIEW_TYPE_COMMCARE) {
            // Bind data for CommCareViewHolder
            CommCareViewHolder viewHolder = (CommCareViewHolder) holder;
             viewHolder.bind();
        } else {
            // Bind data for ConnectHomeViewHolder
            ConnectHomeViewHolder viewHolder = (ConnectHomeViewHolder) holder;
             viewHolder.bind();
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof CommCareItem ? VIEW_TYPE_COMMCARE : VIEW_TYPE_CONNECT_HOME;
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

        public void bind() {}
    }

    static class ConnectHomeViewHolder extends RecyclerView.ViewHolder {
        private final ItemLoginConnectHomeAppsBinding binding;

        public ConnectHomeViewHolder(ItemLoginConnectHomeAppsBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind() {}
    }
}
