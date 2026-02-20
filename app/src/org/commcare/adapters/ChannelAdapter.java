package org.commcare.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ItemChannelBinding;
import org.javarosa.core.model.utils.DateUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder> {

    private final List<ConnectMessagingChannelRecord> channels = new ArrayList<>();
    private final OnChannelClickListener clickListener;

    public ChannelAdapter(List<ConnectMessagingChannelRecord> channels, OnChannelClickListener clickListener) {
        this.clickListener = clickListener;
        setChannels(channels);
    }

    public void setChannels(List<ConnectMessagingChannelRecord> incomingChannels) {
        List<ConnectMessagingChannelRecord> subscribedChannels = new ArrayList<>();
        List<ConnectMessagingChannelRecord> unsubscribedChannels = new ArrayList<>();

        for (ConnectMessagingChannelRecord channel : incomingChannels) {
            if (channel.getConsented()) {
                subscribedChannels.add(channel);
            } else {
                unsubscribedChannels.add(channel);
            }
        }

        channels.clear();
        channels.addAll(subscribedChannels);
        channels.addAll(unsubscribedChannels);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChannelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ChannelViewHolder(ItemChannelBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelViewHolder holder, int position) {
        holder.bind(channels.get(position), clickListener);
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

        public void bind(ConnectMessagingChannelRecord channel, OnChannelClickListener clickListener) {
            binding.tvChannelName.setText(channel.getChannelName());

            Date lastDate = channel.getLastMessageDate();
            int unread = channel.getUnreadCount();

            boolean showDate = lastDate != null && channel.getConsented();
            if (showDate) {
                String lastText;
                if (DateUtils.dateDiff(new Date(), lastDate) == 0) {
                    lastText = DateUtils.formatTime(lastDate, DateUtils.FORMAT_HUMAN_READABLE_SHORT);
                } else {
                    SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM, yyyy", Locale.ENGLISH);
                    lastText = outputFormat.format(lastDate);
                }

                binding.tvLastChatTime.setText(lastText);
                binding.tvLastChatTime.setVisibility(View.VISIBLE);
            } else {
                binding.tvLastChatTime.setVisibility(View.GONE);
            }

            boolean showUnread = unread > 0 && channel.getConsented();
            if (showUnread) {
                binding.tvUnreadCount.setText(String.valueOf(unread));
                binding.tvUnreadCount.setVisibility(View.VISIBLE);
            } else {
                binding.tvUnreadCount.setVisibility(View.GONE);
            }

            binding.itemRootLayout.setOnClickListener(view -> {
                clickListener.onChannelClick(channel);
            });

            Context context = binding.getRoot().getContext();
            if (channel.getConsented()) {
                binding.tvUnsubscribedPill.setVisibility(View.GONE);
                binding.tvChannelName.setTextColor(ContextCompat.getColor(context, R.color.black));
                binding.tvChannelDescription.setVisibility(View.VISIBLE);
                binding.tvChannelDescription.setText(channel.getPreview());
            } else {
                binding.tvUnsubscribedPill.setVisibility(View.VISIBLE);
                binding.tvChannelName.setTextColor(ContextCompat.getColor(context, R.color.steel_dust));
                binding.tvChannelDescription.setVisibility(View.GONE);
            }
        }
    }

    public interface OnChannelClickListener {
        void onChannelClick(ConnectMessagingChannelRecord channel);
    }
}
