package org.commcare.android.database.app.models;

import org.commcare.android.storage.framework.Persisting;
import org.commcare.android.storage.framework.Table;
import org.commcare.modern.models.MetaField;

import java.util.Date;

/**
 * Represents the version of a UserKeyRecord that exists on any devices running a pre-2.26
 * version of CommCare, which was deprecated in app db version 8. This class is used to read a
 * UserKeyRecord that exists in such a database, in order to run a db upgrade.
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
@Table(UserKeyRecordV1.STORAGE_KEY)
public class UserKeyRecordV1 extends UserKeyRecord {

    public static final String META_USERNAME = "username";
    public static final String META_SANDBOX_ID = "sandbox_id";
    public static final String META_KEY_STATUS = "status";

    public static final String STORAGE_KEY = "user_key_records";

    @Persisting(1)
    @MetaField(META_USERNAME)
    private String username;

    @Persisting(2)
    private String passwordHash;

    @Persisting(3)
    private byte[] encryptedKey;

    @Persisting(4)
    private Date validFrom;

    @Persisting(5)
    private Date validTo;

    /** The unique ID of the data sandbox covered by this key **/
    @Persisting(6)
    @MetaField(META_SANDBOX_ID)
    private String uuid;

    @MetaField(META_KEY_STATUS)
    @Persisting(7)
    private int type;

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return the passwordHash
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * @return the encryptedKey
     */
    public byte[] getEncryptedKey() {
        return encryptedKey;
    }

    /**
     * @return the validFrom
     */
    public Date getValidFrom() {
        return validFrom;
    }

    /**
     * @return the validTo
     */
    public Date getValidTo() {
        return validTo;
    }

    /**
     * @return the uuid
     */
    public String getUuid() {
        return uuid;
    }

    public int getType() {
        return type;
    }


}
