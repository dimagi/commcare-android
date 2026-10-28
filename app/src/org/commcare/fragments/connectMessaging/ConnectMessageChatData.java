package org.commcare.fragments.connectMessaging;

import androidx.annotation.Nullable;

import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState;

import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ConnectMessageChatData {
    private String messageId;
    private int type;
    private String message;
    private String userName;
    private Date timestamp;
    private int countUnread;
    private boolean isMessageRead;
    private final List<ConnectMessageAttachmentItem> attachments;
    private final boolean unsupportedVersion;
    @Nullable
    private final ConnectMessagingAttachmentState pendingDownloadState;

    // Constructor with parameters
    public ConnectMessageChatData(String messageId, int type, String message, String userName, Date timestamp, boolean isMessageRead) {
        this(messageId, type, message, userName, timestamp, isMessageRead, Collections.emptyList(), false, null);
    }

    public ConnectMessageChatData(String messageId, int type, String message, String userName, Date timestamp,
                                  boolean isMessageRead, List<ConnectMessageAttachmentItem> attachments,
                                  boolean unsupportedVersion,
                                  @Nullable ConnectMessagingAttachmentState pendingDownloadState) {
        this.messageId = messageId;
        this.type = type;
        this.message = message;
        this.userName = userName;
        this.timestamp = timestamp;
        this.isMessageRead = isMessageRead;
        this.attachments = attachments;
        this.unsupportedVersion = unsupportedVersion;
        this.pendingDownloadState = pendingDownloadState;
    }

    public List<ConnectMessageAttachmentItem> getAttachments() {
        return attachments;
    }

    public boolean isUnsupportedVersion() {
        return unsupportedVersion;
    }

    @Nullable
    public ConnectMessagingAttachmentState getPendingDownloadState() {
        return pendingDownloadState;
    }

    public boolean isAwaitingDownload() {
        return pendingDownloadState != null;
    }

    public boolean hasRichContent() {
        return !attachments.isEmpty() || unsupportedVersion || isAwaitingDownload();
    }

    // Getters and setters
    public String getMessageId() {
        return messageId;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public int getCountUnread() {
        return countUnread;
    }

    public void setCountUnread(int countUnread) {
        this.countUnread = countUnread;
    }

    public boolean isMessageRead() {
        return isMessageRead;
    }

    public void setMessageRead(boolean messageRead) {
        isMessageRead = messageRead;
    }
}
