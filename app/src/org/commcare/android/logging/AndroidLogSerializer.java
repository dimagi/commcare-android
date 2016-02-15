package org.commcare.android.logging;

import org.commcare.android.database.SqlStorage;
import org.javarosa.core.log.LogEntry;
import org.javarosa.core.log.StreamLogSerializer;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.util.SortedIntSet;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

/**
 * @author ctsims
 */
public class AndroidLogSerializer extends StreamLogSerializer implements DeviceReportElement {

    private XmlSerializer serializer;

    // The entire storage object of all log entries
    private final SqlStorage<AndroidLogEntry> logStorage;


    public AndroidLogSerializer(final SqlStorage<AndroidLogEntry> logStorage) {
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
    protected void serializeLog(LogEntry entry) throws IOException {
        String dateString = DateUtils.formatDateTime(entry.getTime(), DateUtils.FORMAT_ISO8601);

        serializer.startTag(DeviceReportWriter.XMLNS, "log");
        try {
            serializer.attribute(null, "date", dateString);
            writeText("type", entry.getType());
            writeText("msg", entry.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            serializer.endTag(DeviceReportWriter.XMLNS, "log");
        }
    }

    private void writeText(String element, String text) throws IllegalArgumentException, IllegalStateException, IOException {
        serializer.startTag(DeviceReportWriter.XMLNS, element);
        try {
            serializer.text(text);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            serializer.endTag(DeviceReportWriter.XMLNS, element);
        }
    }

    @Override
    public void writeToDeviceReport(XmlSerializer serializer) throws IOException {
        this.serializer = serializer;

        serializer.startTag(DeviceReportWriter.XMLNS, "log_subreport");

        try {
            for (AndroidLogEntry entry : logStorage) {
                serializeLog(entry.getID(), entry);
            }
        } finally {
            serializer.endTag(DeviceReportWriter.XMLNS, "log_subreport");
        }
    }
}
