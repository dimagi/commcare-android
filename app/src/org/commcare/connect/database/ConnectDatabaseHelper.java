package org.commcare.connect.database;

import android.content.Context;

import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.global.models.GlobalErrorRecord;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.network.personalId.SsoToken;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.CommCareApplication;
import org.commcare.models.database.AndroidDbHelper;
import org.commcare.models.database.IDatabase;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.connect.DatabaseConnectOpenHelper;
import org.commcare.modern.database.Table;
import org.commcare.utils.GlobalErrorUtil;
import org.commcare.utils.GlobalErrors;
import org.javarosa.core.services.Logger;
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

    public static void handleReceivedDbPassphrase(String passphrase) {
        ConnectDatabaseUtils.storeConnectDbPassphrase(passphrase);
    }

    public static boolean dbExists() {
        return DatabaseConnectOpenHelper.dbExists();
    }

    public static boolean isDbBroken() {
        return dbBroken;
    }

    public static <T extends Persistable> SqlStorage<T> getConnectStorage(Class<T> c) {
        Context context = CommCareApplication.instance().getApplicationContext();
        return new SqlStorage<>(c.getAnnotation(Table.class).value(), c, new AndroidDbHelper(context) {
            @Override
            public IDatabase getHandle() {
                synchronized (connectDbHandleLock) {
                    if (connectDatabase == null || !connectDatabase.isOpen()) {
                        try {
                            connectDatabase = CommCareApplication.instance().getConnectDbOpenHelper();
                        } catch (Exception e) {
                            //Flag the DB as broken if we hit an error opening it (usually means corrupted or bad encryption)
                            dbBroken = true;
                            Logger.exception("Error opening Connect DB", e);
                            GlobalErrorUtil.triggerGlobalError(GlobalErrors.PERSONALID_GENERIC_ERROR);
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

    public static void handleGlobalError(GlobalErrors error) {
        GlobalErrorUtil.addError(new GlobalErrorRecord(new Date(), error.ordinal()));
        PersonalIdManager.getInstance().forgetUser(AnalyticsParamValue.PERSONAL_ID_FORGOT_USER_DB_ERROR);
    }

    public static void storeHqToken(String appId, String userId, SsoToken token) {
        ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(appId, userId);
        record.updateHqToken(token);
        getConnectStorage(ConnectLinkedAppRecord.class).write(record);
    }
}
