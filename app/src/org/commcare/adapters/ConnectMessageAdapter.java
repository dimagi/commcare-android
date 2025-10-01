package org.commcare.adapters;

import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ItemChatLeftViewBinding;
import org.commcare.dalvik.databinding.ItemChatRightViewBinding;
import org.commcare.fragments.connectMessaging.ConnectMessageChatData;
import org.commcare.utils.MarkupUtil;
import org.javarosa.core.model.utils.DateUtils;

import java.util.List;

public class ConnectMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int LEFTVIEW = 0;
    public static final int RIGHTVIEW = 1;
    private List<ConnectMessageChatData> messages;

    public ConnectMessageAdapter(List<ConnectMessageChatData> messages) {
        this.messages = messages;
    }

    public void updateData(List<ConnectMessageChatData> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    public void addMessage(ConnectMessageChatData message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateMessageReadStatus(ConnectMessageChatData modifiedChat) {

        for (int messageIndex = messages.size() - 1; messageIndex >= 0; messageIndex--) {

            if (messages.get(messageIndex).getMessageId().equals(modifiedChat.getMessageId())) {
                messages.get(messageIndex).setMessageRead(modifiedChat.isMessageRead());
                notifyItemChanged(messageIndex);
                return;
            }
        }

    }

    public class LeftViewHolder extends BaseMessageViewHolder {
        public LeftViewHolder(ItemChatLeftViewBinding binding) {
            super(binding);
        }
    }

    public class RightViewHolder extends BaseMessageViewHolder {

        public RightViewHolder(ItemChatRightViewBinding binding) {
            super(binding);
        }
    }

    public class BaseMessageViewHolder extends RecyclerView.ViewHolder {
        ViewBinding binding;

        public BaseMessageViewHolder(ViewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ConnectMessageChatData chat) {
            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(chat.getMessage());

            TextView tvChatMessage;
            TextView tvChatDate;
            ImageView ivReadStatus = null;
            if (binding instanceof ItemChatLeftViewBinding) {
                tvChatMessage = ((ItemChatLeftViewBinding)binding).tvChatMessage;
                tvChatDate = ((ItemChatLeftViewBinding)binding).tvChatDate;
            } else {
                tvChatMessage = ((ItemChatRightViewBinding)binding).tvChatMessage;
                tvChatDate = ((ItemChatRightViewBinding)binding).tvChatDate;
                ivReadStatus = ((ItemChatRightViewBinding)binding).imgMessageReadStatus;
            }

            tvChatDate.setText(DateUtils.formatDateTime(chat.getTimestamp(), DateUtils.FORMAT_HUMAN_READABLE_SHORT));
            MarkupUtil.setMarkdown(tvChatMessage, builder, new SpannableStringBuilder());
            if (ivReadStatus != null) {
                int resource = chat.isMessageRead() ? R.drawable.ic_connect_message_read : R.drawable.ic_connect_message_unread;
                ivReadStatus.setImageResource(resource);
            }
        }
    }

    @NonNull
    @Override
    public BaseMessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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
