package org.commcare.activities.connect;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectJob;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.connect.models.MockJobProvider;
import org.commcare.android.database.global.models.ConnectKeyRecord;
import org.commcare.models.database.AndroidDbHelper;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.connect.DatabaseConnectOpenHelper;
import org.commcare.modern.database.Table;
import org.commcare.utils.EncryptionUtils;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.Persistable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
            ConnectKeyRecord record = new ConnectKeyRecord(encoded);
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

    public static void storeAvailableJobs(Context context, List<ConnectJob> jobs) {
        List<ConnectJob> existingList = getAvailableJobs(context);

        //Delete jobs that are no longer available
        for (ConnectJob existing : existingList) {
            boolean stillExists = false;
            for (ConnectJob newJob : jobs) {
                if(existing.getID() == newJob.getID()) {
                    stillExists = true;
                    break;
                }
            }

            if(!stillExists) {
                getConnectStorage(context, ConnectJob.class).remove(existing.getID());
            }
        }

        //Now insert/update jobs
        for (ConnectJob newJob : jobs) {
            for (ConnectJob existingJob : existingList) {
                if(newJob.getID() == existingJob.getID()) {
                    //To update, set ID for the new job from the existing
                    newJob.setID(existingJob.getID());
                    break;
                }
            }

            //Now insert/update the job
            getConnectStorage(context, ConnectJob.class).write(newJob);
        }
    }

    private static final boolean UseMockData = true;

    public static List<ConnectJob> getJobs(Context context, int status) {
        if(UseMockData) {
            return switch(status) {
                case ConnectJob.STATUS_AVAILABLE ->
                        MockJobProvider.getAvailableJobs();
                case ConnectJob.STATUS_LEARNING ->
                    MockJobProvider.getTrainingJobs();
                case ConnectJob.STATUS_DELIVERING ->
                    MockJobProvider.getClaimedJobs();
                default -> new ArrayList<>();
            };
        }

        Vector<ConnectJob> records = getConnectStorage(context, ConnectJob.class)
                .getRecordsForValues(
                        new String[]{ConnectJob.META_STATUS},
                        new Object[]{status});

        return new ArrayList<>(records);
    }

    public static List<ConnectJob> getAvailableJobs(Context context) {
        List<ConnectJob> jobs = getJobs(context, ConnectJob.STATUS_AVAILABLE);
        jobs.addAll(getJobs(context, ConnectJob.STATUS_AVAILABLE_NEW));
        return jobs;
    }

    public static List<ConnectJob> getTrainingJobs(Context context) {
        return getJobs(context, ConnectJob.STATUS_LEARNING);
    }

    public static List<ConnectJob> getClaimdeJobs(Context context) {
        return getJobs(context, ConnectJob.STATUS_DELIVERING);
    }
}
