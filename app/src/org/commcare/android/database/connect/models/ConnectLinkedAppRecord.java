package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.connect.network.SsoToken;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;

import java.util.Date;

/**
 * DB model holding info for an HQ app linked to ConnectID
 *
 * @author dviggiano
 */
@Table(ConnectLinkedAppRecord.STORAGE_KEY)
public class ConnectLinkedAppRecord extends Persisted {
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
    public static final String META_LAST_ACCESSED = "last_accessed";

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

    @Persisting(13)
    @MetaField(META_LAST_ACCESSED)
    private Date lastAccessed;

    public ConnectLinkedAppRecord() {
        hqTokenExpiration = new Date();
        linkOfferDate1 = new Date();
        linkOfferDate2 = new Date();
        lastAccessed = new Date();
    }

    public ConnectLinkedAppRecord(String appId, String userId, boolean connectIdLinked, String password) {
        this();

        this.appId = appId;
        this.userId = userId;
        this.connectIdLinked = connectIdLinked;
        this.password = password;
    }

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

    public void setWorkerLinked(boolean linked) {
        workerLinked = linked;
    }

    public String getHqToken() {
        return hqToken;
    }

    public Date getHqTokenExpiration() {
        return hqTokenExpiration;
    }

    public void updateHqToken(SsoToken token) {
        hqToken = token.getToken();
        hqTokenExpiration = token.getExpiration();
    }

    public boolean getConnectIdLinked() {
        return connectIdLinked;
    }

    public void clearHqToken() {
        hqToken = null;
        hqTokenExpiration = new Date();
    }

    public void setConnectIdLinked(boolean linked) {
        connectIdLinked = linked;
    }

    public void linkToConnectId(String password) {
        connectIdLinked = true;
        this.password = password;
    }

    public void severConnectIdLink() {
        connectIdLinked = false;
        password = "";
        linkOffered1 = false;
        linkOffered2 = false;
    }

    public Date getLinkOfferDate1() {
        return linkOffered1 ? linkOfferDate1 : null;
    }

    public void setLinkOfferDate1(Date date) {
        linkOffered1 = true;
        linkOfferDate1 = date;
    }

    public Date getLinkOfferDate2() {
        return linkOffered2 ? linkOfferDate2 : null;
    }

    public void setLinkOfferDate2(Date date) {
        linkOffered2 = true;
        linkOfferDate2 = date;
    }

    public boolean isUsingLocalPassphrase() {
        return usingLocalPassphrase;
    }

    public void setIsUsingLocalPassphrase(boolean using) {
        usingLocalPassphrase = using;
    }

    public Date getLastAccessed() {
        return lastAccessed;
    }

    public void setLastAccessed(Date date) {
        lastAccessed = date;
    }

    public static ConnectLinkedAppRecord fromV9(ConnectLinkedAppRecordV9 oldRecord) {
        ConnectLinkedAppRecord newRecord = new ConnectLinkedAppRecord();

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

        newRecord.usingLocalPassphrase = oldRecord.isUsingLocalPassphrase();

        return newRecord;
    }
}
