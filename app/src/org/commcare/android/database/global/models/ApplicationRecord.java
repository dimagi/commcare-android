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
 * @author amstone
 */

@Table(ApplicationRecord.STORAGE_KEY)
public class ApplicationRecord extends Persisted {

    /**
     * Name of database that stores application records
     */
    public static final String STORAGE_KEY = "app_record";
    private static final String META_STATUS = "status";
    
    public static final int STATUS_UNINITIALIZED = 0;
    public static final int STATUS_INSTALLED = 1;
    public static final int STATUS_DELETE_REQUESTED = 2;
    /**
     * The app needs to be upgraded from an old version
     */
    public static final int STATUS_SPECIAL_LEGACY = 2;

    @Persisting(1)
    private String applicationId;
    @Persisting(2)
    @MetaField(META_STATUS)
    private int status;
    @Persisting(3)
    private String uniqueId;
    @Persisting(4)
    private String displayName;
    @Persisting(5)
    private boolean resourcesValidated;
    @Persisting(6)
    private boolean isArchived;
    @Persisting(7)
    private boolean convertedViaDbUpgrader;
    @Persisting(8)
    private boolean preMultipleAppsProfile;
    @Persisting(9)
    private int versionNumber;
    
    /**
     * Deserialization only
     */
    public ApplicationRecord() {
        
    }
    
    public ApplicationRecord(String applicationId, int status) {
        this.applicationId = applicationId;
        this.status = status;
        // initialize to -1 so we know when it has not yet been set from the profile
        this.versionNumber = -1;
    }
    
    public String getApplicationId() {
        return applicationId;
    }
    
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String id) {
        this.uniqueId = id;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String appName) {
        this.displayName = appName;
    }

    public int getVersionNumber() {
        return this.versionNumber;
    }

    public void setVersionNumber(int version) {
        this.versionNumber = version;
    }

    public void setArchiveStatus(boolean b) {
        this.isArchived = b;
    }

    public boolean isArchived() {
        return this.isArchived;
    }

    @Override
    public String toString() {
        return this.displayName;
    }

    public void setResourcesStatus(boolean b) {
        this.resourcesValidated = b;
    }

    public boolean resourcesValidated() {
        return this.resourcesValidated;
    }

    /**
     * Returns true if this ApplicationRecord represents an app generated from
     * a pre-Multiple Apps version of CommCare that does not have profile files
     * with uniqueId and displayName
     */
    public boolean preMultipleAppsProfile() {
        return this.preMultipleAppsProfile;
    }
    
    public void setPreMultipleAppsProfile(boolean b) {
        this.preMultipleAppsProfile = b;
    }

    /**
     * Returns true if this ApplicationRecord was just generated from the a
     * different ApplicationRecord format via the db upgrader, because it was 
     * initially installed on a phone with a pre-Multiple Apps version of CommCare
     */
    public boolean convertedByDbUpgrader() {
        return this.convertedViaDbUpgrader;
    }

    public void setConvertedByDbUpgrader(boolean b) {
        this.convertedViaDbUpgrader = b;
    }

}
