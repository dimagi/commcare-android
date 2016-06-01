package org.commcare.android.database.app.models;

import org.commcare.android.storage.framework.PersistedPlain;
import org.commcare.models.framework.Table;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Represents the version of a UserKeyRecord that exists on any devices running a pre-2.26
 * version of CommCare, which was deprecated in app db version 8. This class is used to read a
 * UserKeyRecord that exists in such a database, in order to run a db upgrade.
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
@Table(UserKeyRecordV1.STORAGE_KEY)
public class UserKeyRecordV1 extends PersistedPlain {

    public static final String META_USERNAME = "username";
    public static final String META_SANDBOX_ID = "sandbox_id";
    public static final String META_KEY_STATUS = "status";

    public static final String STORAGE_KEY = "user_key_records";

    private String username;
    private String passwordHash;
    private byte[] encryptedKey;
    private Date validFrom;
    private Date validTo;

    /**
     * The unique ID of the data sandbox covered by this key
     **/
    private String uuid;
    private int type;

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public byte[] getEncryptedKey() {
        return encryptedKey;
    }

    public Date getValidFrom() {
        return validFrom;
    }

    public Date getValidTo() {
        return validTo;
    }

    public String getUuid() {
        return uuid;
    }

    public int getType() {
        return type;
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        super.readExternal(in, pf);

        username = ExtUtil.readString(in);
        passwordHash = ExtUtil.readString(in);
        encryptedKey = ExtUtil.readBytes(in);
        validFrom = ExtUtil.readDate(in);
        validTo = ExtUtil.readDate(in);
        uuid = ExtUtil.readString(in);
        type = ExtUtil.readInt(in);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);

        ExtUtil.writeString(out, username);
        ExtUtil.writeString(out, passwordHash);
        ExtUtil.writeBytes(out, encryptedKey);
        ExtUtil.writeDate(out, validFrom);
        ExtUtil.writeDate(out, validTo);
        ExtUtil.writeString(out, uuid);
        ExtUtil.writeNumeric(out, type);
    }

    @Override
    public String[] getMetaDataFields() {
        return new String[]{
                META_USERNAME, META_SANDBOX_ID, META_KEY_STATUS
        };
    }

    @Override
    public Object getMetaData(String fieldName) {
        switch (fieldName) {
            case META_USERNAME:
                return username;
            case META_SANDBOX_ID:
                return uuid;
            case META_KEY_STATUS:
                return type;
            default:
                throw new IllegalArgumentException("No metadata field " + fieldName + " in the storage system");
        }
    }
}
