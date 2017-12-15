package org.commcare.android.javarosa;

import org.commcare.models.framework.Persisting;

/**
 * Represents the format of DeviceReportRecord that existed on all devices on user DB versions 22
 * and below (CommCare versions 2.41 and below)
 *
 * @author Aliza Stone
 */

public class DeviceReportRecordV1 extends DeviceReportRecord {

    @Persisting(1)
    private String fileName;
    @Persisting(2)
    private byte[] aesKey;

}
