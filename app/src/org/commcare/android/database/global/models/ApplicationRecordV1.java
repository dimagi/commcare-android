package org.commcare.android.database.global.models;

import org.commcare.android.storage.framework.MetaField;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.android.storage.framework.Persisting;
import org.commcare.android.storage.framework.Table;

/**
 * The previous version of ApplicationRecord in the database,
 * used for database upgrade purposes
 * 
 * @author amstone
 */
@Table("app_record")
public class ApplicationRecordV1 extends Persisted {
    private static final String META_STATUS = "status";
    
    @Persisting(1)
    private String applicationId;
    @Persisting(2)
    @MetaField(META_STATUS)
    private int status;
    
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
