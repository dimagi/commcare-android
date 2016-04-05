package org.commcare.logging;

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
 * Convert xpath error logs to xml
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class XPathErrorSerializer
        extends StreamLogSerializer
        implements DeviceReportElement {
    private final SqlStorage<XPathErrorEntry> errorLogStorage;
    private XmlSerializer serializer;

    /**
     * Report format version for ability to dispatch different parser on server
     */
    private static final int ERROR_FORMAT_VERSION = 1;

    public XPathErrorSerializer(final SqlStorage<XPathErrorEntry> logStorage) {
        errorLogStorage = logStorage;
        this.setPurger(new AndroidLogPurger<>(errorLogStorage));
    }

    @Override
    public void writeToDeviceReport(XmlSerializer serializer) throws IOException {
        this.serializer = serializer;

        serializer.startTag(DeviceReportWriter.XMLNS, "user_error_subreport");
        serializer.attribute(null, "version", ERROR_FORMAT_VERSION + "");

        try {
            for (XPathErrorEntry entry : errorLogStorage) {
                serializeLog(entry.getID(), entry);
            }
        } finally {
            serializer.endTag(DeviceReportWriter.XMLNS, "user_error_subreport");
        }
    }

    @Override
    protected void serializeLog(LogEntry entry) throws IOException {
        final XPathErrorEntry errorEntry = (XPathErrorEntry)entry;
        String dateString =
                DateUtils.formatDateTime(errorEntry.getTime(), DateUtils.FORMAT_ISO8601);

        serializer.startTag(DeviceReportWriter.XMLNS, "user_error");
        try {
            serializer.attribute(null, "date", dateString);
            AndroidLogSerializer.writeText("type", errorEntry.getType(), serializer);
            AndroidLogSerializer.writeText("msg", errorEntry.getMessage(), serializer);
            AndroidLogSerializer.writeText("user_id", errorEntry.getUserId(), serializer);
            AndroidLogSerializer.writeText("session", errorEntry.getSessionPath(), serializer);
            AndroidLogSerializer.writeText("app_build", errorEntry.getAppVersion() + "", serializer);
            AndroidLogSerializer.writeText("app_id", errorEntry.getAppId(), serializer);
            AndroidLogSerializer.writeText("expr", errorEntry.getExpression(), serializer);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            serializer.endTag(DeviceReportWriter.XMLNS, "user_error");
        }
    }

}
