package org.commcare.logging;

import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.models.database.SqlStorage;
import org.commcare.utils.CrashUtil;
import org.javarosa.core.api.ILogger;
import org.javarosa.core.log.IFullLogSerializer;
import org.javarosa.core.log.LogEntry;
import org.javarosa.core.log.StreamLogSerializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

/**
 * Logging engine for CommCare ODK Environments.
 *
 * @author ctsims
 */
public class AndroidLogger implements ILogger {

    //TODO: Currently assumes that it gets back iterated records in RecordID order.
    //when serializing a limited number of records then clearing

    private final SqlStorage<AndroidLogEntry> storage;


    public AndroidLogger(SqlStorage<AndroidLogEntry> storage) {
        this.storage = storage;
    }

    @Override
    public void log(String type, String message, Date logDate) {
        storage.write(new AndroidLogEntry(type, message, logDate));
        CrashUtil.log(message);
    }

    @Override
    public void clearLogs() {
        storage.removeAll();
    }

    @Override
    public <T> T serializeLogs(IFullLogSerializer<T> serializer) {
        ArrayList<LogEntry> logs = new ArrayList<>();
        for (AndroidLogEntry entry : storage) {
            logs.add(entry);
        }
        return serializer.serializeLogs(logs.toArray(new LogEntry[logs.size()]));
    }

    @Override
    public void serializeLogs(StreamLogSerializer serializer) throws IOException {
        for (AndroidLogEntry entry : storage) {
            serializer.addLog(entry.getID());
        }
    }

    @Override
    public void serializeLogs(StreamLogSerializer serializer, int limit) throws IOException {
        int count = 0;
        for (AndroidLogEntry entry : storage) {
            serializer.addLog(entry.getID());
            count++;
            if (count > limit) {
                break;
            }
        }
    }

    @Override
    public void panic() {
        //Unclear
    }

    @Override
    public int logSize() {
        return storage.getNumRecords();
    }

    @Override
    public void halt() {
        //Meh.
    }
}
