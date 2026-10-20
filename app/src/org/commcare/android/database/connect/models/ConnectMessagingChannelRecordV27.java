package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;

import java.util.Date;

@Table(ConnectMessagingChannelRecord.STORAGE_KEY)
public class ConnectMessagingChannelRecordV27 extends Persisted {

    public ConnectMessagingChannelRecordV27() {
    }

    @Persisting(1)
    @MetaField(ConnectMessagingChannelRecord.META_CHANNEL_ID)
    private String channelId;

    @Persisting(2)
    @MetaField(ConnectMessagingChannelRecord.META_CHANNEL_CREATED)
    private Date channelCreated;

    @Persisting(3)
    @MetaField(ConnectMessagingChannelRecord.META_ANSWERED_CONSENT)
    private boolean answeredConsent;

    @Persisting(4)
    @MetaField(ConnectMessagingChannelRecord.META_CONSENT)
    private boolean consented;

    @Persisting(5)
    @MetaField(ConnectMessagingChannelRecord.META_CHANNEL_SOURCE)
    private String channelSource;

    @Persisting(6)
    @MetaField(ConnectMessagingChannelRecord.META_KEY_URL)
    private String keyUrl;

    @Persisting(7)
    @MetaField(ConnectMessagingChannelRecord.META_KEY)
    private String key;

    public String getChannelId() {
        return channelId;
    }

    public Date getChannelCreated() {
        return channelCreated;
    }

    public boolean getAnsweredConsent() {
        return answeredConsent;
    }

    public boolean getConsented() {
        return consented;
    }

    public String getChannelSource() {
        return channelSource;
    }

    public String getKeyUrl() {
        return keyUrl;
    }

    public String getKey() {
        return key;
    }
}
