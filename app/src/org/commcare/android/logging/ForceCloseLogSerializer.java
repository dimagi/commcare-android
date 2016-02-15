package org.commcare.android.logging;

import org.commcare.android.database.SqlStorage;
import org.javarosa.core.log.LogEntry;
import org.javarosa.core.log.StreamLogSerializer;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.util.SortedIntSet;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.Hashtable;

/**
 * Convert xpath error logs to xml.
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

        this.setPurger(new Purger() {
            @Override
            public void purge(final SortedIntSet IDs) {
                logStorage.removeAll(new EntityFilter<LogEntry>() {
                    public int preFilter(int id, Hashtable<String, Object> metaData) {
                        return IDs.contains(id) ? PREFILTER_INCLUDE : PREFILTER_EXCLUDE;
                    }

                    public boolean matches(LogEntry e) {
                        throw new RuntimeException("can't happen");
                    }
                });
            }
        });
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
            writeText("type", forceCloseEntry.getType());
            writeText("msg", forceCloseEntry.getMessage());
            writeText("build_number", forceCloseEntry.getAppBuildNumber() + "");
            writeText("android_version", forceCloseEntry.getAndroidVersion());
            writeText("device_model", forceCloseEntry.getDeviceModel());
            writeText("session_readable", forceCloseEntry.getReadableSession());
            writeText("session_serialized", forceCloseEntry.getSerializedSessionString());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            serializer.endTag(DeviceReportWriter.XMLNS, "force_close");
        }
    }

    private void writeText(String element, String text)
            throws IllegalArgumentException, IllegalStateException, IOException {
        serializer.startTag(DeviceReportWriter.XMLNS, element);
        try {
            serializer.text(text);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            serializer.endTag(DeviceReportWriter.XMLNS, element);
        }
    }
}
