package org.commcare.android.database.connect.models;

import androidx.annotation.Nullable;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.commcare.util.Base64;
import org.commcare.util.Base64DecoderException;
import org.commcare.util.EncryptionUtils;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

import static org.commcare.utils.PushNotificationHelper.MESSAGE;
import static org.commcare.utils.PushNotificationHelper.truncateMessage;

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

    public static final int VERSION_PLAIN = 0;
    public static final int VERSION_RICH = 2;
    private static final long NO_EXPIRY = 0;

    private static final String JSON_VERSION = "version";
    private static final String JSON_RICH_TEXT = "rich_text";
    private static final String JSON_FORMAT = "format";
    private static final String JSON_EXPIRES_AT = "expires_at";
    private static final String JSON_CIPHER_TEXT = "ciphertext";
    private static final String JSON_NONCE = "nonce";
    private static final String JSON_TAG = "tag";

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

    @Persisting(8)
    private int version;

    @Persisting(value = 9, nullable = true)
    private String richText;

    @Persisting(value = 10, nullable = true)
    private String format;

    @Persisting(11)
    private long expiresAtMillis;

    public static ConnectMessagingMessageRecord fromV29(ConnectMessagingMessageRecordV29 oldRecord) {
        ConnectMessagingMessageRecord record = new ConnectMessagingMessageRecord();
        record.messageId = oldRecord.getMessageId();
        record.channelId = oldRecord.getChannelId();
        record.timeStamp = oldRecord.getTimeStamp();
        record.message = oldRecord.getMessage();
        record.isOutgoing = oldRecord.isOutgoing();
        record.confirmed = oldRecord.getConfirmed();
        record.userViewed = oldRecord.getUserViewed();
        record.version = VERSION_PLAIN;
        record.richText = null;
        record.format = null;
        record.expiresAtMillis = NO_EXPIRY;
        return record;
    }

    /**
     * Creates a decrypted message record from encrypted JSON payload by using the channel key
     *
     * @param json     JSON data to parse
     * @param channels List of channels from the Database with keys for decryption
     * @return ConnectMessagingMessageRecord or null if decryption fails or channel not found
     * @throws JSONException
     * @throws ParseException
     */
    public static ConnectMessagingMessageRecord fromJson(
            JSONObject json,
            List<ConnectMessagingChannelRecord> channels
    ) throws JSONException, ParseException {
        ConnectMessagingMessageRecord connectMessagingMessageRecord =
                new ConnectMessagingMessageRecord();

        connectMessagingMessageRecord.messageId = json.getString(META_MESSAGE_ID);
        connectMessagingMessageRecord.channelId = json.getString(META_MESSAGE_CHANNEL_ID);

        ConnectMessagingChannelRecord channel =
                getChannel(channels, connectMessagingMessageRecord.channelId);
        if (channel == null) {
            return null;
        }

        String dateString = json.getString(META_MESSAGE_TIMESTAMP);
        connectMessagingMessageRecord.timeStamp = DateUtils.parseDateTime(dateString);

        String tag = json.getString(JSON_TAG);
        String nonce = json.getString(JSON_NONCE);
        String cipherText = json.getString(JSON_CIPHER_TEXT);

        String decrypted = decrypt(cipherText, nonce, tag, channel.getKey());

        if (decrypted == null) {
            return null;
        }

        connectMessagingMessageRecord.message = truncateMessage(decrypted, MESSAGE);

        connectMessagingMessageRecord.isOutgoing = false;
        connectMessagingMessageRecord.confirmed = false;
        connectMessagingMessageRecord.userViewed = false;

        connectMessagingMessageRecord.version = json.optInt(JSON_VERSION, VERSION_PLAIN);
        if (connectMessagingMessageRecord.isRich()) {
            readRichFields(connectMessagingMessageRecord, json, channel.getKey());
        }

        return connectMessagingMessageRecord;
    }

    private static void readRichFields(ConnectMessagingMessageRecord record, JSONObject json, String key)
            throws JSONException, ParseException {
        JSONObject richText = json.optJSONObject(JSON_RICH_TEXT);
        if (richText != null) {
            String decryptedRichText = decrypt(
                    richText.optString(JSON_CIPHER_TEXT),
                    richText.optString(JSON_NONCE),
                    richText.optString(JSON_TAG),
                    key
            );
            record.richText = decryptedRichText == null ? null : truncateMessage(decryptedRichText, MESSAGE);
        }

        record.format = json.has(JSON_FORMAT) ? json.getString(JSON_FORMAT) : null;

        if (json.has(JSON_EXPIRES_AT)) {
            String expiresAt = json.getString(JSON_EXPIRES_AT);
            Date expiryDate = DateUtils.parseDateTime(expiresAt);
            if (expiryDate == null) {
                throw new ParseException("Invalid expires_at for message " + record.messageId + ": '"
                        + expiresAt + "'", 0);
            }
            record.expiresAtMillis = expiryDate.getTime();
        }
    }

    private static ConnectMessagingChannelRecord getChannel(
            List<ConnectMessagingChannelRecord> channels,
            String channelId
    ) {
        for (ConnectMessagingChannelRecord channel : channels) {
            if (channel.getChannelId().equals(channelId)) {
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

            ByteBuffer bytes = ByteBuffer.allocate(
                    cipherTextBytes.length + nonceBytes.length + tagBytes.length + 1
            );
            bytes.put((byte)nonceBytes.length);
            bytes.put(nonceBytes);
            bytes.put(cipherTextBytes);
            bytes.put(tagBytes);

            String encoded = Base64.encode(bytes.array());
            return EncryptionUtils.decrypt(encoded, key);
        } catch (Exception e) {
            Logger.exception("Decrypting message", e);
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

            return new String[]{cipherText, nonce, tag};
        } catch (EncryptionUtils.EncryptionException | Base64DecoderException e) {
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

    public int getVersion() {
        return version;
    }

    public boolean isRich() {
        return version == VERSION_RICH;
    }

    public boolean isUnsupportedVersion() {
        return version != VERSION_PLAIN && version != VERSION_RICH;
    }

    @Nullable
    public String getRichText() {
        return richText;
    }

    @Nullable
    public String getFormat() {
        return format;
    }

    @Nullable
    public Date getExpiresAt() {
        return expiresAtMillis == NO_EXPIRY ? null : new Date(expiresAtMillis);
    }

    public String getDisplayText() {
        return richText != null ? richText : message;
    }
}
