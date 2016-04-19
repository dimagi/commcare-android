package org.commcare.android.database.global.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.models.framework.Table;
import org.commcare.modern.models.MetaField;

/**
 * The version of ApplicationRecord that exists in databases on any pre- multiple apps version
 * of CommCare. This class is used to read and make available an ApplicationRecord that exists in
 * such a database (for database upgrade purposes).
 *
 * @author amstone
 */
@Table(ApplicationRecord.STORAGE_KEY)
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
