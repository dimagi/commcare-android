package org.commcare.android.javarosa;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;

/**
 * Legacy model for DeviceReportRecord prior to v30, which stored a per-record AES key.
 * Used only during database migration from v29 to v30.
 */
@Table(DeviceReportRecord.STORAGE_KEY)
public class DeviceReportRecordPreV30 extends Persisted {

    @Persisting(1)
    private String fileName;

    @Persisting(2)
    private byte[] aesKey;

    public DeviceReportRecordPreV30() {
        // for externalization
    }

    public String getFilePath() {
        return fileName;
    }

    public byte[] getAesKey() {
        return aesKey;
    }
}