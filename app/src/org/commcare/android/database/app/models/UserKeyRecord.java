/**
 * 
 */
package org.commcare.android.database.app.models;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

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
	
	public static final int TYPE_NORMAL = 1;
	public static final int TYPE_LEGACY_TRANSITION = 2;
	public static final int TYPE_LEGACY_TRANSITION_PARTIAL = 3;
	
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
	private String uuid;
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
}
