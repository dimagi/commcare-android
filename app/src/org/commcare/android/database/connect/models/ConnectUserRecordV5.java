package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.connect.ConnectConstants;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;

import java.util.Date;

/**
 * Database model for storing ConnectID user information and authentication state.
 * This V5 version includes support for connect tokens and registration phases.
 * <p>
 * This record is used by:
 * - ApiConnectId for user authentication
 * - Connect feature for user management
 * - Database upgrade mechanisms for version migrations
 *
 * @author dviggiano
 * @see org.commcare.connect.ConnectConstants
 */
@Table(ConnectUserRecordV5.STORAGE_KEY)
public class ConnectUserRecordV5 extends Persisted {
    /**
     * Name of database that stores Connect user records
     */
    public static final String STORAGE_KEY = "user_info";
    /**
     * Unique identifier for the ConnectID user.
     * This ID is immutable and used as the primary key.
     */
    @Persisting(1)
    private String userId;
    /**
     * User's password hash.
     * Updated when password is changed or reset.
     *
     * @see #setPassword(String)
     * @see #lastPasswordDate
     */
    @Persisting(2)
    private String password;
    /**
     * User's display name.
     * Can be updated by the user.
     */
    @Persisting(3)
    private String name;
    /**
     * User's phone no.
     * Used for authentication and recovery
     */
    @Persisting(4)
    private String primaryPhone;
    /**
     * User's secondary phone no.
     * Used for authentication and recovery
     */
    @Persisting(5)
    private String alternatePhone;
    /**
     * it tells about the current position of registration of user
     * Used for smoot registration
     */
    @Persisting(6)
    private int registrationPhase;

    @Persisting(7)
    private Date lastPasswordDate;

    @Persisting(value = 8, nullable = true)
    private String connectToken;

    @Persisting(value = 9, nullable = true)
    private Date connectTokenExpiration;

    public ConnectUserRecordV5() {
        registrationPhase = ConnectConstants.PERSONALID_NO_ACTIVITY;
        lastPasswordDate = new Date();
        connectTokenExpiration = new Date();
    }

    public String getUserId() {
        return userId;
    }

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

    public int getRegistrationPhase() {
        return registrationPhase;
    }

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
