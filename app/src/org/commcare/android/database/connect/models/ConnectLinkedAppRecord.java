package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;

@Table(org.commcare.android.database.connect.models.ConnectUserRecord.STORAGE_KEY)
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

    public ConnectLinkedAppRecord() {

    }

    public ConnectLinkedAppRecord(String appID, String userID, String password) {
        this.app_id = appID;
        this.user_id = userID;
        this.password = password;
    }

    public String getAppID() { return app_id; }
    public String getUserID() { return user_id; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password;}

}
