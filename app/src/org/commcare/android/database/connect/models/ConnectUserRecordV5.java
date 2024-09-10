package org.commcare.android.database.connect.models;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.connect.ConnectConstants;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;

import java.util.Date;

/**
 * DB model for a ConnectID user and their info
 *
 * @author dviggiano
 */
@Table(ConnectUserRecord.STORAGE_KEY)
public class ConnectUserRecordV5 extends Persisted {
    /**
     * Name of database that stores Connect user records
     */
    public static final String STORAGE_KEY = "user_info";

    @Persisting(1)
    private String userId;

    @Persisting(2)
    private String password;

    @Persisting(3)
    private String name;

    @Persisting(4)
    private String primaryPhone;

    @Persisting(5)
    private String alternatePhone;

    @Persisting(6)
    private int registrationPhase;

    @Persisting(7)
    private Date lastPasswordDate;

    @Persisting(value = 8, nullable = true)
    private String connectToken;

    @Persisting(value = 9, nullable = true)
    private Date connectTokenExpiration;

    public ConnectUserRecordV5() {
        registrationPhase = ConnectConstants.CONNECT_NO_ACTIVITY;
        lastPasswordDate = new Date();
        connectTokenExpiration = new Date();
    }

    public String getUserId() {return userId; }
    public String getPrimaryPhone() {
        return primaryPhone;
    }
    public String getAlternatePhone() {
        return alternatePhone;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public int getRegistrationPhase() { return registrationPhase; }
    public Date getLastPasswordDate() {
        return lastPasswordDate;
    }
    public String getConnectToken() {
        return connectToken;
    }
    public Date getConnectTokenExpiration() {
        return connectTokenExpiration;
    }
}
