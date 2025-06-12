package org.commcare.connect.database;

import android.content.Context;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.global.models.ConnectKeyRecord;
import org.commcare.models.database.connect.DatabaseConnectOpenHelper;

public class ConnectUserDatabaseUtil {

    public static ConnectUserRecord getUser(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        if (!ConnectDatabaseHelper.dbExists()) {
            return null;
        }
        Iterable<ConnectUserRecord> records = ConnectDatabaseHelper.getConnectStorage(
                context, ConnectUserRecord.class);
        if (records.iterator().hasNext()) {
            return records.iterator().next();
        }
        return null;
    }

    public static void storeUser(Context context, ConnectUserRecord user) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        ConnectDatabaseHelper.getConnectStorage(context, ConnectUserRecord.class).write(user);
    }

    public static void forgetUser(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        DatabaseConnectOpenHelper.deleteDb();
        CommCareApplication.instance().getGlobalStorage(ConnectKeyRecord.class).removeAll();
        ConnectDatabaseHelper.dbBroken = false;
        ConnectDatabaseHelper.teardown();
    }
}
