/**
 * 
 */
package org.commcare.android.database.global.models;

import org.commcare.android.storage.framework.MetaField;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.android.storage.framework.Persisting;
import org.commcare.android.storage.framework.Table;

/**
 * An Application Record tracks an individual CommCare app on the current
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
	@Persisting(4)
	String displayName;
	@Persisting(5)
	boolean isArchived;
	@Persisting(6)
	boolean resourcesValidated;

	
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
	
	public void setDisplayName(String appName) {
		this.displayName = appName;
	}
	
	public String getDisplayName() {
		return this.displayName;
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
	
	public void setArchiveStatus(boolean b) {
		//this.isArchived = b;
	}
	
	@Override
	public String toString() {
		//TODO: change this to return displayName when it is available
		return this.uniqueId;
	}
	
	public void setResourcesValidated() {
		this.resourcesValidated = true;
	}
	
	public boolean resourcesValidated() {
		return this.resourcesValidated;
	}
	
}
