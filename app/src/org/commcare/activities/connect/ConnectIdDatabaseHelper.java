package org.commcare.activities.connect;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.global.models.ConnectKeyRecord;
import org.commcare.models.database.AndroidDbHelper;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.connect.DatabaseConnectOpenHelper;
import org.commcare.modern.database.Table;
import org.commcare.utils.EncryptionUtils;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.Persistable;

import java.util.Date;
import java.util.Vector;

/**
 * Helper class for accessing the Connect DB
 *
 * @author dviggiano
 */
public class ConnectIdDatabaseHelper {
    private static final Object connectDbHandleLock = new Object();
    private static SQLiteDatabase connectDatabase;

    private static byte[] getConnectDbPassphrase(Context context) {
        try {
            for (ConnectKeyRecord r : CommCareApplication.instance().getGlobalStorage(ConnectKeyRecord.class)) {
                return EncryptionUtils.decryptFromBase64String(context, r.getEncryptedPassphrase());
            }

            //If we get here, the passphrase hasn't been created yet
            byte[] passphrase = EncryptionUtils.generatePassphrase();

            String encoded = EncryptionUtils.encryptToBase64String(context, passphrase);
            ConnectKeyRecord record = new ConnectKeyRecord(encoded, true);
            CommCareApplication.instance().getGlobalStorage(ConnectKeyRecord.class).write(record);

            return passphrase;
        } catch (Exception e) {
            Logger.exception("Getting DB passphrase", e);
            throw new RuntimeException(e);
        }
    }

    public static void init(Context context) {
        synchronized (connectDbHandleLock) {
            byte[] passphrase = getConnectDbPassphrase(context);
            SQLiteDatabase database = new DatabaseConnectOpenHelper(context).getWritableDatabase(passphrase);
            database.close();
        }
    }

    private static <T extends Persistable> SqlStorage<T> getConnectStorage(Context context, Class<T> c) {
        return new SqlStorage<>(c.getAnnotation(Table.class).value(), c, new AndroidDbHelper(context) {
            @Override
            public SQLiteDatabase getHandle() {
                synchronized (connectDbHandleLock) {
                    if (connectDatabase == null || !connectDatabase.isOpen()) {
                        byte[] passphrase = getConnectDbPassphrase(context);

                        connectDatabase = new DatabaseConnectOpenHelper(this.c).getWritableDatabase(passphrase);
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
        getConnectStorage(context, ConnectUserRecord.class).removeAll();
    }

    public static ConnectLinkedAppRecord getAppData(Context context, String appId, String username) {
        Vector<ConnectLinkedAppRecord> records = getConnectStorage(context, ConnectLinkedAppRecord.class)
                .getRecordsForValues(
                        new String[]{ConnectLinkedAppRecord.META_APP_ID, ConnectLinkedAppRecord.META_USER_ID},
                        new Object[]{appId, username});
        return records.isEmpty() ? null : records.firstElement();
    }

    public static void deleteAppData(Context context, ConnectLinkedAppRecord record) {
        SqlStorage<ConnectLinkedAppRecord> storage = getConnectStorage(context, ConnectLinkedAppRecord.class);
        storage.remove(record);
    }

    public static void storeApp(Context context, String appId, String userId, String passwordOrPin) {
        ConnectLinkedAppRecord record = getAppData(context, appId, userId);
        if (record == null) {
            record = new ConnectLinkedAppRecord(appId, userId, passwordOrPin);
        } else if (!record.getPassword().equals(passwordOrPin)) {
            record.setPassword(passwordOrPin);
        }

        storeApp(context, record);
    }

    public static void storeApp(Context context, ConnectLinkedAppRecord record) {
        getConnectStorage(context, ConnectLinkedAppRecord.class).write(record);
    }

    public static void storeHqToken(Context context, String appId, String userId, String token, Date expiration) {
        ConnectLinkedAppRecord record = getAppData(context, appId, userId);
        if (record == null) {
            record = new ConnectLinkedAppRecord(appId, userId, "");
        }

        record.updateHqToken(token, expiration);

        getConnectStorage(context, ConnectLinkedAppRecord.class).write(record);
    }

    public static void setRegistrationPhase(Context context, ConnectIdTask phase) {
        ConnectUserRecord user = getUser(context);
        if (user != null) {
            user.setRegistrationPhase(phase);
            storeUser(context, user);
        }
    }
}
