package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;

import java.util.Date;

@Table(org.commcare.android.database.connect.models.ConnectLinkedAppRecord.STORAGE_KEY)
public class ConnectLinkedAppRecord extends Persisted {
    /**
     * Name of database that stores Connect user records
     */
    public static final String STORAGE_KEY = "app_info";

    public static final String META_APP_ID = "app_id";
    public static final String META_USER_ID = "user_id";

    @Persisting(1)
    @MetaField(META_APP_ID)
    private String app_id;

    @Persisting(2)
    @MetaField(META_USER_ID)
    private String user_id;

    @Persisting(3)
    private String password;

    @Persisting(4)
    private boolean workerLinked;
    @Persisting(value=5, nullable = true)
    private String hqToken;

    @Persisting(6)
    private Date hqTokenExpiration;

    public ConnectLinkedAppRecord() {
        hqTokenExpiration = new Date();
    }

    public ConnectLinkedAppRecord(String appID, String userID, String password) {
        this.app_id = appID;
        this.user_id = userID;
        this.password = password;

        hqTokenExpiration = new Date();
    }

    public String getUserID() { return user_id; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password;}
    public boolean getWorkerLinked() { return workerLinked; }
    public void setWorkerLinked(boolean linked) { workerLinked = linked; }
    public String getHQToken() { return hqToken; }
    public Date getHQTokenExpiration() { return hqTokenExpiration; }
    public void updateHQToken(String token, Date expirationDate) {
        hqToken = token;
        hqTokenExpiration = expirationDate;
    }
}
