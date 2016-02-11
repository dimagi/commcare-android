package org.commcare.android.logging;

import org.javarosa.core.log.LogEntry;
import org.javarosa.core.log.StreamLogSerializer;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;

/**
 * Created by amstone326 on 2/11/16.
 */
public class ForceCloseLogSerializer extends StreamLogSerializer implements DeviceReportElement {

    public ForceCloseLogSerializer(ForceCloseLogEntry entry) {

    }

    @Override
    public void writeToDeviceReport(XmlSerializer serializer) throws IOException {

    }

    @Override
    protected void serializeLog(LogEntry entry) throws IOException {

    }
}
