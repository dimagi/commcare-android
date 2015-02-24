/**
 * 
 */
package org.commcare.android.database.app.models;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commcare.android.crypt.CryptUtil;
import org.commcare.android.storage.framework.MetaField;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.android.storage.framework.Persisting;
import org.commcare.android.storage.framework.Table;
import org.javarosa.core.util.PropertyUtils;

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
    
    public static final Pattern HASH_STRING_PATTERN=Pattern.compile("([^\\$]+)\\$([^\\$]+)\\$([^\\$]+)");
    
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
    
    private static final int DEFAULT_SALT_LENGTH = 6;
    
    public static String generatePwdHash(String pwd) {
        return generatePwdHash(pwd, PropertyUtils.genGUID(DEFAULT_SALT_LENGTH).toLowerCase());
    }
    
    public static String extractSalt(String pwdString) {
        Matcher m = HASH_STRING_PATTERN.matcher(pwdString);
        if(m.matches()) { return m.group(2);}
        throw new IllegalArgumentException("Invalid pwd string for salt extraction");
    }
    
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
        if(HASH_STRING_PATTERN.matcher(hash).matches()) {
            if(hash.equals(UserKeyRecord.generatePwdHash(password, UserKeyRecord.extractSalt(hash)))) { return true; }
        }
        return false;
    }

    public byte[] unWrapKey(String password) {
        if(isPasswordValid(password)) {
            return CryptUtil.unWrapKey(getEncryptedKey(), password);
        } else {
            //throw exception?
            return null;
        }
    }
    
    public boolean isCurrentlyValid() {
        //currentTimeMillis is UTC
        Date today = new Date(System.currentTimeMillis());
        
        //Our validity dates are all in UTC
        if(validFrom.before(today) && (validTo == null || (validTo.getTime() != Long.MAX_VALUE && validTo.after(today)))) {
            return true;
        }
        return false;
    }
}
