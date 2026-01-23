package org.commcare.connect.database;

import android.content.Context;

import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.global.models.GlobalErrorRecord;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.network.SsoToken;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.models.database.AndroidDbHelper;
import org.commcare.models.database.IDatabase;
import org.commcare.models.database.EncryptedDatabaseAdapter;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.connect.DatabaseConnectOpenHelper;
import org.commcare.models.database.user.UserSandboxUtils;
import org.commcare.modern.database.Table;
import org.commcare.utils.GlobalErrorUtil;
import org.commcare.utils.GlobalErrors;
import org.javarosa.core.services.storage.Persistable;

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

    public static void handleReceivedDbPassphrase(Context context, String passphrase) {
        ConnectDatabaseUtils.storeConnectDbPassphrase(context, passphrase);
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
                            byte[] passphrase = ConnectDatabaseUtils.getConnectDbPassphrase(context);
                            if(passphrase == null || passphrase.length == 0) {
                                throw new IllegalStateException("Attempting to access Connect DB without a passphrase");
                            }

                            connectDatabase = new EncryptedDatabaseAdapter(new DatabaseConnectOpenHelper(
                                    this.c, UserSandboxUtils.getSqlCipherEncodedKey(passphrase)));
                        } catch (Exception e) {
                            //Flag the DB as broken if we hit an error opening it (usually means corrupted or bad encryption)
                            dbBroken = true;
                            crashDb(GlobalErrors.PERSONALID_GENERIC_ERROR, e);
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
        crashDb(error, null);
    }

    public static void crashDb(GlobalErrors error, Exception ex) {
        GlobalErrorUtil.addError(new GlobalErrorRecord(new Date(), error.ordinal()));
        PersonalIdManager.getInstance().forgetUser(AnalyticsParamValue.PERSONAL_ID_FORGOT_USER_DB_ERROR);
        throw new RuntimeException("Connect database crash: " + error.name(), ex);
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
