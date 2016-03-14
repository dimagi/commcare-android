package org.commcare.android.logging;

import org.commcare.logging.AndroidLogPurger;
import org.commcare.logging.AndroidLogSerializer;
import org.commcare.logging.DeviceReportElement;
import org.commcare.logging.DeviceReportWriter;
import org.commcare.models.database.SqlStorage;
import org.javarosa.core.log.LogEntry;
import org.javarosa.core.log.StreamLogSerializer;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.util.SortedIntSet;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.Hashtable;

/**
 * Convert force close error logs to xml.
 *
 * @author Aliza Stone
 */
public class ForceCloseLogSerializer extends StreamLogSerializer implements DeviceReportElement {

    private ForceCloseLogEntry singleEntry;
    private SqlStorage<ForceCloseLogEntry> logStorage;
    private XmlSerializer serializer;

    public ForceCloseLogSerializer(ForceCloseLogEntry entry) {
        this.singleEntry = entry;
    }

    public ForceCloseLogSerializer(final SqlStorage<ForceCloseLogEntry> logStorage) {
        this.logStorage = logStorage;
        this.setPurger(new AndroidLogPurger<>(logStorage));
    }

    @Override
    public void writeToDeviceReport(XmlSerializer serializer) throws IOException {
        this.serializer = serializer;

        serializer.startTag(DeviceReportWriter.XMLNS, "force_close_subreport");
        try {
            if (singleEntry != null) {
                serializeLog(singleEntry.getID(), singleEntry);
            } else {
                for (ForceCloseLogEntry entry : logStorage) {
                    serializeLog(entry.getID(), entry);
                }
            }
        } finally {
            serializer.endTag(DeviceReportWriter.XMLNS, "force_close_subreport");
        }
    }

    @Override
    protected void serializeLog(LogEntry entry) throws IOException {
        final ForceCloseLogEntry forceCloseEntry = (ForceCloseLogEntry)entry;
        String dateString =
                DateUtils.formatDateTime(forceCloseEntry.getTime(), DateUtils.FORMAT_ISO8601);

        serializer.startTag(DeviceReportWriter.XMLNS, "force_close");
        try {
            serializer.attribute(null, "date", dateString);
            AndroidLogSerializer.writeText("type",
                    forceCloseEntry.getType(), serializer);
            AndroidLogSerializer.writeText("msg",
                    forceCloseEntry.getMessage(), serializer);
            AndroidLogSerializer.writeText("build_number",
                    forceCloseEntry.getAppBuildNumber() + "", serializer);
            AndroidLogSerializer.writeText("android_version",
                    forceCloseEntry.getAndroidVersion(), serializer);
            AndroidLogSerializer.writeText("device_model",
                    forceCloseEntry.getDeviceModel(), serializer);
            AndroidLogSerializer.writeText("session_readable",
                    forceCloseEntry.getReadableSession(), serializer);
            AndroidLogSerializer.writeText("session_serialized",
                    forceCloseEntry.getSerializedSessionString(), serializer);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            serializer.endTag(DeviceReportWriter.XMLNS, "force_close");
        }
    }

}
