package org.commcare.android.logging;

import org.commcare.logging.AndroidLogPurger;
import org.commcare.logging.DeviceReportElement;
import org.commcare.models.database.SqlStorage;
import org.javarosa.core.log.LogEntry;
import org.javarosa.core.log.StreamLogSerializer;
import java.util.ArrayList;

/**
 * Convert force close error logs to xml.
 *
 * @author Aliza Stone
 */
public class ForceCloseLogSerializer extends StreamLogSerializer implements DeviceReportElement {

    private ForceCloseLogEntry singleEntry;
    private SqlStorage<ForceCloseLogEntry> logStorage;
    private ArrayList<ForceCloseLogEntry> logEntries = new ArrayList<>();
    private int index;

    public ForceCloseLogSerializer(ForceCloseLogEntry entry) {
        this.singleEntry = entry;
    }

    public ForceCloseLogSerializer(final SqlStorage<ForceCloseLogEntry> logStorage) {
        this.logStorage = logStorage;
        this.setPurger(new AndroidLogPurger<>(logStorage));
    }

    private void fetchEntries() {
        if (logEntries.isEmpty()) {
            for (ForceCloseLogEntry entry: logStorage) {
                addLog(entry.getID());
                logEntries.add(entry);
            }
        }
    }

    @Override
    public LogEntry getLogEntry() {
        try {
            if (singleEntry != null) {
                addLog(singleEntry.getID());
                return singleEntry;
            }
            fetchEntries();
            if (index >= logEntries.size()) { return null; }
            return logEntries.get(index++);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
