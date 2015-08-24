/**
 * 
 */
package org.commcare.android.database.app.models;

import org.commcare.android.crypt.CryptUtil;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.android.storage.framework.Persisting;
import org.commcare.android.storage.framework.Table;
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
 *
 */
@Table("user_key_records")
public class UserKeyRecord extends Persisted {
    
    public static final String META_USERNAME = "username";
    public static final String META_SANDBOX_ID = "sandbox_id";
    public static final String META_KEY_STATUS = "status";
    
    /** This is a normal sandbox record that is ready to be used **/
    public static final int TYPE_NORMAL = 1;
    /** This is a record representing a legacy database that should be transfered over **/
    public static final int TYPE_LEGACY_TRANSITION = 2;
    /** This is a new record that hasn't been evaluated for usage yet **/
    public static final int TYPE_NEW = 3;
    /** This is a new record that hasn't been evaluated for usage yet **/
    public static final int TYPE_PENDING_DELETE = 4;
    
    // Hashed passwords should contain 3 groupings that are delimited by '$'.
    // The 1st group describes the hashing algorithm, the 2nd is the salt, and
    // the 3rd group is the digest.
    public static final Pattern HASH_STRING_PATTERN = Pattern.compile("([^\\$]+)\\$([^\\$]+)\\$([^\\$]+)");
    
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
    @Persisting(6)
    /** The unique ID of the data sandbox covered by this key **/
    @MetaField(META_SANDBOX_ID)
    private String uuid;
    @MetaField(META_KEY_STATUS)
    @Persisting(7)
    private int type;
    
    /**
     * Serialization Only!
     */
    public UserKeyRecord() {
        
    }
    
    public UserKeyRecord(String username, String passwordHash, byte[] encryptedKey, Date validFrom, Date validTo, String uuid) {
        this(username, passwordHash, encryptedKey, validFrom, validTo, uuid, TYPE_NORMAL);
    }
    
    public UserKeyRecord(String username, String passwordHash, byte[] encryptedKey, Date validFrom, Date validTo, String uuid, int type) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.encryptedKey = encryptedKey;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.uuid = uuid;
        this.type = type;
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
    
    /**
     * Build a SHA-1 password hash out of a password string, where the salt is
     * generated to be a globally unique string.  The hash is delimited by '$'.
     *
     * @param pwd is the plain-text password inputted by the user.
     *
     * @return SHA-1 hashed password
     */
    public static String generatePwdHash(String pwd) {
        return generatePwdHash(pwd, PropertyUtils.genGUID(DEFAULT_SALT_LENGTH).toLowerCase());
    }
    
    /**
     * Grab the salt out of a hashed password.
     *
     * @param pwdString a String with three groups delimited by '$', the second
     * containing the salt
     *
     * @return salt String out of a hashed password
     */
    public static String extractSalt(String pwdString) {
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
     * @param pwd is the plain-text password inputted by the user.
     * @param salt is a random string included during hashing to prevent
     * against hash dictionary attacks.
     *
     * @return SHA-1 hashed password
     */
    public static String generatePwdHash(String pwd, String salt) {
        String alg = "sha1";
        int hashLength = 41;

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        BigInteger number = new BigInteger(1, md.digest((salt+pwd).getBytes()));
        String hashed = number.toString(16);
        
        // prepend 0's until the hash is of the correct length
        while(hashed.length() < hashLength) {
            hashed = "0" + hashed;
        }
        
        return alg + "$" + salt + "$" + hashed;
    }

    public void setType(int typeNormal) {
        this.type = typeNormal;
    }

    public boolean isPasswordValid(String password) {
        String hash = this.getPasswordHash();

        // Is the local hash value a valid hash string pattern
        // and does it match the hashed password (using the extracted salt)?
        return (HASH_STRING_PATTERN.matcher(hash).matches() &&
                hash.equals(UserKeyRecord.generatePwdHash(password, UserKeyRecord.extractSalt(hash))));
    }

    public byte[] unWrapKey(String password) {
        if(isPasswordValid(password)) {
            return CryptUtil.unWrapKey(getEncryptedKey(), password);
        } else {
            //throw exception?
            return null;
        }
    }
    
    /**
     * Does today lie within the record's validity range.
     *
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
}
