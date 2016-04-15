package org.commcare.logging;

import org.commcare.android.logging.AndroidLogEntry;
import org.commcare.models.database.SqlStorage;
import org.javarosa.core.log.LogEntry;
import org.javarosa.core.log.StreamLogSerializer;
import org.javarosa.core.model.utils.DateUtils;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;

/**
 * @author ctsims
 */
public class AndroidLogSerializer <T extends AndroidLogEntry>
        extends StreamLogSerializer implements DeviceReportElement {

    private XmlSerializer serializer;

    private AndroidLogEntry singleEntry;
    private SqlStorage<T> logStorage;

    public AndroidLogSerializer(AndroidLogEntry entry) {
        this.singleEntry = entry;
    }

    public AndroidLogSerializer(final SqlStorage<T> logStorage) {
        this.logStorage = logStorage;
        this.setPurger(new AndroidLogPurger<>(logStorage));
    }

    @Override
    protected void serializeLog(LogEntry entry) throws IOException {
        String dateString = DateUtils.formatDateTime(entry.getTime(), DateUtils.FORMAT_ISO8601);

        serializer.startTag(DeviceReportWriter.XMLNS, "log");
        try {
            serializer.attribute(null, "date", dateString);
            writeText("type", entry.getType(), serializer);
            writeText("msg", entry.getMessage(), serializer);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            serializer.endTag(DeviceReportWriter.XMLNS, "log");
        }
    }

    public static void writeText(String element, String text, XmlSerializer serializer)
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

    @Override
    public void writeToDeviceReport(XmlSerializer serializer) throws IOException {
        this.serializer = serializer;

        serializer.startTag(DeviceReportWriter.XMLNS, "log_subreport");

        try {
            if (singleEntry != null) {
                serializeLog(singleEntry.getID(), singleEntry);
            }
            else {
                for (AndroidLogEntry entry : logStorage) {
                    serializeLog(entry.getID(), entry);
                }
            }
        } finally {
            serializer.endTag(DeviceReportWriter.XMLNS, "log_subreport");
        }
    }
}
