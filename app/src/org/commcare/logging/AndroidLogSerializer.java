package org.commcare.logging;

import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.models.database.SqlStorage;
import org.javarosa.core.log.LogEntry;
import org.javarosa.core.log.StreamLogSerializer;
import java.util.ArrayList;

/**
 * @author ctsims
 */
public class AndroidLogSerializer <T extends AndroidLogEntry>
        extends StreamLogSerializer implements DeviceReportElement {

    private AndroidLogEntry singleEntry;
    private SqlStorage<T> logStorage;
    private ArrayList<AndroidLogEntry> logEntries = new ArrayList<>();
    private int index;

    public AndroidLogSerializer(AndroidLogEntry entry) {
        this.singleEntry = entry;
    }

    public AndroidLogSerializer(final SqlStorage<T> logStorage) {
        this.logStorage = logStorage;
        this.setPurger(new AndroidLogPurger<>(logStorage));
    }

    private void fetchEntries() {
        if (logEntries.isEmpty()) {
            for (AndroidLogEntry entry: logStorage) {
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
