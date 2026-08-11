package org.commcare.connect.database;

import android.content.Context;

import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.global.models.ConnectKeyRecord;
import org.commcare.android.database.global.models.GlobalErrorRecord;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.network.SsoToken;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.CommCareApplication;
import org.commcare.models.database.AndroidDbHelper;
import org.commcare.models.database.IDatabase;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.connect.DatabaseConnectOpenHelper;
import org.commcare.modern.database.Table;
import org.commcare.util.LogTypes;
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
    //Written under connectDbHandleLock, but isDbBroken() reads it from other threads
    private static volatile boolean dbBroken = false;

    public static void handleReceivedDbPassphrase(Context context, String passphrase) {
        ConnectDatabaseUtils.storeConnectDbPassphrase(context, passphrase);
    }

    public static boolean dbExists() {
        return DatabaseConnectOpenHelper.dbExists();
    }

    public static boolean isDbBroken() {
        return dbBroken;
    }

    public static <T extends Persistable> SqlStorage<T> getConnectStorage(Context context, Class<T> c) {
        return new SqlStorage<>(c.getAnnotation(Table.class).value(), c, new AndroidDbHelper(context) {
            @Override
            public IDatabase getHandle() {
                synchronized (connectDbHandleLock) {
                    if (connectDatabase == null || !connectDatabase.isOpen()) {
                        try {
                            connectDatabase = CommCareApplication.instance().getConnectDbOpenHelper(context);
                        } catch (ConnectDatabaseUnavailableException e) {
                            //There's no account to open a DB for, which is the expected state once
                            //the user signs out. Don't flag the DB or raise the global error: that
                            //would wipe the account and restart the process over work that simply
                            //raced with sign-out
                            Logger.log(LogTypes.TYPE_MAINTENANCE,
                                    "Skipping Connect DB access, there is no passphrase to open it");
                            throw e;
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

    /**
     * Deletes the Connect DB along with the passphrase used to open it.
     * <p>
     * Held under the same lock as {@link #getConnectStorage} so that a storage operation racing
     * with sign-out either completes against the live DB or sees it already gone. Without the
     * lock a reader can open a handle partway through, and re-flag the DB as broken after this
     * has cleared the flag.
     */
    static void clearConnectData() {
        synchronized (connectDbHandleLock) {
            teardown();
            DatabaseConnectOpenHelper.deleteDb();
            CommCareApplication.instance().getGlobalStorage(ConnectKeyRecord.class).removeAll();
            dbBroken = false;
        }
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
