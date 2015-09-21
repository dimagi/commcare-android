/**
 * 
 */
package org.commcare.android.database.global.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.android.storage.framework.Persisting;
import org.commcare.android.storage.framework.Table;
import org.commcare.modern.models.MetaField;
import org.commcare.suite.model.Profile;
import org.javarosa.core.services.locale.Localization;

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

    public void setResourcesStatus(boolean b) {
        this.resourcesValidated = b;
    }

    public boolean resourcesValidated() {
        return this.resourcesValidated;
    }

    /**
     * A 'visible' app record has status installed and is not archived
     */
    public boolean isVisible() {
        return status == STATUS_INSTALLED && !isArchived;
    }

    /**
     *  A 'usable' app record is one that will actually get shown to a user -- is visible and has
     *  its MM resources validated
     */
    public boolean isUsable() {
        return isVisible() && resourcesValidated;
    }

    @Override
    public String toString() {
        return this.displayName;
    }

    /**
     * Returns true if this ApplicationRecord represents an app generated from
     * a pre-Multiple Apps version of CommCare that does not have profile files
     * with uniqueId and displayName
     */
    public boolean isPreMultipleAppsProfile() {
        return this.preMultipleAppsProfile;
    }

    /**
     * Returns true if this ApplicationRecord was just generated from the a
     * different ApplicationRecord format via the db upgrader, because it was 
     * initially installed on a phone with a pre-Multiple Apps version of CommCare
     */
    public boolean wasConvertedByDbUpgrader() {
        return this.convertedViaDbUpgrader;
    }

    /**
     * Used when this record is either first installed, or upgraded from an old version, to set all
     * properties of the record that come from its profile file
     */
    public void setPropertiesFromProfile(Profile p) {
        this.uniqueId = p.getUniqueId();
        this.displayName = p.getDisplayName();
        if ("".equals(displayName)) {
            // If this profile didn't have a display name, get it from Localization strings instead
            displayName = Localization.get("app.display.name");
        }
        this.versionNumber = p.getVersion();
        this.preMultipleAppsProfile = p.isOldVersion();
    }


    // region: methods used only in the upgrade process for an ApplicationRecord, should not be
    // touched otherwise

    public void setPreMultipleAppsProfile(boolean b) {
        this.preMultipleAppsProfile = b;
    }

    public void setConvertedByDbUpgrader(boolean b) {
        this.convertedViaDbUpgrader = b;
    }

    // endregion

}
