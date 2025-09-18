package org.commcare.connect.database;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;


import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.global.models.ConnectKeyRecord;
import org.commcare.android.database.global.models.GlobalErrorRecord;
import org.commcare.connect.network.SsoToken;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.models.database.AndroidDbHelper;
import org.commcare.models.database.IDatabase;
import org.commcare.models.database.EncryptedDatabaseAdapter;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.connect.DatabaseConnectOpenHelper;
import org.commcare.models.database.user.UserSandboxUtils;
import org.commcare.modern.database.Table;
import org.commcare.utils.GlobalErrorUtil;
import org.commcare.utils.GlobalErrors;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.Persistable;

import android.util.Base64;
import java.util.Date;


/**
 * Helper class for accessing the Connect DB
 *
 * @author dviggiano
 */
public class ConnectDatabaseHelper {
    private static final Object connectDbHandleLock = new Object();
    public static IDatabase connectDatabase;
    static boolean dbBroken = false;

    public static void handleReceivedDbPassphrase(Context context, String remotePassphrase) {
        ConnectDatabaseUtils.storeConnectDbPassphrase(context, remotePassphrase, false);
        try {
            //Rekey the DB if the remote passphrase is different than local
            String localPassphrase = ConnectDatabaseUtils.getConnectDbEncodedPassphrase(context, true);
            if (connectDatabase != null && connectDatabase.isOpen() && !remotePassphrase.equals(localPassphrase)) {
                DatabaseConnectOpenHelper.rekeyDB(connectDatabase, remotePassphrase);
                FirebaseAnalyticsUtil.reportRekeyedDatabase();
            }

            //Store the received passphrase as what's in use locally
            ConnectDatabaseUtils.storeConnectDbPassphrase(context, remotePassphrase, true);
        } catch (Exception e) {
            crashDb(GlobalErrors.PERSONALID_DB_UPGRADE_ERROR);
        }
    }

    public static boolean dbExists() {
        return DatabaseConnectOpenHelper.dbExists();
    }

    public static boolean isDbBroken() {
        return dbBroken;
    }

    static <T extends Persistable> SqlStorage<T> getConnectStorage(Context context, Class<T> c) {
        return new SqlStorage<>(c.getAnnotation(Table.class).value(), c, new AndroidDbHelper(context) {
            @Override
            public IDatabase getHandle() {
                synchronized (connectDbHandleLock) {
                    if (connectDatabase == null || !connectDatabase.isOpen()) {
                        try {
                            byte[] passphrase = ConnectDatabaseUtils.getConnectDbPassphrase(context, true);
                            if(passphrase == null) {
                                throw new IllegalStateException("Attempting to access Connect DB without a passphrase");
                            }

                            String remotePassphrase = ConnectDatabaseUtils.getConnectDbEncodedPassphrase(context, false);
                            String localPassphrase = ConnectDatabaseUtils.getConnectDbEncodedPassphrase(context, true);
                            DatabaseConnectOpenHelper dbConnectOpenHelper;
                            if (remotePassphrase != null && remotePassphrase.equals(localPassphrase)) {
                                //Using the UserSandboxUtils helper method to align with other code
                                dbConnectOpenHelper = new DatabaseConnectOpenHelper(this.c,
                                        UserSandboxUtils.getSqlCipherEncodedKey(passphrase));
                            } else {
                                //LEGACY: Used to open the DB using the byte[], not String overload
                                Logger.exception("Legacy DB Usage", new Exception("Accessing Connect DB via legacy code"));
                                dbConnectOpenHelper = new DatabaseConnectOpenHelper(this.c,
                                        Base64.encodeToString(passphrase, Base64.NO_WRAP));
                            }
                            connectDatabase = new EncryptedDatabaseAdapter(dbConnectOpenHelper);
                        } catch (Exception e) {
                            //Flag the DB as broken if we hit an error opening it (usually means corrupted or bad encryption)
                            dbBroken = true;
                            crashDb(GlobalErrors.PERSONALID_GENERIC_ERROR);
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

    public static void crashDb(GlobalErrors error) {
        GlobalErrorUtil.addError(new GlobalErrorRecord(new Date(), error.ordinal()));
        throw new RuntimeException("Connect database crash: " + error.name());
    }

    public static void storeHqToken(Context context, String appId, String userId, SsoToken token) {
        ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context, appId, userId);
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
