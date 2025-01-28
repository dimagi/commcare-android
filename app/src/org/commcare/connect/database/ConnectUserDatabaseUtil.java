package org.commcare.connect.database;

import android.content.Context;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.global.models.ConnectKeyRecord;
import org.commcare.models.database.connect.DatabaseConnectOpenHelper;
import org.javarosa.core.services.Logger;

public class ConnectUserDatabaseUtil {
    private static final Object LOCK = new Object();
    public static ConnectUserRecord getUser(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        synchronized (LOCK) {
            if (!ConnectDatabaseHelper.dbExists(context)) {
                return null;
            }
            try {
                Iterable<ConnectUserRecord> records = ConnectDatabaseHelper.getConnectStorage(
                        context, ConnectUserRecord.class);
                if (records.iterator().hasNext()) {
                    return records.iterator().next();
                }
                return null;
            } catch (Exception e) {
                Logger.exception("Corrupt Connect DB trying to get user", e);
                ConnectDatabaseHelper.dbBroken = true;
                throw new RuntimeException("Failed to access Connect database", e);
            }
        }
    }

    public static void storeUser(Context context, ConnectUserRecord user) {
        if (context == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        synchronized (LOCK) {
            try {
                ConnectDatabaseHelper.getConnectStorage(context, ConnectUserRecord.class).write(user);
            } catch (Exception e) {
                Logger.exception("Failed to store user", e);
                throw new RuntimeException("Failed to store user in Connect database", e);
            }
        }
    }

    public static void forgetUser(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        synchronized (LOCK) {
            try {
                DatabaseConnectOpenHelper.deleteDb(context);
                CommCareApplication.instance().getGlobalStorage(ConnectKeyRecord.class).removeAll();
                ConnectDatabaseHelper.dbBroken = false;
            } catch (IllegalStateException e) {
                Logger.exception("Database access error while forgetting user", e);
                throw new RuntimeException("Failed to access database while cleaning up", e);
            } catch (SecurityException e) {
                Logger.exception("Permission denied while deleting database", e);
                throw new RuntimeException("Failed to delete database due to permissions", e);
            } catch (Exception e) {
                Logger.exception("Failed to forget user", e);
                throw new RuntimeException("Failed to clean up Connect database", e);
            }
        }
    }
}
