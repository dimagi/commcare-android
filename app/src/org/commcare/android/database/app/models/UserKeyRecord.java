/**
 * 
 */
package org.commcare.android.database.app.models;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

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
	
	public static String generatePwdHash(String pwd) {
		String alg = "sha1";
		
		int saltLength = 6;
		int hashLength = 41;
		
		String salt = PropertyUtils.genGUID(saltLength).toLowerCase();
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
		try {
			String hash = this.getPasswordHash();
			if(hash.contains("$")) {
	    		String alg = "sha1";
	    		String salt = hash.split("\\$")[1];
	    		String check = hash.split("\\$")[2];
	    		MessageDigest md = MessageDigest.getInstance("SHA-1");
	    		BigInteger number = new BigInteger(1, md.digest((salt+password).getBytes()));
	    		String hashed = number.toString(16);
	    		
	    		while(hashed.length() < check.length()) {
	    			hashed = "0" + hashed;
	    		}
	    		
	    		if(hash.equals(alg + "$" + salt + "$" + hashed)) {
	    			return true;
	    		}
	    	}
			return false;
		} catch (NoSuchAlgorithmException  nsae) {
			throw new RuntimeException("SHA-1 support not present!");
		}
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
		Date today = new Date();
		if(validFrom.before(today) && validTo == null || validTo.after(today)) {
			return true;
		}
		return false;
	}
}
