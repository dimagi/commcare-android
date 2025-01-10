package org.commcare.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.dalvik.databinding.ItemChannelBinding;
import org.javarosa.core.model.utils.DateUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

            Date lastDate = null;
            int unread = 0;
            for(ConnectMessagingMessageRecord message : channel.getMessages()) {
                if(lastDate == null || lastDate.before(message.getTimeStamp())) {
                    lastDate = message.getTimeStamp();
                }

                if(!message.getUserViewed()) {
                    unread++;
                }
            }

            boolean showDate = lastDate != null;
            binding.tvLastChatTime.setVisibility(showDate ? View.VISIBLE : View.GONE);
            if(showDate) {
                String lastText;
                if(DateUtils.dateDiff(new Date(), lastDate) == 0) {
                    lastText = DateUtils.formatTime(lastDate, DateUtils.FORMAT_HUMAN_READABLE_SHORT);
                } else {
                    SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM, yyyy", Locale.ENGLISH);
                    lastText = outputFormat.format(lastDate);
                }

                binding.tvLastChatTime.setText(lastText);
            }

            boolean showUnread = unread > 0;
            binding.tvUnreadCount.setVisibility(showUnread ? View.VISIBLE : View.GONE);
            if(showUnread) {
                binding.tvUnreadCount.setText(String.valueOf(unread));
            }

            binding.itemRootLayout.setOnClickListener(view -> {
                if (clickListener != null) {
                    clickListener.onChannelClick(channel);
                }
            });
        }
    }

    private static String formatDate(String dateStr) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM, yyyy", Locale.ENGLISH);
            Date date = inputFormat.parse(dateStr);
            return outputFormat.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    public interface OnChannelClickListener {
        void onChannelClick(ConnectMessagingChannelRecord channel);
    }
}
