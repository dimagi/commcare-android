/**
 * 
 */
package org.commcare.android.javarosa;

import java.io.IOException;

import org.xmlpull.v1.XmlSerializer;

/**
 * @author ctsims
 *
 */
public interface DeviceReportElement {
    public void writeToDeviceReport(XmlSerializer serializer) throws IOException;
}
