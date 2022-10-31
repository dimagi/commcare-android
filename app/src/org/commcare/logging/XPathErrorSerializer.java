package org.commcare.logging;

import org.commcare.models.database.SqlStorage;
import org.javarosa.core.log.LogEntry;
import org.javarosa.core.log.StreamLogSerializer;
import java.util.ArrayList;

/**
 * Convert xpath error logs to xml
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class XPathErrorSerializer extends StreamLogSerializer implements DeviceReportElement {

    private final SqlStorage<XPathErrorEntry> errorLogStorage;
    private ArrayList<XPathErrorEntry> logEntries = new ArrayList<>();
    private int index;

    public XPathErrorSerializer(final SqlStorage<XPathErrorEntry> logStorage) {
        errorLogStorage = logStorage;
        this.setPurger(new AndroidLogPurger<>(errorLogStorage));
    }

    private void fetchEntries() {
        if (logEntries.isEmpty()) {
            for (XPathErrorEntry entry: errorLogStorage) {
                addLog(entry.getID());
                logEntries.add(entry);
            }
        }
    }

    @Override
    public LogEntry getLogEntry() {
        try {
            fetchEntries();
            if (index >= logEntries.size()) { return null; }
            return logEntries.get(index++);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
