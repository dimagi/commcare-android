package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.json.JSONException;
import org.json.JSONObject;
import org.simpleframework.xml.core.PersistenceException;

import java.io.Serializable;
import java.text.ParseException;

@Table(ConnectMessagingMessageRecord.STORAGE_KEY)
public class ConnectMessagingMessageRecord extends Persisted implements Serializable {

    /**
     * Name of database that stores Connect payment units
     */
    public static final String STORAGE_KEY = "connect_messaging_message";

    public static final String META_MESSAGE_ID = "message_id";
    public static final String META_MESSAGE_CHANNEL_ID = "channel_id";
    public static final String META_MESSAGE_TIMESTAMP = "timestamp";
    public static final String META_MESSAGE = "message";
    public static final String META_MESSAGE_IS_OUTGOING = "is_outgoing";
    public static final String META_MESSAGE_CONFIRM = "confirmed";
    public static final String META_MESSAGE_USER_VIEWED = "user_viewed";

    public ConnectMessagingMessageRecord() {

    }

    @Persisting(1)
    @MetaField(META_MESSAGE_ID)
    private int messageId;

    @Persisting(2)
    @MetaField(META_MESSAGE_CHANNEL_ID)
    private int channelId;

    @Persisting(3)
    @MetaField(META_MESSAGE_TIMESTAMP)
    private String timeStamp;

    @Persisting(4)
    @MetaField(META_MESSAGE)
    private String message;

    @Persisting(5)
    @MetaField(META_MESSAGE_IS_OUTGOING)
    private String isOutgoing;

    @Persisting(6)
    @MetaField(META_MESSAGE_CONFIRM)
    private String confirmed;

    @Persisting(7)
    @MetaField(META_MESSAGE_USER_VIEWED)
    private String userViewed;

    private static ConnectMessagingMessageRecord fromJson(JSONObject json) throws JSONException, ParseException{
        ConnectMessagingMessageRecord connectMessagingMessageRecord = new ConnectMessagingMessageRecord();

        connectMessagingMessageRecord.messageId = json.getInt(META_MESSAGE_ID);
        connectMessagingMessageRecord.channelId = json.getInt(META_MESSAGE_CHANNEL_ID);
        connectMessagingMessageRecord.timeStamp = json.getString(META_MESSAGE_TIMESTAMP);
        connectMessagingMessageRecord.message = json.getString(META_MESSAGE);
        connectMessagingMessageRecord.isOutgoing = json.getString(META_MESSAGE_IS_OUTGOING);
        connectMessagingMessageRecord.confirmed = json.getString(META_MESSAGE_CONFIRM);
        connectMessagingMessageRecord.userViewed = json.getString (META_MESSAGE_USER_VIEWED);

        return connectMessagingMessageRecord;
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public int getChannelId() {
        return channelId;
    }

    public void setChannelId(int channelId) {
        this.channelId = channelId;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(String timeStamp) {
        this.timeStamp = timeStamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getIsOutgoing() {
        return isOutgoing;
    }

    public void setIsOutgoing(String isOutgoing) {
        this.isOutgoing = isOutgoing;
    }

    public String getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(String confirmed) {
        this.confirmed = confirmed;
    }

    public String getUserViewed() {
        return userViewed;
    }

    public void setUserViewed(String userViewed) {
        this.userViewed = userViewed;
    }
}
