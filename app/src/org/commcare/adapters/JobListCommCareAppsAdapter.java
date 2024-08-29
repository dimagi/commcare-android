package org.commcare.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.dalvik.databinding.ItemLoginCommcareAppsBinding;
import org.commcare.dalvik.databinding.ItemLoginConnectHomeAppsBinding;

public class JobListCommCareAppsAdapter extends RecyclerView.Adapter<JobListCommCareAppsAdapter.ViewHolder> {

    private Context mContext;

    public JobListCommCareAppsAdapter(Context context) {
        this.mContext = context;
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
        // Bind data to each item view
//        String item = items.get(position);
    }

    @Override
    public int getItemCount() {
        return 15;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemLoginCommcareAppsBinding binding;

        public ViewHolder(ItemLoginCommcareAppsBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
