/**
 *
 */
package org.commcare.models.database.global.models;

import org.commcare.models.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.models.framework.Table;
import org.commcare.modern.models.MetaField;

/**
 * An Application Record tracks an individual CommCare app on the current
 * install.
 *
 * @author ctsims
 * @author amstone
 */

@Table(ApplicationRecord.STORAGE_KEY)
public class ApplicationRecordV2 extends Persisted {

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
    protected String applicationId;
    @Persisting(2)
    @MetaField(META_STATUS)
    protected int status;
    @Persisting(3)
    protected String uniqueId;
    @Persisting(4)
    protected String displayName;
    @Persisting(5)
    protected boolean resourcesValidated;
    @Persisting(6)
    protected boolean isArchived;
    @Persisting(7)
    protected boolean convertedViaDbUpgrader;
    @Persisting(8)
    protected boolean preMultipleAppsProfile;
    @Persisting(9)
    protected int versionNumber;

    /**
     * Deserialization only
     */
    public ApplicationRecordV2() {

    }

}
