package org.commcare.connect.database;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.network.SsoToken;
import org.commcare.dalvik.R;
import org.commcare.models.database.AndroidDbHelper;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.connect.DatabaseConnectOpenHelper;
import org.commcare.models.database.user.UserSandboxUtils;
import org.commcare.modern.database.Table;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.Persistable;

/**
 * Helper class for accessing the Connect DB
 *
 * @author dviggiano
 */
public class ConnectDatabaseHelper {
    private static final Object connectDbHandleLock = new Object();
    public static SQLiteDatabase connectDatabase;
    static boolean dbBroken = false;

    public static void handleReceivedDbPassphrase(Context context, String remotePassphrase) {
        ConnectDatabaseUtils.storeConnectDbPassphrase(context, remotePassphrase, false);
        try {
            if ((connectDatabase == null || !connectDatabase.isOpen())) {
                DatabaseConnectOpenHelper.rekeyDB(connectDatabase, remotePassphrase);
                ConnectDatabaseUtils.storeConnectDbPassphrase(context, remotePassphrase, true);
            }
        } catch (Exception e) {
            Logger.exception("Handling received DB passphrase", e);
            handleCorruptDb(context);
        }
    }

    public static boolean dbExists(Context context) {
        return DatabaseConnectOpenHelper.dbExists(context);
    }

    public static boolean isDbBroken() {
        return dbBroken;
    }

    static <T extends Persistable> SqlStorage<T> getConnectStorage(Context context, Class<T> c) {
        return new SqlStorage<>(c.getAnnotation(Table.class).value(), c, new AndroidDbHelper(context) {
            @Override
            public SQLiteDatabase getHandle() {
                synchronized (connectDbHandleLock) {
                    if (connectDatabase == null || !connectDatabase.isOpen()) {
                        try {
                            byte[] passphrase = ConnectDatabaseUtils.getConnectDbPassphrase(context, true);

                            DatabaseConnectOpenHelper helper = new DatabaseConnectOpenHelper(this.c);

                            String remotePassphrase = ConnectDatabaseUtils.getConnectDbEncodedPassphrase(context, false);
                            String localPassphrase = ConnectDatabaseUtils.getConnectDbEncodedPassphrase(context, true);
                            if (remotePassphrase != null && remotePassphrase.equals(localPassphrase)) {
                                //Using the UserSandboxUtils helper method to align with other code
                                connectDatabase = helper.getWritableDatabase(UserSandboxUtils.getSqlCipherEncodedKey(passphrase));
                            } else {
                                //LEGACY: Used to open the DB using the byte[], not String overload
                                connectDatabase = helper.getWritableDatabase(passphrase);
                            }
                        } catch (Exception e) {
                            //Flag the DB as broken if we hit an error opening it (usually means corrupted or bad encryption)
                            dbBroken = true;
                            handleCorruptDb(context);
                            Logger.exception("Corrupt Connect DB", e);
                        }
                    }
                    return connectDatabase;
                }
            }
        });
    }

    public static void teardown() {
        synchronized (connectDbHandleLock) {
            if (connectDatabase != null && connectDatabase.isOpen()) {
                connectDatabase.close();
                connectDatabase = null;
            }
        }
    }

    public static void handleCorruptDb(Context context) {
        ConnectUserDatabaseUtil.forgetUser(context);
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, context.getString(R.string.connect_db_corrupt), Toast.LENGTH_LONG).show()
        );
    }

    public static void storeHqToken(Context context, String appId, String userId, SsoToken token) {
        ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getAppData(context, appId, userId);
        record.updateHqToken(token);
        getConnectStorage(context, ConnectLinkedAppRecord.class).write(record);
    }

    public static void setRegistrationPhase(Context context, int phase) {
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(context);
        if (user != null) {
            user.setRegistrationPhase(phase);
            ConnectUserDatabaseUtil.storeUser(context, user);
        }
    }


}
