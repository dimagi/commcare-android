package org.commcare.adapters;

import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ItemChatLeftViewBinding;
import org.commcare.dalvik.databinding.ItemChatRightViewBinding;
import org.commcare.fragments.connectMessaging.ConnectMessageChatData;
import org.commcare.utils.MarkupUtil;
import org.javarosa.core.model.utils.DateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ConnectMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int LEFTVIEW = 0;
    public static final int RIGHTVIEW = 1;
    private static final Object PAYLOAD_READ_STATUS = new Object();
    private List<ConnectMessageChatData> messages;

    public ConnectMessageAdapter(List<ConnectMessageChatData> messages) {
        this.messages = messages;
    }

    /**
     * Applies the new message list as a diff against the displayed one.
     *
     * @return true if the new list contains a message that was not already displayed
     */
    public boolean updateData(List<ConnectMessageChatData> newMessages) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(
                new MessageDiffCallback(messages, newMessages));
        messages = new ArrayList<>(newMessages);
        diff.dispatchUpdatesTo(this);

        for (int position = 0; position < newMessages.size(); position++) {
            if (diff.convertNewPositionToOld(position) == DiffUtil.DiffResult.NO_POSITION) {
                return true;
            }
        }
        return false;
    }

    public void addMessage(ConnectMessageChatData message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateMessageReadStatus(ConnectMessageChatData modifiedChat) {

        for (int messageIndex = messages.size() - 1; messageIndex >= 0; messageIndex--) {

            if (messages.get(messageIndex).getMessageId().equals(modifiedChat.getMessageId())) {
                messages.get(messageIndex).setMessageRead(modifiedChat.isMessageRead());
                notifyItemChanged(messageIndex, PAYLOAD_READ_STATUS);
                return;
            }
        }

    }

    private static class MessageDiffCallback extends DiffUtil.Callback {
        private final List<ConnectMessageChatData> oldMessages;
        private final List<ConnectMessageChatData> newMessages;

        MessageDiffCallback(List<ConnectMessageChatData> oldMessages,
                            List<ConnectMessageChatData> newMessages) {
            this.oldMessages = oldMessages;
            this.newMessages = newMessages;
        }

        @Override
        public int getOldListSize() {
            return oldMessages.size();
        }

        @Override
        public int getNewListSize() {
            return newMessages.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldMessages.get(oldItemPosition).getMessageId()
                    .equals(newMessages.get(newItemPosition).getMessageId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            ConnectMessageChatData oldChat = oldMessages.get(oldItemPosition);
            ConnectMessageChatData newChat = newMessages.get(newItemPosition);
            return hasSameDisplayContent(oldChat, newChat)
                    && oldChat.isMessageRead() == newChat.isMessageRead();
        }

        @Nullable
        @Override
        public Object getChangePayload(int oldItemPosition, int newItemPosition) {
            ConnectMessageChatData oldChat = oldMessages.get(oldItemPosition);
            ConnectMessageChatData newChat = newMessages.get(newItemPosition);
            return hasSameDisplayContent(oldChat, newChat) ? PAYLOAD_READ_STATUS : null;
        }

        private static boolean hasSameDisplayContent(ConnectMessageChatData oldChat,
                                                     ConnectMessageChatData newChat) {
            return oldChat.getType() == newChat.getType()
                    && Objects.equals(oldChat.getMessage(), newChat.getMessage())
                    && Objects.equals(oldChat.getTimestamp(), newChat.getTimestamp());
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
            if (binding instanceof ItemChatLeftViewBinding) {
                tvChatMessage = ((ItemChatLeftViewBinding)binding).tvChatMessage;
                tvChatDate = ((ItemChatLeftViewBinding)binding).tvChatDate;
            } else {
                tvChatMessage = ((ItemChatRightViewBinding)binding).tvChatMessage;
                tvChatDate = ((ItemChatRightViewBinding)binding).tvChatDate;
            }

            tvChatDate.setText(DateUtils.formatDateTime(chat.getTimestamp(), DateUtils.FORMAT_HUMAN_READABLE_SHORT));
            MarkupUtil.setMarkdown(tvChatMessage, builder, new SpannableStringBuilder());
            bindReadStatus(chat);
        }

        public void bindReadStatus(ConnectMessageChatData chat) {
            if (binding instanceof ItemChatRightViewBinding) {
                int resource = chat.isMessageRead()
                        ? R.drawable.ic_connect_message_read
                        : R.drawable.ic_connect_message_unread;
                ((ItemChatRightViewBinding)binding).imgMessageReadStatus.setImageResource(resource);
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
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_READ_STATUS)) {
            ((BaseMessageViewHolder)holder).bindReadStatus(messages.get(position));
        } else {
            onBindViewHolder(holder, position);
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
