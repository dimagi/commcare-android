package org.commcare.connect.database;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.global.models.ConnectKeyRecord;
import org.commcare.models.database.connect.DatabaseConnectOpenHelper;

public class ConnectUserDatabaseUtil {

    public static ConnectUserRecord getUser() {
        if (!ConnectDatabaseHelper.dbExists()) {
            return null;
        }
        Iterable<ConnectUserRecord> records = ConnectDatabaseHelper.getConnectStorage(
                ConnectUserRecord.class);
        if (records.iterator().hasNext()) {
            return records.iterator().next();
        }
        return null;
    }

    public static void storeUser(ConnectUserRecord user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        ConnectDatabaseHelper.getConnectStorage(ConnectUserRecord.class).write(user);
    }

    public static void forgetUser() {
        DatabaseConnectOpenHelper.deleteDb();
        CommCareApplication.instance().getGlobalStorage(ConnectKeyRecord.class).removeAll();
        ConnectDatabaseHelper.dbBroken = false;
        ConnectDatabaseHelper.teardown();
    }

    public static boolean hasConnectAccess() {
        ConnectUserRecord user = getUser();
        return user != null && user.hasConnectAccess();
    }

    public static void turnOnConnectAccess() {
        ConnectUserRecord user = getUser();
        if (user != null && !user.hasConnectAccess()) {
            user.setHasConnectAccess(true);
            storeUser(user);
        }
    }
}
