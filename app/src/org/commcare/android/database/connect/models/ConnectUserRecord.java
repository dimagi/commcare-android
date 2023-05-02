package org.commcare.android.database.connect.models;

import android.content.Intent;

import org.commcare.activities.ConnectIDRegistrationActivity;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;

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
    private String registrationPhase;

    @Persisting(value=5, nullable = true)
    private String clientID;

    @Persisting(value=6, nullable = true)
    private String clientSecret;

    public ConnectUserRecord() {
        registrationPhase = "";
    }

    public ConnectUserRecord(String userID, String password, String displayName) {
        this();
        this.userID = userID;
        this.password = password;
        this.name = displayName;
    }

    public static ConnectUserRecord getUserFromIntent(Intent intent) {
        ConnectUserRecord user = new ConnectUserRecord();
        user.userID = intent.getStringExtra(ConnectIDRegistrationActivity.USERNAME);
        user.password = intent.getStringExtra(ConnectIDRegistrationActivity.PASSWORD);
        user.name = intent.getStringExtra(ConnectIDRegistrationActivity.NAME);

        return user;
    }

    public void putUserInIntent(Intent intent) {
        intent.putExtra(ConnectIDRegistrationActivity.USERNAME, userID);
        intent.putExtra(ConnectIDRegistrationActivity.PASSWORD, password);
        intent.putExtra(ConnectIDRegistrationActivity.NAME, name);
    }

    public String getUserID() { return userID; }
    public String getPassword() { return password; }
    public String getName() { return name; }
    public String getRegistrationPhase() { return registrationPhase; }
    public void setRegistrationPhase(String phase) { registrationPhase = phase; }
    public String getClientID() { return clientID; }
    public String getClientSecret() { return clientSecret; }
}
