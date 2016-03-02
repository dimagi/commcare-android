package org.commcare.logging;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;

/**
 * @author ctsims
 */
public interface DeviceReportElement {
    void writeToDeviceReport(XmlSerializer serializer) throws IOException;
}
