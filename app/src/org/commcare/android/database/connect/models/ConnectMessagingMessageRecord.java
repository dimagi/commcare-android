package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.commcare.util.Base64;
import org.commcare.util.EncryptionUtils;
import org.javarosa.core.model.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

@Table(ConnectMessagingMessageRecord.STORAGE_KEY)
public class ConnectMessagingMessageRecord extends Persisted implements Serializable {

    /**
     * Name of database that stores Connect payment units
     */
    public static final String STORAGE_KEY = "connect_messaging_message";

    public static final String META_MESSAGE_ID = "message_id";
    public static final String META_MESSAGE_CHANNEL_ID = "channel";
    public static final String META_MESSAGE_TIMESTAMP = "timestamp";
    public static final String META_MESSAGE = "content";
    public static final String META_MESSAGE_IS_OUTGOING = "is_outgoing";
    public static final String META_MESSAGE_CONFIRM = "confirmed";
    public static final String META_MESSAGE_USER_VIEWED = "user_viewed";

    public ConnectMessagingMessageRecord() {

    }

    @Persisting(1)
    @MetaField(META_MESSAGE_ID)
    private String messageId;

    @Persisting(2)
    @MetaField(META_MESSAGE_CHANNEL_ID)
    private String channelId;

    @Persisting(3)
    @MetaField(META_MESSAGE_TIMESTAMP)
    private Date timeStamp;

    @Persisting(4)
    @MetaField(META_MESSAGE)
    private String message;

    @Persisting(5)
    @MetaField(META_MESSAGE_IS_OUTGOING)
    private boolean isOutgoing;

    @Persisting(6)
    @MetaField(META_MESSAGE_CONFIRM)
    private boolean confirmed;

    @Persisting(7)
    @MetaField(META_MESSAGE_USER_VIEWED)
    private boolean userViewed;

    public static ConnectMessagingMessageRecord fromJson(JSONObject json, List<ConnectMessagingChannelRecord> channels) throws JSONException, ParseException{
        ConnectMessagingMessageRecord connectMessagingMessageRecord = new ConnectMessagingMessageRecord();

        connectMessagingMessageRecord.messageId = json.getString(META_MESSAGE_ID);
        connectMessagingMessageRecord.channelId = json.getString(META_MESSAGE_CHANNEL_ID);

        ConnectMessagingChannelRecord channel = getChannel(channels, connectMessagingMessageRecord.channelId);
        if(channel == null) {
            return null;
        }

        String dateString = json.getString(META_MESSAGE_TIMESTAMP);
        connectMessagingMessageRecord.timeStamp = DateUtils.parseDateTime(dateString);

        String tag = json.getString("tag");
        String nonce = json.getString("nonce");
        String cipherText = json.getString("ciphertext");

        String decrypted = decrypt(cipherText, nonce, tag, channel.getKey());

        if(decrypted == null) {
            return null;
        }

        connectMessagingMessageRecord.message = decrypted;

        connectMessagingMessageRecord.isOutgoing = false;
        connectMessagingMessageRecord.confirmed = false;
        connectMessagingMessageRecord.userViewed = false;

        return connectMessagingMessageRecord;
    }

    private static ConnectMessagingChannelRecord getChannel(List<ConnectMessagingChannelRecord> channels, String channelId) {
        for(ConnectMessagingChannelRecord channel : channels) {
            if(channel.getChannelId().equals(channelId)) {
                return channel;
            }
        }

        return null;
    }

    private static String decrypt(String cipherText, String nonce, String tag, String key) {
        try {
            byte[] cipherTextBytes = Base64.decode(cipherText);
            byte[] nonceBytes = Base64.decode(nonce);
            byte[] tagBytes = Base64.decode(tag);

            ByteBuffer bytes = ByteBuffer.allocate(cipherTextBytes.length + nonceBytes.length + tagBytes.length + 1);
            bytes.put((byte)nonceBytes.length);
            bytes.put(nonceBytes);
            bytes.put(cipherTextBytes);
            bytes.put(tagBytes);

            String encoded = Base64.encode(bytes.array());
            return EncryptionUtils.decrypt(encoded, key);
        } catch (Exception e) {
            return null;
        }
    }

    public static String[] encrypt(String text, String key) {
        try {
            String encoded = EncryptionUtils.encrypt(text, key);
            byte[] bytes = Base64.decode(encoded);

            ByteBuffer buffer = ByteBuffer.wrap(bytes);

            int nonceLength = buffer.get();
            byte[] nonceBytes = new byte[nonceLength];
            buffer.get(nonceBytes);
            String nonce = Base64.encode(nonceBytes);

            int tagLength = 16;
            int textLength = bytes.length - 1 - nonceLength - tagLength;
            byte[] cipherBytes = new byte[textLength];
            buffer.get(cipherBytes);
            String cipherText = Base64.encode(cipherBytes);

            byte[] tagBytes = new byte[tagLength];
            buffer.get(tagBytes);
            String tag = Base64.encode(tagBytes);

            return new String[] { cipherText, nonce, tag };
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(Date timeStamp) {
        this.timeStamp = timeStamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean getIsOutgoing() {
        return isOutgoing;
    }

    public void setIsOutgoing(boolean isOutgoing) {
        this.isOutgoing = isOutgoing;
    }

    public boolean getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public boolean getUserViewed() {
        return userViewed;
    }

    public void setUserViewed(boolean userViewed) {
        this.userViewed = userViewed;
    }
}
