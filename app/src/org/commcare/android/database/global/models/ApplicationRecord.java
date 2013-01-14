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
	
	public static final int STATUS_INITIALIZED = 0;
	public static final int STATUS_INSTALLED = 1;
	
	
	@Persisting
	private String applicationId;
	@Persisting
	@MetaField(META_STATUS)
	protected int status;
	
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
	
	public int getStatus() {
		return status;
	}
}
