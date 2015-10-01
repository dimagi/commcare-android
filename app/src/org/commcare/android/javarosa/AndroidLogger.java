package org.commcare.android.javarosa;

import org.commcare.android.database.SqlStorage;
import org.javarosa.core.api.ILogger;
import org.javarosa.core.log.IFullLogSerializer;
import org.javarosa.core.log.LogEntry;
import org.javarosa.core.log.StreamLogSerializer;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.services.storage.StorageFullException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;


/**
 * Logging engine for CommCare ODK Environments.
 *
 * @author ctsims
 */
public class AndroidLogger implements ILogger {

    //Log Types:
    /**
     * Fatal problem with one of CommCare's cryptography libraries
     */
    public static final String TYPE_ERROR_CRYPTO = "error-crypto";

    /**
     * Some invariant application assumption has been violated
     */
    public static final String TYPE_ERROR_ASSERTION = "error-state";

    /**
     * Some invariant application assumption has been violated
     */
    public static final String TYPE_ERROR_WORKFLOW = "error-workflow";

    /**
     * There is a problem with the underlying storage layer which is preventing the app from working correctly
     */
    public static final String TYPE_ERROR_STORAGE = "error-storage";

    /**
     * One of the config files (suite, profile, xform, locale, etc) contains something
     * which is invalid and prevented the app from working properly
     */
    public static final String TYPE_ERROR_CONFIG_STRUCTURE = "error-config";

    /**
     * Something bad happened which the app should not have allowed to happen. This
     * category of error should be aggressively caught and addressed by the software team *
     */
    public static final String TYPE_ERROR_DESIGN = "error-design";

    /**
     * Something bad happened because of network connectivity *
     */
    public static final String TYPE_WARNING_NETWORK = "warning-network";

    /**
     * Logs relating to user events (login/logout/restore, etc) *
     */
    public static final String TYPE_USER = "user";

    /**
     * Logs relating to the external files and resources which make up an app *
     */
    public static final String TYPE_RESOURCES = "resources";

    /**
     * Maintenance events (autopurging, cleanups, etc) *
     */
    public static final String TYPE_MAINTENANCE = "maintenance";

    /**
     * Form Entry workflow messages *
     */
    public static final String TYPE_FORM_ENTRY = "form-entry";

    /**
     * Problem reported via report activity at home screen *
     */
    public static final String USER_REPORTED_PROBLEM = "user-report";

    /**
     * Used for internal checking of whether or not certain sections of code ever get called
     */
    public static final String SOFT_ASSERT = "soft-assert";

    //TODO: Currently assumes that it gets back iterated records in RecordID order.
    //when serializing a limited number of records then clearing

    SqlStorage<AndroidLogEntry> storage;

    private int lastEntry = -1;
    private boolean serializing = false;

    private final Object serializationLock = new Object();

    public AndroidLogger(SqlStorage<AndroidLogEntry> storage) {
        this.storage = storage;
    }

    @Override
    public void log(String type, String message, Date logDate) {
        try {
            storage.write(new AndroidLogEntry(type, message, logDate));
        } catch (StorageFullException e) {
            e.printStackTrace();
            panic();
        }
    }

    @Override
    public void clearLogs() {
        if (serializing) {
            storage.removeAll();
        } else {
            storage.removeAll(new EntityFilter<AndroidLogEntry>() {
                @Override
                public boolean matches(AndroidLogEntry e) {
                    if (e.getID() <= lastEntry) {
                        return true;
                    } else {
                        return false;
                    }
                }

            });

        }
    }

    @Override
    public <T> T serializeLogs(IFullLogSerializer<T> serializer) {
        ArrayList<LogEntry> logs = new ArrayList<LogEntry>();
        for (AndroidLogEntry entry : storage) {
            logs.add(entry);
            if (serializing) {
                if (entry.getID() > lastEntry) {
                    lastEntry = entry.getID();
                }
            }
        }
        return serializer.serializeLogs(logs.toArray(new LogEntry[logs.size()]));
    }

    @Override
    public void serializeLogs(StreamLogSerializer serializer) throws IOException {
        for (AndroidLogEntry entry : storage) {
            serializer.serializeLog(entry.getID(), entry);
            if (serializing) {
                if (entry.getID() > lastEntry) {
                    lastEntry = entry.getID();
                }
            }
        }
    }

    @Override
    public void serializeLogs(StreamLogSerializer serializer, int limit) throws IOException {
        int count = 0;
        for (AndroidLogEntry entry : storage) {
            serializer.serializeLog(entry.getID(), entry);
            if (serializing) {
                if (entry.getID() > lastEntry) {
                    lastEntry = entry.getID();
                }
            }
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

    /**
     * Call before serializing to limit what records will be purged during any
     * calls to clear records.
     * <p/>
     * TODO: This is kind of weird.
     */
    public void beginSerializationSession() {
        synchronized (serializationLock) {
            serializing = true;
            lastEntry = -1;
        }
    }

    /**
     * Call after done with a serialization/purging session to reset the internal
     * state of the logger
     * <p/>
     * TODO: This is kind of weird.
     */
    public void endSerializatonSession() {
        synchronized (serializationLock) {
            serializing = false;
            lastEntry = -1;
        }
    }

}
