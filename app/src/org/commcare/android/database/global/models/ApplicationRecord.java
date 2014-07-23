/**
 * 
 */
package org.commcare.android.database.global.models;

import org.commcare.android.storage.framework.MetaField;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.android.storage.framework.Persisting;
import org.commcare.android.storage.framework.Table;

/**
 * An Application Record tracks an indvidiual CommCare app on the current
 * install.
 * 
 * @author ctsims
 *
 */
@Table("app_record")
public class ApplicationRecord extends Persisted {
	public static final String META_STATUS = "status";
	
	public static final int STATUS_UNINITIALIZED = 0;
	public static final int STATUS_INSTALLED = 1;
	/**
	 * The app needs to be upgraded from an old version
	 */
	public static final int STATUS_SPECIAL_LEGACY = 2;
	
	
	@Persisting(1)
	String applicationId;
	@Persisting(2)
	@MetaField(META_STATUS)
	int status;
	@Persisting(3)
	String uniqueId;
	
	/*
	 * Deserialization only
	 */
	public ApplicationRecord() {
		
	}
	
	public ApplicationRecord(String applicationId, int status) {
		this.applicationId = applicationId;
		this.status = status;
	}
	
	public String getApplicationId() {
		return applicationId;
	}
	
	public void setUniqueId(String id) {
		this.uniqueId = id;
	}
	
	public String getUniqueId() {
		return uniqueId;
	}
	
	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}
}
