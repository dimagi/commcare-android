package org.commcare.adapters;

import android.text.SpannableStringBuilder;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ItemChatLeftRichViewBinding;
import org.commcare.dalvik.databinding.ItemChatLeftViewBinding;
import org.commcare.dalvik.databinding.ItemChatRightViewBinding;
import org.commcare.fragments.connectMessaging.ConnectMessageAttachmentListener;
import org.commcare.fragments.connectMessaging.ConnectMessageAttachmentsBinder;
import org.commcare.fragments.connectMessaging.ConnectMessageChatData;
import org.commcare.fragments.connectMessaging.ConnectMessageMediaSizer;
import org.commcare.utils.MarkupUtil;
import org.javarosa.core.model.utils.DateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ConnectMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int LEFTVIEW = 0;
    public static final int RIGHTVIEW = 1;
    public static final int LEFT_RICH_VIEW = 2;
    private static final Object PAYLOAD_READ_STATUS = new Object();
    private static final Object PAYLOAD_ATTACHMENTS = new Object();
    private static final float TEXT_BUBBLE_WIDTH_FRACTION = 0.7f;
    private static final float MEDIA_BUBBLE_WIDTH_FRACTION = 1f;
    private List<ConnectMessageChatData> messages;
    private final ConnectMessageAttachmentListener attachmentListener;

    public ConnectMessageAdapter(List<ConnectMessageChatData> messages,
                                 ConnectMessageAttachmentListener attachmentListener) {
        this.messages = messages;
        this.attachmentListener = attachmentListener;
    }

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
                    && hasSameAttachmentContent(oldChat, newChat)
                    && oldChat.isMessageRead() == newChat.isMessageRead();
        }

        @Nullable
        @Override
        public Object getChangePayload(int oldItemPosition, int newItemPosition) {
            ConnectMessageChatData oldChat = oldMessages.get(oldItemPosition);
            ConnectMessageChatData newChat = newMessages.get(newItemPosition);
            if (!hasSameDisplayContent(oldChat, newChat)) {
                return null;
            }
            if (!hasSameAttachmentContent(oldChat, newChat)) {
                return oldChat.hasRichContent() && newChat.hasRichContent() ? PAYLOAD_ATTACHMENTS : null;
            }
            return PAYLOAD_READ_STATUS;
        }

        private static boolean hasSameAttachmentContent(ConnectMessageChatData oldChat,
                                                        ConnectMessageChatData newChat) {
            return oldChat.getAttachments().equals(newChat.getAttachments())
                    && oldChat.getPendingDownloadState() == newChat.getPendingDownloadState();
        }

        private static boolean hasSameDisplayContent(ConnectMessageChatData oldChat,
                                                     ConnectMessageChatData newChat) {
            return oldChat.getType() == newChat.getType()
                    && Objects.equals(oldChat.getMessage(), newChat.getMessage())
                    && Objects.equals(oldChat.getTimestamp(), newChat.getTimestamp())
                    && oldChat.isUnsupportedVersion() == newChat.isUnsupportedVersion();
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

    public class RichLeftViewHolder extends BaseMessageViewHolder {
        private final ItemChatLeftRichViewBinding richBinding;
        private final ViewGroup messageList;
        private final ConnectMessageAttachmentsBinder attachmentsBinder;

        public RichLeftViewHolder(ItemChatLeftRichViewBinding binding, ViewGroup messageList) {
            super(binding);
            this.richBinding = binding;
            this.messageList = messageList;
            this.attachmentsBinder = new ConnectMessageAttachmentsBinder(binding.llAttachments,
                    attachmentListener);
        }

        @Override
        public void bind(ConnectMessageChatData chat) {
            super.bind(chat);
            bindAttachments(chat);
        }

        public void bindAttachments(ConnectMessageChatData chat) {
            boolean awaitingDownload = chat.isAwaitingDownload();
            boolean hasAttachments = awaitingDownload || !chat.getAttachments().isEmpty();
            richBinding.tvChatMessage.setVisibility(awaitingDownload ? View.GONE : View.VISIBLE);
            richBinding.tvUpdateNotice.setVisibility(
                    chat.isUnsupportedVersion() && !awaitingDownload ? View.VISIBLE : View.GONE);
            ConstraintLayout.LayoutParams guidelineParams =
                    (ConstraintLayout.LayoutParams)richBinding.guideline.getLayoutParams();
            guidelineParams.guidePercent = hasAttachments
                    ? MEDIA_BUBBLE_WIDTH_FRACTION
                    : TEXT_BUBBLE_WIDTH_FRACTION;
            richBinding.guideline.setLayoutParams(guidelineParams);

            ConnectMessageAttachmentsBinder.Layout layout = awaitingDownload
                    ? attachmentsBinder.bindPendingMessage(chat.getMessageId(), chat.getPendingDownloadState())
                    : attachmentsBinder.bind(chat.getAttachments(), maxMediaWidth(), maxMediaHeight());

            ViewGroup.LayoutParams bubbleParams = richBinding.llBubble.getLayoutParams();
            bubbleParams.width = layout.getFillsBubbleWidth()
                    ? ViewGroup.LayoutParams.MATCH_PARENT
                    : ViewGroup.LayoutParams.WRAP_CONTENT;
            richBinding.llBubble.setLayoutParams(bubbleParams);
            richBinding.tvChatMessage.setMaxWidth(hasAttachments && !layout.getFillsBubbleWidth()
                    ? layout.getContentWidth()
                    : Integer.MAX_VALUE);
        }

        private int maxMediaWidth() {
            int rowWidth = messageList.getWidth() > 0
                    ? messageList.getWidth()
                    : displayMetrics().widthPixels;
            int rowMargins = 2 * itemView.getResources().getDimensionPixelSize(R.dimen.spacer_small);
            int bubblePadding = 2 * itemView.getResources().getDimensionPixelSize(R.dimen.connect_message_bubble_padding);
            int tailWidth = richBinding.edge.getDrawable().getIntrinsicWidth();
            return Math.max(1, rowWidth - rowMargins - tailWidth - bubblePadding);
        }

        private int maxMediaHeight() {
            int listHeight = messageList.getHeight() > 0
                    ? messageList.getHeight()
                    : displayMetrics().heightPixels;
            return Math.max(1, (int)(listHeight * ConnectMessageMediaSizer.MAX_HEIGHT_FRACTION_OF_LIST));
        }

        private DisplayMetrics displayMetrics() {
            return itemView.getResources().getDisplayMetrics();
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
            } else if (binding instanceof ItemChatLeftRichViewBinding) {
                tvChatMessage = ((ItemChatLeftRichViewBinding)binding).tvChatMessage;
                tvChatDate = ((ItemChatLeftRichViewBinding)binding).tvChatDate;
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
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == LEFT_RICH_VIEW) {
            ItemChatLeftRichViewBinding binding = ItemChatLeftRichViewBinding.inflate(inflater, parent, false);
            return new RichLeftViewHolder(binding, parent);
        } else if (viewType == LEFTVIEW) {
            ItemChatLeftViewBinding binding = ItemChatLeftViewBinding.inflate(inflater, parent, false);
            return new LeftViewHolder(binding);
        } else {
            ItemChatRightViewBinding binding = ItemChatRightViewBinding.inflate(inflater, parent, false);
            return new RightViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ((BaseMessageViewHolder)holder).bind(messages.get(position));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_ATTACHMENTS) && holder instanceof RichLeftViewHolder) {
            ((RichLeftViewHolder)holder).bindAttachments(messages.get(position));
        } else if (payloads.contains(PAYLOAD_READ_STATUS)) {
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
        ConnectMessageChatData chat = messages.get(position);
        if (chat.getType() != LEFTVIEW) {
            return RIGHTVIEW;
        }
        return chat.hasRichContent() ? LEFT_RICH_VIEW : LEFTVIEW;
    }
}
