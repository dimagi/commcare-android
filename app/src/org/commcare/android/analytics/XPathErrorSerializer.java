package org.commcare.android.analytics;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.javarosa.DeviceReportElement;
import org.commcare.android.javarosa.DeviceReportWriter;
import org.javarosa.core.log.LogEntry;
import org.javarosa.core.log.StreamLogSerializer;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.util.SortedIntSet;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.Hashtable;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class XPathErrorSerializer extends StreamLogSerializer implements DeviceReportElement {
    private final SqlStorage<XPathErrorEntry> errorLogStorage;
    private XmlSerializer serializer;

    public XPathErrorSerializer(final SqlStorage<XPathErrorEntry> logStorage) {
        errorLogStorage = logStorage;
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

        serializer.startTag(DeviceReportWriter.XMLNS, "xpath_error_subreport");

        try {
            for (XPathErrorEntry entry : errorLogStorage) {
                serializeLog(entry.getID(), entry);
            }
        } finally {
            serializer.endTag(DeviceReportWriter.XMLNS, "xpath_error_subreport");
        }
    }

    @Override
    protected void serializeLog(LogEntry entry) throws IOException {
        final XPathErrorEntry errorEntry = (XPathErrorEntry)entry;
        String dateString = DateUtils.formatDateTime(errorEntry.getTime(), DateUtils.FORMAT_ISO8601);

        serializer.startTag(DeviceReportWriter.XMLNS, "xpath_error");
        try {
            serializer.attribute(null, "date", dateString);
            writeText("type", errorEntry.getType());
            writeText("msg", errorEntry.getMessage());
            writeText("ssn", errorEntry.getSessionPath());
            writeText("expr", errorEntry.getExpression());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            serializer.endTag(DeviceReportWriter.XMLNS, "xpath_error");
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
}
