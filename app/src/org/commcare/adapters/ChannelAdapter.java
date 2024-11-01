package org.commcare.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.dalvik.databinding.ItemChannelBinding;

import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder> {

    private List<ConnectMessagingChannelRecord> channels;
    private final OnChannelClickListener clickListener;

    public ChannelAdapter(List<ConnectMessagingChannelRecord> channels,  OnChannelClickListener clickListener) {
        this.channels = channels;
        this.clickListener = clickListener;
    }

    public void setChannels(List<ConnectMessagingChannelRecord> channels) {
        this.channels = channels;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChannelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ChannelViewHolder(ItemChannelBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelViewHolder holder, int position) {
        holder.bind(holder.binding, channels.get(position), clickListener);
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    static class ChannelViewHolder extends RecyclerView.ViewHolder {

        private final ItemChannelBinding binding;

        public ChannelViewHolder(@NonNull ItemChannelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ItemChannelBinding binding, ConnectMessagingChannelRecord channel, OnChannelClickListener clickListener) {
            binding.tvChannelName.setText(channel.getChannelName());

            if(!channel.getConsented()) {
                binding.tvChannelDescription.setText("Unconsented channel");
            } else {
                binding.tvChannelDescription.setVisibility(View.GONE);
            }

            //binding.tvLastChatTime.setText("");
            //binding.tvUnreadCount.setText("");

            binding.itemRootLayout.setOnClickListener(view -> {
                if (clickListener != null) {
                    clickListener.onChannelClick(channel);
                }
            });
        }
    }

    public interface OnChannelClickListener {
        void onChannelClick(ConnectMessagingChannelRecord channel);
    }
}
