package org.commcare.activities.connect;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectAppInfo;
import org.commcare.android.database.connect.models.ConnectJob;
import org.commcare.android.database.connect.models.ConnectLearnModuleInfo;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.connect.models.MockJobProvider;
import org.commcare.android.database.global.models.ConnectKeyRecord;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.database.AndroidDbHelper;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.connect.DatabaseConnectOpenHelper;
import org.commcare.modern.database.Table;
import org.commcare.utils.EncryptionUtils;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.Persistable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
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

    public static void storeJobs(Context context, List<ConnectJob> jobs) {
        SqlStorage<ConnectJob> jobStorage = getConnectStorage(context, ConnectJob.class);
        List<ConnectJob> existingList = getJobs(context, -1, jobStorage);

        //Delete jobs that are no longer available
        for (ConnectJob existing : existingList) {
            boolean stillExists = false;
            for (ConnectJob incoming : jobs) {
                if(existing.getJobId() == incoming.getJobId()) {
                    incoming.setID(existing.getID());
                    stillExists = true;
                    break;
                }
            }

            if(!stillExists) {
                jobStorage.remove(existing.getID());
            }
        }

        //Now insert/update jobs
        for (ConnectJob incomingJob : jobs) {
            //Now insert/update the job
            jobStorage.write(incomingJob);

            //Next, store the learn and delivery app info
            incomingJob.getLearnAppInfo().setJobId(incomingJob.getJobId());
            incomingJob.getDeliveryAppInfo().setJobId(incomingJob.getJobId());
            SqlStorage<ConnectAppInfo> appInfoStorage = getConnectStorage(context, ConnectAppInfo.class);
            Vector<ConnectAppInfo> records = appInfoStorage.getRecordsForValues(
                            new String[]{ConnectAppInfo.META_JOB_ID},
                            new Object[]{incomingJob.getJobId()});

            for(ConnectAppInfo existing : records) {
                ConnectAppInfo incomingAppInfo = existing.getIsLearning() ? incomingJob.getLearnAppInfo() : incomingJob.getDeliveryAppInfo();
                incomingAppInfo.setID(existing.getID());
            }

            appInfoStorage.write(incomingJob.getLearnAppInfo());
            appInfoStorage.write(incomingJob.getDeliveryAppInfo());

            //Finally, store the info for the learn modules
            //Delete modules that are no longer available
            SqlStorage<ConnectLearnModuleInfo> moduleStorage = getConnectStorage(context, ConnectLearnModuleInfo.class);
            Vector<ConnectLearnModuleInfo> existingLearnModules = moduleStorage.getRecordsForValues(
                            new String[]{ConnectLearnModuleInfo.META_JOB_ID},
                            new Object[]{incomingJob.getJobId()});
            for (ConnectLearnModuleInfo existing : existingLearnModules) {
                boolean stillExists = false;
                for (ConnectLearnModuleInfo incoming : incomingJob.getLearnAppInfo().getLearnModules()) {
                    if(Objects.equals(existing.getSlug(), incoming.getSlug())) {
                        incoming.setID(incoming.getID());
                        stillExists = true;
                        break;
                    }
                }

                if(!stillExists) {
                    moduleStorage.remove(existing.getID());
                }
            }

            for(ConnectLearnModuleInfo module : incomingJob.getLearnAppInfo().getLearnModules()) {
                module.setJobId(incomingJob.getJobId());
                moduleStorage.write(module);
            }
        }
    }

    private static final boolean UseMockData = false;

    public static List<ConnectJob> getJobs(Context context, int status, SqlStorage<ConnectJob> jobStorage) {
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

        if(jobStorage == null) {
            jobStorage = getConnectStorage(context, ConnectJob.class);
        }

        Vector<ConnectJob> jobs;
        if(status > 0) {
            jobs = jobStorage.getRecordsForValues(
                    new String[]{ConnectJob.META_STATUS},
                    new Object[]{status});
        } else {
            jobs = jobStorage.getRecordsForValues(new String[]{}, new Object[]{});
        }

        SqlStorage<ConnectAppInfo> appInfoStorage = getConnectStorage(context, ConnectAppInfo.class);
        SqlStorage<ConnectLearnModuleInfo> moduleStorage = getConnectStorage(context, ConnectLearnModuleInfo.class);
        for(ConnectJob job : jobs) {
            //Retrieve learn and delivery app info
            Vector<ConnectAppInfo> existingAppInfos = appInfoStorage.getRecordsForValues(
                    new String[]{ConnectAppInfo.META_JOB_ID},
                    new Object[]{job.getJobId()});

            for(ConnectAppInfo info : existingAppInfos) {
                if(info.getIsLearning()) {
                    job.setLearnAppInfo(info);
                }
                else {
                    job.setDeliveryAppInfo(info);
                }
            }

            //Retrieve learn modules
            Vector<ConnectLearnModuleInfo> existingModules = moduleStorage.getRecordsForValues(
                    new String[]{ConnectLearnModuleInfo.META_JOB_ID},
                    new Object[]{job.getJobId()});

            List<ConnectLearnModuleInfo> modules = new ArrayList<>(existingModules);
            modules.sort(Comparator.comparingInt(ConnectLearnModuleInfo::getModuleIndex));

            job.getLearnAppInfo().setLearnModules(modules);
        }

        return new ArrayList<>(jobs);
    }

    public static List<ConnectJob> getAvailableJobs(Context context) {
        return getAvailableJobs(context, null);
    }
    public static List<ConnectJob> getAvailableJobs(Context context, SqlStorage<ConnectJob> jobStorage) {
        List<ConnectJob> jobs = getJobs(context, ConnectJob.STATUS_AVAILABLE, jobStorage);
        jobs.addAll(getJobs(context, ConnectJob.STATUS_AVAILABLE_NEW, jobStorage));
        return jobs;
    }

    public static List<ConnectJob> getTrainingJobs(Context context) {
        return getTrainingJobs(context, null);
    }
    public static List<ConnectJob> getTrainingJobs(Context context, SqlStorage<ConnectJob> jobStorage) {
        return getJobs(context, ConnectJob.STATUS_LEARNING, jobStorage);
    }

    public static List<ConnectJob> getClaimedJobs(Context context) {
        return getClaimedJobs(context, null);
    }
    public static List<ConnectJob> getClaimedJobs(Context context, SqlStorage<ConnectJob> jobStorage) {
        return getJobs(context, ConnectJob.STATUS_DELIVERING, jobStorage);
    }
}
