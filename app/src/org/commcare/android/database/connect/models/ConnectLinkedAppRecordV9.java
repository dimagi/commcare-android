package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;

import java.util.Date;

/**
 * Migrates a V8 record to V9 format.
 * New in V9:
 * - Added usingLocalPassphrase field
 * - Changed link offer date handling
 *
 * @return A new V9 record with migrated data
 * @throws IllegalArgumentException if oldRecord is null
 */
@Table(ConnectLinkedAppRecordV9.STORAGE_KEY)
public class ConnectLinkedAppRecordV9 extends Persisted {
    /**
     * Name of database that stores Connect user records
     */
    public static final String STORAGE_KEY = "app_info";

    public static final String META_APP_ID = "app_id";
    public static final String META_USER_ID = "user_id";
    public static final String META_CONNECTID_LINKED = "connectid_linked";
    public static final String META_OFFERED_1 = "link_offered_1";
    public static final String META_OFFERED_1_DATE = "link_offered_1_date";
    public static final String META_OFFERED_2 = "link_offered_2";
    public static final String META_OFFERED_2_DATE = "link_offered_2_date";
    public static final String META_LOCAL_PASSPHRASE = "using_local_passphrase";

    @Persisting(1)
    @MetaField(META_APP_ID)
    private String appId;
    @Persisting(2)
    @MetaField(META_USER_ID)
    private String userId;
    @Persisting(3)
    private String password;
    @Persisting(4)
    private boolean workerLinked;
    @Persisting(value = 5, nullable = true)
    private String hqToken;
    @Persisting(6)
    private Date hqTokenExpiration;
    @Persisting(7)
    @MetaField(META_CONNECTID_LINKED)
    private boolean connectIdLinked;
    @Persisting(8)
    @MetaField(META_OFFERED_1)
    private boolean linkOffered1;
    @Persisting(9)
    @MetaField(META_OFFERED_1_DATE)
    private Date linkOfferDate1;
    @Persisting(10)
    @MetaField(META_OFFERED_2)
    private boolean linkOffered2;
    @Persisting(11)
    @MetaField(META_OFFERED_2_DATE)
    private Date linkOfferDate2;

    @Persisting(12)
    @MetaField(META_LOCAL_PASSPHRASE)
    private boolean usingLocalPassphrase;

    public ConnectLinkedAppRecordV9() {
        hqTokenExpiration = new Date();
        linkOfferDate1 = new Date();
        linkOfferDate2 = new Date();
    }

    public String getAppId(){ return appId; }
    public String getUserId() {
        return userId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean getWorkerLinked() {
        return workerLinked;
    }

    public String getHqToken() {
        return hqToken;
    }

    public Date getHqTokenExpiration() {
        return hqTokenExpiration;
    }

    public boolean getConnectIdLinked() { return connectIdLinked; }

    public Date getLinkOfferDate1() {
        return linkOffered1 ? linkOfferDate1 : null;
    }

    public Date getLinkOfferDate2() {
        return linkOffered2 ? linkOfferDate2 : null;
    }

    public boolean isUsingLocalPassphrase() { return usingLocalPassphrase; }

    public static ConnectLinkedAppRecordV9 fromV8(ConnectLinkedAppRecordV8 oldRecord) {
        ConnectLinkedAppRecordV9 newRecord = new ConnectLinkedAppRecordV9();

        newRecord.appId = oldRecord.getAppId();
        newRecord.userId = oldRecord.getUserId();
        newRecord.password = oldRecord.getPassword();
        newRecord.workerLinked = oldRecord.getWorkerLinked();
        newRecord.hqToken = oldRecord.getHqToken();
        newRecord.hqTokenExpiration = oldRecord.getHqTokenExpiration();
        newRecord.connectIdLinked = oldRecord.getConnectIdLinked();
        newRecord.linkOffered1 = oldRecord.getLinkOfferDate1() != null;
        newRecord.linkOfferDate1 = newRecord.linkOffered1 ? oldRecord.getLinkOfferDate1() : new Date();
        newRecord.linkOffered2 = oldRecord.getLinkOfferDate2() != null;
        newRecord.linkOfferDate2 = newRecord.linkOffered2 ? oldRecord.getLinkOfferDate2() : new Date();
        // Default to true for backward compatibility
        newRecord.usingLocalPassphrase = true;

        return newRecord;
    }
}
