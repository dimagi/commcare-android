package org.commcare.adapters;

import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import org.commcare.dalvik.databinding.ItemChatLeftViewBinding;
import org.commcare.dalvik.databinding.ItemChatRightViewBinding;
import org.commcare.fragments.connectMessaging.ConnectMessageChatData;
import org.commcare.utils.MarkupUtil;
import org.javarosa.core.model.utils.DateUtils;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class ConnectMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int LEFTVIEW = 0;
    public static final int RIGHTVIEW = 1;
    List<ConnectMessageChatData> messages;

    public ConnectMessageAdapter(List<ConnectMessageChatData> messages) {
        this.messages = messages;
    }

    public void updateData(List<ConnectMessageChatData> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    private static void bindCommon(TextView messageView, TextView userNameView, ConnectMessageChatData chat) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(chat.getMessage());
        MarkupUtil.setMarkdown(messageView, builder, new SpannableStringBuilder());
        userNameView.setText(DateUtils.formatDateTime(chat.getTimestamp(), DateUtils.FORMAT_HUMAN_READABLE_SHORT));
    }

    public static class LeftViewHolder extends RecyclerView.ViewHolder {
        ItemChatLeftViewBinding binding;

        public LeftViewHolder(ItemChatLeftViewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ConnectMessageChatData chat) {
            bindCommon(binding.tvChatMessage, binding.tvUserName, chat);
        }
    }

    public static class RightViewHolder extends RecyclerView.ViewHolder {
        ItemChatRightViewBinding binding;

        public RightViewHolder(ItemChatRightViewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ConnectMessageChatData chat) {
            bindCommon(binding.tvChatMessage, binding.tvUserName, chat);
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == LEFTVIEW) {
            ItemChatLeftViewBinding binding = ItemChatLeftViewBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new LeftViewHolder(binding);
        } else {
            ItemChatRightViewBinding binding = ItemChatRightViewBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new RightViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ConnectMessageChatData chat = messages.get(position);
        if (chat == null) {
            return;
        }
        if (getItemViewType(position) == LEFTVIEW) {
            ((LeftViewHolder)holder).bind(chat);
        } else {
            ((RightViewHolder)holder).bind(chat);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType() == LEFTVIEW ? LEFTVIEW : RIGHTVIEW;
    }
}
