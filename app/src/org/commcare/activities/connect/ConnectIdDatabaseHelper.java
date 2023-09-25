package org.commcare.activities.connect;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
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

    public static void storeJobs(Context context, List<ConnectJobRecord> jobs) {
        SqlStorage<ConnectJobRecord> jobStorage = getConnectStorage(context, ConnectJobRecord.class);
        List<ConnectJobRecord> existingList = getJobs(context, -1, jobStorage);

        //Delete jobs that are no longer available
        Vector<Integer> idsToDelete = new Vector<>();
        for (ConnectJobRecord existing : existingList) {
            boolean stillExists = false;
            for (ConnectJobRecord incoming : jobs) {
                if(existing.getJobId() == incoming.getJobId()) {
                    incoming.setID(existing.getID());
                    stillExists = true;
                    break;
                }
            }

            if(!stillExists) {
                idsToDelete.add(existing.getID());
            }
        }

        jobStorage.removeAll(idsToDelete);

        //Now insert/update jobs
        for (ConnectJobRecord incomingJob : jobs) {
            //Now insert/update the job
            jobStorage.write(incomingJob);

            //Next, store the learn and delivery app info
            incomingJob.getLearnAppInfo().setJobId(incomingJob.getJobId());
            incomingJob.getDeliveryAppInfo().setJobId(incomingJob.getJobId());
            SqlStorage<ConnectAppRecord> appInfoStorage = getConnectStorage(context, ConnectAppRecord.class);
            Vector<ConnectAppRecord> records = appInfoStorage.getRecordsForValues(
                            new String[]{ConnectAppRecord.META_JOB_ID},
                            new Object[]{incomingJob.getJobId()});

            for(ConnectAppRecord existing : records) {
                ConnectAppRecord incomingAppInfo = existing.getIsLearning() ? incomingJob.getLearnAppInfo() : incomingJob.getDeliveryAppInfo();
                incomingAppInfo.setID(existing.getID());
            }

            appInfoStorage.write(incomingJob.getLearnAppInfo());
            appInfoStorage.write(incomingJob.getDeliveryAppInfo());

            //Finally, store the info for the learn modules
            //Delete modules that are no longer available
            SqlStorage<ConnectLearnModuleSummaryRecord> moduleStorage = getConnectStorage(context, ConnectLearnModuleSummaryRecord.class);
            Vector<ConnectLearnModuleSummaryRecord> existingLearnModules = moduleStorage.getRecordsForValues(
                            new String[]{ConnectLearnModuleSummaryRecord.META_JOB_ID},
                            new Object[]{incomingJob.getJobId()});
            for (ConnectLearnModuleSummaryRecord existing : existingLearnModules) {
                boolean stillExists = false;
                for (ConnectLearnModuleSummaryRecord incoming : incomingJob.getLearnAppInfo().getLearnModules()) {
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

            for(ConnectLearnModuleSummaryRecord module : incomingJob.getLearnAppInfo().getLearnModules()) {
                module.setJobId(incomingJob.getJobId());
                moduleStorage.write(module);
            }
        }
    }

    private static final boolean UseMockData = false;

    public static List<ConnectJobRecord> getJobs(Context context, int status, SqlStorage<ConnectJobRecord> jobStorage) {
        if(UseMockData) {
            return switch(status) {
                case ConnectJobRecord.STATUS_AVAILABLE ->
                        MockJobProvider.getAvailableJobs();
                case ConnectJobRecord.STATUS_LEARNING ->
                    MockJobProvider.getTrainingJobs();
                case ConnectJobRecord.STATUS_DELIVERING ->
                    MockJobProvider.getClaimedJobs();
                default -> new ArrayList<>();
            };
        }

        if(jobStorage == null) {
            jobStorage = getConnectStorage(context, ConnectJobRecord.class);
        }

        Vector<ConnectJobRecord> jobs;
        if(status > 0) {
            jobs = jobStorage.getRecordsForValues(
                    new String[]{ConnectJobRecord.META_STATUS},
                    new Object[]{status});
        } else {
            jobs = jobStorage.getRecordsForValues(new String[]{}, new Object[]{});
        }

        SqlStorage<ConnectAppRecord> appInfoStorage = getConnectStorage(context, ConnectAppRecord.class);
        SqlStorage<ConnectLearnModuleSummaryRecord> moduleStorage = getConnectStorage(context, ConnectLearnModuleSummaryRecord.class);
        for(ConnectJobRecord job : jobs) {
            //Retrieve learn and delivery app info
            Vector<ConnectAppRecord> existingAppInfos = appInfoStorage.getRecordsForValues(
                    new String[]{ConnectAppRecord.META_JOB_ID},
                    new Object[]{job.getJobId()});

            for(ConnectAppRecord info : existingAppInfos) {
                if(info.getIsLearning()) {
                    job.setLearnAppInfo(info);
                }
                else {
                    job.setDeliveryAppInfo(info);
                }
            }

            //Retrieve learn modules
            Vector<ConnectLearnModuleSummaryRecord> existingModules = moduleStorage.getRecordsForValues(
                    new String[]{ConnectLearnModuleSummaryRecord.META_JOB_ID},
                    new Object[]{job.getJobId()});

            List<ConnectLearnModuleSummaryRecord> modules = new ArrayList<>(existingModules);
            modules.sort(Comparator.comparingInt(ConnectLearnModuleSummaryRecord::getModuleIndex));

            job.getLearnAppInfo().setLearnModules(modules);
        }

        return new ArrayList<>(jobs);
    }

    public static List<ConnectJobRecord> getAvailableJobs(Context context) {
        return getAvailableJobs(context, null);
    }
    public static List<ConnectJobRecord> getAvailableJobs(Context context, SqlStorage<ConnectJobRecord> jobStorage) {
        List<ConnectJobRecord> jobs = getJobs(context, ConnectJobRecord.STATUS_AVAILABLE, jobStorage);
        jobs.addAll(getJobs(context, ConnectJobRecord.STATUS_AVAILABLE_NEW, jobStorage));
        return jobs;
    }

    public static List<ConnectJobRecord> getTrainingJobs(Context context) {
        return getTrainingJobs(context, null);
    }
    public static List<ConnectJobRecord> getTrainingJobs(Context context, SqlStorage<ConnectJobRecord> jobStorage) {
        return getJobs(context, ConnectJobRecord.STATUS_LEARNING, jobStorage);
    }

    public static List<ConnectJobRecord> getClaimedJobs(Context context) {
        return getClaimedJobs(context, null);
    }
    public static List<ConnectJobRecord> getClaimedJobs(Context context, SqlStorage<ConnectJobRecord> jobStorage) {
        return getJobs(context, ConnectJobRecord.STATUS_DELIVERING, jobStorage);
    }
}
