package org.commcare.activities.connect;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.models.database.AndroidDbHelper;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.connect.DatabaseConnectOpenHelper;
import org.commcare.modern.database.Table;
import org.commcare.utils.EncryptionUtils;
import org.javarosa.core.services.storage.Persistable;

public class ConnectIDDatabaseHelper {
    private static final Object connectDbHandleLock = new Object();
    private static SQLiteDatabase connectDatabase;

    public static void init(Context context) {
        synchronized (connectDbHandleLock) {
            SQLiteDatabase database = new DatabaseConnectOpenHelper(context).getWritableDatabase(EncryptionUtils.getConnectDBPassphrase(context));
            database.close();
        }
    }

    private static <T extends Persistable> SqlStorage<T> getConnectStorage(Context context, Class<T> c) {
        return new SqlStorage<>(c.getAnnotation(Table.class).value(), c, new AndroidDbHelper(context.getApplicationContext()) {
            @Override
            public SQLiteDatabase getHandle() {
                synchronized (connectDbHandleLock) {
                    if (connectDatabase == null || !connectDatabase.isOpen()) {
                        connectDatabase = new DatabaseConnectOpenHelper(this.c).getWritableDatabase(EncryptionUtils.getConnectDBPassphrase(context));
                    }
                    return connectDatabase;
                }
            }
        });
    }

    public static ConnectUserRecord getUser(Context context) {
        ConnectUserRecord user = null;
        for (ConnectUserRecord r : getConnectStorage(context, ConnectUserRecord.class)) {
            user = r;
            break;
        }

        return user;
    }

    public static void storeUser(Context context, ConnectUserRecord user) {
        getConnectStorage(context, ConnectUserRecord.class).write(user);
    }

    public static void forgetUser(Context context) {
        getConnectStorage(context, ConnectUserRecord.class).removeAll();//(getUser().getID());
    }

    public static ConnectLinkedAppRecord getAppData(Context context, String appId) {
        ConnectLinkedAppRecord record = null;
        for (ConnectLinkedAppRecord r : getConnectStorage(context, ConnectLinkedAppRecord.class)) {
            if(r.getAppID().equals(appId)) {
                record = r;
                break;
            }
        }

        return record;
    }

    public static void storeApp(Context context, ConnectLinkedAppRecord record) {
        getConnectStorage(context, ConnectLinkedAppRecord.class).write(record);
    }

    public static void setRegistrationPhase(Context context, int phase) {
        ConnectUserRecord user = getUser(context);
        if(user != null) {
            user.setRegistrationPhase(phase);
            storeUser(context, user);
        }
    }
}
