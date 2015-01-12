/**
 * 
 */
package org.commcare.android.database.global.models;

import org.commcare.android.storage.framework.MetaField;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.android.storage.framework.Persisting;
import org.commcare.android.storage.framework.Table;

/**
 * The previous version of ApplicationRecord in the database,
 * used for database upgrade purposes
 * 
 * @author astone
 *
 */
@Table("app_record")
public class ApplicationRecordV1 extends Persisted {
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
    
    /*
     * Deserialization only
     */
    public ApplicationRecordV1() {
        
    }
    
    public String getApplicationId() {
        return applicationId;
    }
    
    public int getStatus() {
        return status;
    }
}