package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.ParseException;
import java.util.Date;

@Table(ConnectMessagingChannelRecord.STORAGE_KEY)
public class ConnectMessagingChannelRecord extends Persisted implements Serializable {

    /**
     * Name of database that stores Connect payment units
     */
    public static final String STORAGE_KEY = "connect_messaging_channel";

    public static final String META_CHANNEL_ID = "channel_id";
    public static final String META_CHANNEL_CREATED = "created";
    public static final String META_ANSWERED_CONSENT = "answered_consent";
    public static final String META_CONSENTED = "consented";
    public static final String META_CHANNEL_NAME = "channel_source";
    public static final String META_KEY_URL = "key_url";
    public static final String META_KEY = "key";

    public ConnectMessagingChannelRecord() {

    }

    @Persisting(1)
    @MetaField(META_CHANNEL_ID)
    private String channelId;

    @Persisting(2)
    @MetaField(META_CHANNEL_CREATED)
    private Date channelCreated;

    @Persisting(3)
    @MetaField(META_ANSWERED_CONSENT)
    private boolean answeredConsent;

    @Persisting(4)
    @MetaField(META_CONSENTED)
    private boolean consented;

    @Persisting(5)
    @MetaField(META_CHANNEL_NAME)
    private String channelName;

    @Persisting(6)
    @MetaField(META_KEY_URL)
    private String keyUrl;

    @Persisting(7)
    @MetaField(META_KEY)
    private String key;

    public static ConnectMessagingChannelRecord fromJson(JSONObject json) throws JSONException, ParseException {
        ConnectMessagingChannelRecord connectMessagingChannelRecord = new ConnectMessagingChannelRecord();

        connectMessagingChannelRecord.channelId = json.getString(META_CHANNEL_ID);
        connectMessagingChannelRecord.channelCreated = new Date();// json.getString(META_CHANNEL_CREATED);
        connectMessagingChannelRecord.answeredConsent = false;//json.getString(META_ANSWERED_CONSENT);
        connectMessagingChannelRecord.consented = false;//json.getString(META_CONSENTED);
        connectMessagingChannelRecord.channelName = json.getString(META_CHANNEL_NAME);
        connectMessagingChannelRecord.keyUrl = json.getString(META_KEY_URL);
        connectMessagingChannelRecord.key = "";//json.getString(META_KEY);

        return connectMessagingChannelRecord;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public Date getChannelCreated() {
        return channelCreated;
    }

    public void setChannelCreated(Date channelCreated) {
        this.channelCreated = channelCreated;
    }

    public boolean getAnsweredConsent() {
        return answeredConsent;
    }

    public void setAnsweredConsent(boolean answeredConsent) {
        this.answeredConsent = answeredConsent;
    }

    public boolean getConsented() {
        return consented;
    }

    public void setConsented(boolean consented) {
        this.consented = consented;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getKeyUrl() {
        return keyUrl;
    }

    public void setKeyUrl(String keyUrl) {
        this.keyUrl = keyUrl;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}