package org.commcare.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.dalvik.databinding.ItemLoginCommcareAppsBinding;
import org.commcare.models.connect.ConnectLoginJobListModel;

import java.util.ArrayList;

public class JobListCommCareAppsAdapter extends RecyclerView.Adapter<JobListCommCareAppsAdapter.ViewHolder> {

    private final Context mContext;
    private final ArrayList<ConnectLoginJobListModel> traditionalJobList;

    public JobListCommCareAppsAdapter(Context context, ArrayList<ConnectLoginJobListModel> traditionalJobList) {
        this.mContext = context;
        this.traditionalJobList = traditionalJobList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the layout for each item using View Binding
        ItemLoginCommcareAppsBinding binding = ItemLoginCommcareAppsBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(mContext, traditionalJobList.get(position));
    }

    @Override
    public int getItemCount() {
        return traditionalJobList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemLoginCommcareAppsBinding binding;

        public ViewHolder(ItemLoginCommcareAppsBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Context mContext, ConnectLoginJobListModel connectLoginJobListModel) {
            binding.tvTitle.setText(connectLoginJobListModel.getName());
        }
    }
}
