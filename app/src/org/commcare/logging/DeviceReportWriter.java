package org.commcare.logging;

import org.commcare.android.javarosa.DeviceReportRecord;
import org.commcare.util.JsonUtil;
import org.javarosa.core.log.LogEntry;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.PriorityQueue;

/**
 * This class generates and serializes a device report to either a byte array
 * or to a file as designated by a log record
 *
 * @author ctsims
 */
public class DeviceReportWriter {

    private final OutputStream os;
    private final ArrayList<DeviceReportElement> elements = new ArrayList<>();

    public DeviceReportWriter(DeviceReportRecord record) throws IOException {
        this(record.openOutputStream());
    }

    public DeviceReportWriter(OutputStream outputStream) throws IOException {
        os = outputStream;
    }


    public void addReportElement(DeviceReportElement element) {
        this.elements.add(element);
    }

    class LogEntryModel implements Comparable<LogEntryModel> {
        LogEntry entry;
        int index; // Denotes the index of array.
        LogEntryModel(LogEntry entry, int index) {
            this.entry = entry;
            this.index = index;
        }

        @Override
        public int compareTo(LogEntryModel logEntryModel) {
            if (this.entry == null || logEntryModel.entry == null) {
                return 0;
            }
            return this.entry.getTime().compareTo(logEntryModel.entry.getTime());
        }
    }

    public void write() throws IllegalArgumentException, IllegalStateException, IOException {
        try {
            PriorityQueue<LogEntryModel> logEntries = new PriorityQueue<>(elements.size());
            for (int i = 0; i < elements.size(); i++) {
                LogEntry entry = elements.get(i).getLogEntry();
                if (entry == null) { continue; }
                logEntries.add(new LogEntryModel(entry, i));
            }

            OutputStreamWriter writer = new OutputStreamWriter(os);
            try {
                while (!logEntries.isEmpty()) {
                    LogEntryModel model = logEntries.poll();
                    if (model == null || model.entry == null) {
                        continue;
                    }
                    writer.write(JsonUtil.getJsonFromObject(model.entry, model.entry.getClass()));
                    writer.write("\n");
                    int index = model.index;
                    LogEntry entry = elements.get(index).getLogEntry();
                    if (entry == null) {
                        continue;
                    }
                    logEntries.add(new LogEntryModel(entry, index));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    writer.close();
                } catch (IOException e) { }
            }
        } finally {
            try {
                os.close();
            } catch (IOException e) {
            }
        }
    }
}
