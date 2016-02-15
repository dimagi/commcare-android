package org.commcare.android.logging;

import org.javarosa.core.api.ILogger;
import org.javarosa.core.log.IFullLogSerializer;
import org.javarosa.core.log.StreamLogSerializer;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

/**
 * This class keeps track of logs before the app has fully initialized its storage engine
 *
 * @author ctsims
 */
public class PreInitLogger implements ILogger {
    private final ArrayList<AndroidLogEntry> logs = new ArrayList<>();

    public PreInitLogger() {

    }

    @Override
    public void log(String type, String message, Date logDate) {
        logs.add(new AndroidLogEntry(type, message, logDate));
    }

    public void dumpToNewLogger() {
        for (AndroidLogEntry log : logs) {
            if (Logger._() != null) {
                Logger._().log(log.getType(), log.getMessage(), log.getTime());
            }
        }
    }

    @Override
    public void clearLogs() {

    }

    @Override
    public <T> T serializeLogs(IFullLogSerializer<T> serializer) {
        return null;
    }

    @Override
    public void serializeLogs(StreamLogSerializer serializer) throws IOException {

    }

    @Override
    public void serializeLogs(StreamLogSerializer serializer, int limit) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void panic() {
        // TODO Auto-generated method stub

    }

    @Override
    public int logSize() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void halt() {
        // TODO Auto-generated method stub
    }
}
