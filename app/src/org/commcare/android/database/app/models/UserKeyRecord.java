/**
 * 
 */
package org.commcare.android.database.app.models;

import java.util.Date;

import org.commcare.android.storage.framework.MetaField;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.android.storage.framework.Persisting;

/**
 * @author ctsims
 *
 */
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
	
	/**
	 * Serialization Only!
	 */
	public UserKeyRecord() {
		
	}
	
	public UserKeyRecord(String username, String passwordHash, byte[] encryptedKey, Date validFrom, Date validTo) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.encryptedKey = encryptedKey;
		this.validFrom = validFrom;
		this.validTo = validTo;
	}
}
