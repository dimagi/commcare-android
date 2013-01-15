/**
 * 
 */
package org.commcare.android.database.app.models;

import java.util.Date;

import org.commcare.android.storage.framework.MetaField;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.android.storage.framework.Persisting;
import org.commcare.android.storage.framework.Table;

/**
 * @author ctsims
 *
 */
@Table("user_key_records")
public class UserKeyRecord extends Persisted {
	
	public static final String META_USERNAME = "username";
	
	@Persisting
	@MetaField(META_USERNAME)
	private String username;
	@Persisting
	private String passwordHash;
	@Persisting
	private byte[] encryptedKey;
	@Persisting
	private Date validFrom;
	@Persisting
	private Date validTo;
	@Persisting
	/** The unique ID of the data sandbox covered by this key **/
	private String uuid;
	
	/**
	 * Serialization Only!
	 */
	public UserKeyRecord() {
		
	}
	
	public UserKeyRecord(String username, String passwordHash, byte[] encryptedKey, Date validFrom, Date validTo, String uuid) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.encryptedKey = encryptedKey;
		this.validFrom = validFrom;
		this.validTo = validTo;
		this.uuid = uuid;
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
}
