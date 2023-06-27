package org.commcare.android.database.connect.models;

import android.content.Intent;

import org.commcare.activities.connect.ConnectIDConstants;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;

import java.util.Date;

@Table(ConnectUserRecord.STORAGE_KEY)
public class ConnectUserRecord extends Persisted {
    /**
     * Name of database that stores Connect user records
     */
    public static final String STORAGE_KEY = "user_info";

    @Persisting(1)
    private String userID;

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

    @Persisting(value=8, nullable = true)
    private String clientID;

    @Persisting(value=9, nullable = true)
    private String clientSecret;

    public ConnectUserRecord() {
        registrationPhase = ConnectIDConstants.CONNECT_NO_ACTIVITY;
        lastPasswordDate = new Date();
    }

    public ConnectUserRecord(String primaryPhone, String userID, String password, String name, String alternatePhone) {
        this();
        this.primaryPhone = primaryPhone;
        this.alternatePhone = alternatePhone;
        this.userID = userID;
        this.password = password;
        this.name = name;
    }

    public static ConnectUserRecord getUserFromIntent(Intent intent) {
        return new ConnectUserRecord(
                intent.getStringExtra(ConnectIDConstants.PHONE),
                intent.getStringExtra(ConnectIDConstants.USERNAME),
                intent.getStringExtra(ConnectIDConstants.PASSWORD),
                intent.getStringExtra(ConnectIDConstants.NAME),
                intent.getStringExtra(ConnectIDConstants.ALT_PHONE));
    }

    public void putUserInIntent(Intent intent) {
        intent.putExtra(ConnectIDConstants.PHONE, primaryPhone);
        intent.putExtra(ConnectIDConstants.USERNAME, userID);
        intent.putExtra(ConnectIDConstants.PASSWORD, password);
        intent.putExtra(ConnectIDConstants.NAME, name);
        intent.putExtra(ConnectIDConstants.ALT_PHONE, alternatePhone);
    }

    public String getUserID() { return userID; }
    public String getPrimaryPhone() { return primaryPhone; }
    public void setPrimaryPhone(String primaryPhone) { this.primaryPhone = primaryPhone; }
    public String getAlternatePhone() { return alternatePhone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getName() { return name; }
    public int getRegistrationPhase() { return registrationPhase; }
    public void setRegistrationPhase(int phase) { registrationPhase = phase; }
    public Date getLastPasswordDate() { return lastPasswordDate; }
    public void setLastPasswordDate(Date date) { lastPasswordDate = date; }
    public String getClientID() { return clientID; }
    public String getClientSecret() { return clientSecret; }
}
