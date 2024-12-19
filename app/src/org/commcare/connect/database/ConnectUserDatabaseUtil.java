package org.commcare.connect.database;

import android.content.Context;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.global.models.ConnectKeyRecord;
import org.commcare.models.database.connect.DatabaseConnectOpenHelper;
import org.javarosa.core.services.Logger;

public class ConnectUserDatabaseUtil {
    public static ConnectUserRecord getUser(Context context) {
        ConnectUserRecord user = null;
        if (ConnectDatabaseHelper.dbExists(context)) {
            try {
                for (ConnectUserRecord r : ConnectDatabaseHelper.getConnectStorage(context, ConnectUserRecord.class)) {
                    user = r;
                    break;
                }
            } catch (Exception e) {
                Logger.exception("Corrupt Connect DB trying to get user", e);
                ConnectDatabaseHelper.dbBroken = true;
            }
        }

        return user;
    }

    public static void storeUser(Context context, ConnectUserRecord user) {
        ConnectDatabaseHelper.getConnectStorage(context, ConnectUserRecord.class).write(user);
    }

    public static void forgetUser(Context context) {
        DatabaseConnectOpenHelper.deleteDb(context);
        CommCareApplication.instance().getGlobalStorage(ConnectKeyRecord.class).removeAll();
        ConnectDatabaseHelper.dbBroken = false;
    }
}
