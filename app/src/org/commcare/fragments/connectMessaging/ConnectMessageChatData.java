package org.commcare.fragments.connectMessaging;

import java.util.Date;

public class ConnectMessageChatData {

    private int type;
    private String message;
    private String userName;
    private Date timestamp;
    private int countUnread;
    private boolean isMessageRead;

    // Constructor with parameters
    public ConnectMessageChatData(int type, String message, String userName, Date timestamp, boolean isMessageRead) {
        this.type = type;
        this.message = message;
        this.userName = userName;
        this.timestamp = timestamp;
        this.isMessageRead = isMessageRead;
    }

    // Getters and setters
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
