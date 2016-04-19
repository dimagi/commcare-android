/**
 *
 */
package org.commcare.models.database.app.models;

import org.commcare.CommCareApp;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.encryption.ByteEncrypter;
import org.commcare.models.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.models.framework.Table;
import org.commcare.modern.models.MetaField;
import org.javarosa.core.util.PropertyUtils;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ctsims
 */
@Table(UserKeyRecord.STORAGE_KEY)
public class UserKeyRecord extends Persisted {

    public static final String STORAGE_KEY = "user_key_records";

    public static final String META_USERNAME = "username";
    public static final String META_SANDBOX_ID = "sandbox_id";
    public static final String META_KEY_STATUS = "status";

    /**
     * This is a normal sandbox record that is ready to be used *
     */
    public static final int TYPE_NORMAL = 1;
    /**
     * This is a record representing a legacy database that should be transfered over *
     */
    public static final int TYPE_LEGACY_TRANSITION = 2;
    /**
     * This is a new record that hasn't been evaluated for usage yet *
     */
    public static final int TYPE_NEW = 3;
    /**
     * This is a new record that hasn't been evaluated for usage yet *
     */
    public static final int TYPE_PENDING_DELETE = 4;

    // Hashed passwords should contain 3 groupings that are delimited by '$'.
    // The 1st group describes the hashing algorithm, the 2nd is the salt, and
    // the 3rd group is the digest.
    private static final Pattern HASH_STRING_PATTERN = Pattern.compile("([^\\$]+)\\$([^\\$]+)\\$([^\\$]+)");

    private static final int DEFAULT_SALT_LENGTH = 6;

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

    /**
     * The unique ID of the data sandbox covered by this key
     **/
    @Persisting(6)
    @MetaField(META_SANDBOX_ID)
    private String uuid;

    @MetaField(META_KEY_STATUS)
    @Persisting(7)
    private int type;

    /**
     * The un-hashed password wrapped by a numeric PIN
     **/
    @Persisting(8)
    private byte[] passwordWrappedByPin;

    /**
     * When a user selects the 'Remember password for next login' option, their un-hashed password
     * gets saved here and then used in the next login, so that the user does not need to enter it
     */
    @Persisting(9)
    private String rememberedPassword;

    /**
     * If there are multiple UKRs for a single username in app storage, we guarantee that only
     * 1 will be marked as active
     */
    @Persisting(10)
    private boolean isActive;

    /**
     * Serialization Only!
     */
    public UserKeyRecord() {

    }

    public UserKeyRecord(String username, String passwordHash, byte[] encryptedKey,
                         Date validFrom, Date validTo, String uuid) {
        this(username, passwordHash, encryptedKey, validFrom, validTo, uuid, TYPE_NORMAL);
    }

    public UserKeyRecord(String username, String passwordHash, byte[] encryptedKey,
                         Date validFrom, Date validTo, String uuid, int type) {
        this(username, passwordHash, encryptedKey, null, validFrom, validTo, uuid, type);
    }

    public UserKeyRecord(String username, String passwordHash, byte[] encryptedKey,
                         byte[] wrappedPassword, Date validFrom, Date validTo, String uuid,
                         int type) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.encryptedKey = encryptedKey;
        if (wrappedPassword != null) {
            this.passwordWrappedByPin = wrappedPassword;
        } else {
            // Means no PIN has been assigned yet, so just set a placeholder that is non-null
            // (Persisting fields can't be null)
            this.passwordWrappedByPin = new byte[0];
        }
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.uuid = uuid;
        this.type = type;
        this.rememberedPassword = "";

        // All new UKRs initialized to active
        this.isActive = true;
    }

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

    public boolean isActive() {
        return this.isActive;
    }

    public void setInactive() {
        this.isActive = false;
    }

    public void setActive() {
        this.isActive = true;
    }

    /**
     * Build a SHA-1 password hash out of a password string, where the salt is
     * generated to be a globally unique string.  The hash is delimited by '$'.
     *
     * @param pwd is the plain-text password inputted by the user.
     * @return SHA-1 hashed password
     */
    public static String generatePwdHash(String pwd) {
        return generatePwdHash(pwd, PropertyUtils.genGUID(DEFAULT_SALT_LENGTH).toLowerCase());
    }

    /**
     * Grab the salt out of a hashed password.
     *
     * @param pwdString a String with three groups delimited by '$', the second
     *                  containing the salt
     * @return salt String out of a hashed password
     */
    private static String extractSalt(String pwdString) {
        Matcher m = HASH_STRING_PATTERN.matcher(pwdString);
        if (m.matches()) {
            // grab the salt segment out of the hashed password
            return m.group(2);
        }
        throw new IllegalArgumentException("Unable to extract salt out of hashed password.");
    }

    /**
     * Build a SHA-1 password hash out of a password string and a salt.
     * The hash is delimited by '$'.
     *
     * @param pwd  is the plain-text password inputted by the user.
     * @param salt is a random string included during hashing to prevent
     *             against hash dictionary attacks.
     * @return SHA-1 hashed password
     */
    private static String generatePwdHash(String pwd, String salt) {
        String alg = "sha1";
        int hashLength = 41;

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        BigInteger number = new BigInteger(1, md.digest((salt + pwd).getBytes()));
        String hashed = number.toString(16);

        // prepend 0's until the hash is of the correct length
        while (hashed.length() < hashLength) {
            hashed = "0" + hashed;
        }

        return alg + "$" + salt + "$" + hashed;
    }

    public void setType(int typeNormal) {
        this.type = typeNormal;
    }

    public boolean isPasswordValid(String password) {
        if (password == null) {
            return false;
        }

        String hash = this.getPasswordHash();

        // Is the local hash value a valid hash string pattern
        // and does it match the hashed password (using the extracted salt)?
        return (HASH_STRING_PATTERN.matcher(hash).matches() &&
                hash.equals(UserKeyRecord.generatePwdHash(password, UserKeyRecord.extractSalt(hash))));
    }

    public void assignPinToRecord(String pin, String password) {
        this.passwordWrappedByPin = ByteEncrypter.wrapByteArrayWithString(password.getBytes(), pin);
    }

    public byte[] getWrappedPassword() {
        return passwordWrappedByPin;
    }

    public boolean hasPinSet() {
        return passwordWrappedByPin.length > 0;
    }

    public boolean isPinValid(String pin) {
        // Unwrap wrapped password with the PIN, and then check if the resulting password is correct
        return isPasswordValid(getUnhashedPasswordViaPin(pin));
    }

    /**
     * Returns the un-hashed password that was wrapped by the given PIN, or null if the given PIN
     * is not valid to unwrap the wrapped password
     */
    public String getUnhashedPasswordViaPin(String pin) {
        byte[] unwrapped = ByteEncrypter.unwrapByteArrayWithString(this.passwordWrappedByPin, pin);
        if (unwrapped == null) {
            // If the pin could not unwrap the password, just return null
            return null;
        }
        return new String(unwrapped);
    }

    /**
     * Must be called with 1 of the 2 values set to null, which indicates that we should check
     * based upon the other
     */
    public boolean isPasswordOrPinValid(String password, String pin) {
        if (pin != null) {
            return isPinValid(pin);
        } else {
            return isPasswordValid(password);
        }
    }

    public byte[] unWrapKey(String password) {
        if (isPasswordValid(password)) {
            return ByteEncrypter.unwrapByteArrayWithString(getEncryptedKey(), password);
        } else {
            //throw exception?
            return null;
        }
    }

    /**
     * Does today lie within the record's validity range.
     * <p/>
     * Expiration dates that are null or overflowed are ignored during this
     * check.
     */
    public boolean isCurrentlyValid() {
        // NOTE: we expect our validity dates to be in UTC

        // currentTimeMillis is UTC
        Date today = new Date(System.currentTimeMillis());

        // Does today lie within key record validity range (ignoring
        // null/overflowed expiration dates)?
        return (validFrom.before(today) &&
                (validTo == null ||
                        (validTo.getTime() != Long.MAX_VALUE && validTo.after(today))));
    }

    public static UserKeyRecord getCurrentValidRecordByPassword(CommCareApp app, String username,
                                                                String pw, boolean acceptExpired) {
        return getCurrentValidRecord(app, username, pw, null, acceptExpired);
    }

    public static UserKeyRecord getCurrentValidRecordByPin(CommCareApp app, String username,
                                                           String pin, boolean acceptExpired) {
        return getCurrentValidRecord(app, username, null, pin, acceptExpired);
    }

    public static UserKeyRecord getMatchingPrimedRecord(CommCareApp app, String username) {
        SqlStorage<UserKeyRecord> storage = app.getStorage(UserKeyRecord.class);
        for (UserKeyRecord ukr : storage.getRecordsForValue(UserKeyRecord.META_USERNAME, username)) {
            if (ukr.isPrimedForNextLogin()) {
                return ukr;
            }
        }
        return null;
    }

    /**
     * @return The user record that matches the given username/password or username/pin combo.
     * Null if not found or user record validity date is expired.
     */
    private static UserKeyRecord getCurrentValidRecord(CommCareApp app, String username, String pw,
                                                       String pin, boolean acceptExpired) {
        UserKeyRecord invalidRecord = null;
        SqlStorage<UserKeyRecord> storage = app.getStorage(UserKeyRecord.class);

        for (UserKeyRecord ukr : storage.getRecordsForValue(UserKeyRecord.META_USERNAME, username)) {
            if (ukr.isPasswordOrPinValid(pw, pin)) {
                if (ukr.isCurrentlyValid()) {
                    return ukr;
                } else {
                    invalidRecord = ukr;
                }
            }
        }

        if (acceptExpired) {
            return invalidRecord;
        }
        return null;
    }

    public void setPrimedPassword(String unhashedPassword) {
        this.rememberedPassword = unhashedPassword;
    }

    public boolean isPrimedForNextLogin() {
        return !"".equals(rememberedPassword);
    }

    public String getPrimedPassword() {
        return this.rememberedPassword;
    }

    public void clearPrimedPassword() {
        this.rememberedPassword = "";
    }

    /**
     * Used for app db migration only
     */
    public static UserKeyRecord fromOldVersion(UserKeyRecordV1 oldRecord) {
        UserKeyRecord newRecord = new UserKeyRecord(
                oldRecord.getUsername(),
                oldRecord.getPasswordHash(),
                oldRecord.getEncryptedKey(),
                oldRecord.getValidFrom(),
                oldRecord.getValidTo(),
                oldRecord.getUuid(),
                oldRecord.getType());

        // Going to set all of these to inactive to start with, and then the migration code will
        // take care of assigning the active flag back to the right ones
        newRecord.setInactive();

        return newRecord;
    }

}
