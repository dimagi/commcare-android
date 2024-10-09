package org.commcare.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.dalvik.databinding.ItemChannelBinding;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder> {

    private final OnChannelClickListener clickListener;

    public ChannelAdapter(OnChannelClickListener clickListener) {
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ChannelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ChannelViewHolder(ItemChannelBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelViewHolder holder, int position) {
        holder.bind(holder.binding, position, clickListener);
    }

    @Override
    public int getItemCount() {
        return 20;
    }

    static class ChannelViewHolder extends RecyclerView.ViewHolder {

        private final ItemChannelBinding binding;

        public ChannelViewHolder(@NonNull ItemChannelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ItemChannelBinding binding, int position, OnChannelClickListener clickListener) {
            binding.itemRootLayout.setOnClickListener(view -> {
                if (clickListener != null) {
                    clickListener.onChannelClick(position);
                }
            });
        }
    }

    public interface OnChannelClickListener {
        void onChannelClick(int position);
    }
}
