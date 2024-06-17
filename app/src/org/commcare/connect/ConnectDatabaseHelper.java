package org.commcare.connect;

import android.content.Context;
import android.os.Build;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobLearningRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
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
public class ConnectDatabaseHelper {
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

    public static boolean dbExists(Context context) {
        return DatabaseConnectOpenHelper.dbExists(context);
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

    public static void teardown() {
        synchronized (connectDbHandleLock) {
            if (connectDatabase != null && connectDatabase.isOpen()) {
                connectDatabase.close();
                connectDatabase = null;
            }
        }
    }

    public static ConnectUserRecord getUser(Context context) {
        ConnectUserRecord user = null;
        if(dbExists(context)) {
            for (ConnectUserRecord r : getConnectStorage(context, ConnectUserRecord.class)) {
                user = r;
                break;
            }
        }

        return user;
    }

    public static void storeUser(Context context, ConnectUserRecord user) {
        getConnectStorage(context, ConnectUserRecord.class).write(user);
    }

    public static void forgetUser(Context context) {
        getConnectStorage(context, ConnectUserRecord.class).removeAll();
        getConnectStorage(context, ConnectLinkedAppRecord.class).removeAll();
        getConnectStorage(context, ConnectJobRecord.class).removeAll();
        getConnectStorage(context, ConnectAppRecord.class).removeAll();
        getConnectStorage(context, ConnectLearnModuleSummaryRecord.class).removeAll();
        getConnectStorage(context, ConnectJobLearningRecord.class).removeAll();
        getConnectStorage(context, ConnectJobAssessmentRecord.class).removeAll();
        getConnectStorage(context, ConnectJobDeliveryRecord.class).removeAll();
        getConnectStorage(context, ConnectJobPaymentRecord.class).removeAll();
        getConnectStorage(context, ConnectPaymentUnitRecord.class).removeAll();
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

    public static ConnectLinkedAppRecord storeApp(Context context, String appId, String userId, boolean connectIdLinked, String passwordOrPin, boolean workerLinked) {
        ConnectLinkedAppRecord record = getAppData(context, appId, userId);
        if (record == null) {
            record = new ConnectLinkedAppRecord(appId, userId, connectIdLinked, passwordOrPin);
        } else if (!record.getPassword().equals(passwordOrPin)) {
            record.setPassword(passwordOrPin);
        }

        record.setConnectIdLinked(connectIdLinked);

        if(workerLinked) {
            //If passed in false, we'll leave the setting unchanged
            record.setWorkerLinked(true);
        }

        storeApp(context, record);

        return record;
    }

    public static void storeApp(Context context, ConnectLinkedAppRecord record) {
        getConnectStorage(context, ConnectLinkedAppRecord.class).write(record);
    }

    public static void storeHqToken(Context context, String appId, String userId, String token, Date expiration) {
        ConnectLinkedAppRecord record = getAppData(context, appId, userId);
        if (record == null) {
            record = new ConnectLinkedAppRecord(appId, userId, false, "");
        }

        record.updateHqToken(token, expiration);

        getConnectStorage(context, ConnectLinkedAppRecord.class).write(record);
    }

    public static void setRegistrationPhase(Context context, ConnectTask phase) {
        ConnectUserRecord user = getUser(context);
        if (user != null) {
            user.setRegistrationPhase(phase);
            storeUser(context, user);
        }
    }

    public static Date getLastJobsUpdate(Context context) {
        Date lastDate = null;
        for(ConnectJobRecord job : getJobs(context, -1, null)) {
            if(lastDate == null || lastDate.before(job.getLastUpdate())) {
                lastDate = job.getLastUpdate();
            }
        }

        return lastDate != null ? lastDate : new Date();
    }

    public static void updateJobLearnProgress(Context context, ConnectJobRecord job) {
        SqlStorage<ConnectJobRecord> jobStorage = getConnectStorage(context, ConnectJobRecord.class);

        job.setLastLearnUpdate(new Date());

        //Check for existing DB ID
        Vector<ConnectJobRecord> existingJobs =
                jobStorage.getRecordsForValues(
                        new String[]{ConnectJobRecord.META_JOB_ID},
                        new Object[]{job.getJobId()});

        if(existingJobs.size() > 0) {
            ConnectJobRecord existing = existingJobs.get(0);
            existing.setComletedLearningModules(job.getCompletedLearningModules());
            existing.setLastUpdate(new Date());
            jobStorage.write(existing);

            //Also update learning and assessment records
            storeLearningRecords(context, job.getLearnings(), job.getJobId(), true);
            storeAssessments(context, job.getAssessments(), job.getJobId(), true);
        }
    }

    public static void upsertJob(Context context, ConnectJobRecord job) {
        List<ConnectJobRecord> list = new ArrayList<>();
        list.add(job);
        storeJobs(context, list, false);
    }

    public static int storeJobs(Context context, List<ConnectJobRecord> jobs, boolean pruneMissing) {
        SqlStorage<ConnectJobRecord> jobStorage = getConnectStorage(context, ConnectJobRecord.class);
        SqlStorage<ConnectAppRecord> appInfoStorage = getConnectStorage(context, ConnectAppRecord.class);
        SqlStorage<ConnectLearnModuleSummaryRecord> moduleStorage = getConnectStorage(context,
                ConnectLearnModuleSummaryRecord.class);
        SqlStorage<ConnectPaymentUnitRecord> paymentUnitStorage = getConnectStorage(context,
                ConnectPaymentUnitRecord.class);

        List<ConnectJobRecord> existingList = getJobs(context, -1, jobStorage);

        //Delete jobs that are no longer available
        Vector<Integer> jobIdsToDelete = new Vector<>();
        Vector<Integer> appInfoIdsToDelete = new Vector<>();
        Vector<Integer> moduleIdsToDelete = new Vector<>();
        Vector<Integer> paymentUnitIdsToDelete = new Vector<>();
        //Note when jobs are found in the loop below, we retrieve the DB ID into the incoming job
        for (ConnectJobRecord existing : existingList) {
            boolean stillExists = false;
            for (ConnectJobRecord incoming : jobs) {
                if(existing.getJobId() == incoming.getJobId()) {
                    incoming.setID(existing.getID());
                    stillExists = true;
                    break;
                }
            }

            if(!stillExists && pruneMissing) {
                //Mark the job, learn/deliver app infos, and learn module infos for deletion
                //Remember their IDs so we can delete them all at once after the loop
                jobIdsToDelete.add(existing.getID());

                appInfoIdsToDelete.add(existing.getLearnAppInfo().getID());
                appInfoIdsToDelete.add(existing.getDeliveryAppInfo().getID());

                for(ConnectLearnModuleSummaryRecord module : existing.getLearnAppInfo().getLearnModules()) {
                    moduleIdsToDelete.add(module.getID());
                }

                for(ConnectPaymentUnitRecord record : existing.getPaymentUnits()) {
                    paymentUnitIdsToDelete.add(record.getID());
                }
            }
        }

        if(pruneMissing) {
            jobStorage.removeAll(jobIdsToDelete);
            appInfoStorage.removeAll(appInfoIdsToDelete);
            moduleStorage.removeAll(moduleIdsToDelete);
            paymentUnitStorage.removeAll(paymentUnitIdsToDelete);
        }

        //Now insert/update jobs
        int newJobs = 0;
        for (ConnectJobRecord incomingJob : jobs) {
            incomingJob.setLastUpdate(new Date());

            if(incomingJob.getID() <= 0) {
                newJobs++;
                if (incomingJob.getStatus() == ConnectJobRecord.STATUS_AVAILABLE) {
                    incomingJob.setStatus(ConnectJobRecord.STATUS_AVAILABLE_NEW);
                }
            }

            //Now insert/update the job
            jobStorage.write(incomingJob);

            //Next, store the learn and delivery app info
            incomingJob.getLearnAppInfo().setJobId(incomingJob.getJobId());
            incomingJob.getDeliveryAppInfo().setJobId(incomingJob.getJobId());
            Vector<ConnectAppRecord> records = appInfoStorage.getRecordsForValues(
                            new String[]{ConnectAppRecord.META_JOB_ID},
                            new Object[]{incomingJob.getJobId()});

            for(ConnectAppRecord existing : records) {
                ConnectAppRecord incomingAppInfo = existing.getIsLearning() ? incomingJob.getLearnAppInfo() : incomingJob.getDeliveryAppInfo();
                incomingAppInfo.setID(existing.getID());
            }

            incomingJob.getLearnAppInfo().setLastUpdate(new Date());
            appInfoStorage.write(incomingJob.getLearnAppInfo());

            incomingJob.getDeliveryAppInfo().setLastUpdate(new Date());
            appInfoStorage.write(incomingJob.getDeliveryAppInfo());

            //Store the info for the learn modules
            //Delete modules that are no longer available
            Vector<Integer> foundIndexes = new Vector<>();
            //Note: Reusing this vector
            moduleIdsToDelete.clear();
            Vector<ConnectLearnModuleSummaryRecord> existingLearnModules =
                    moduleStorage.getRecordsForValues(
                            new String[]{ConnectLearnModuleSummaryRecord.META_JOB_ID},
                            new Object[]{incomingJob.getJobId()});
            for (ConnectLearnModuleSummaryRecord existing : existingLearnModules) {
                boolean stillExists = false;
                if(!foundIndexes.contains(existing.getModuleIndex())) {
                    for (ConnectLearnModuleSummaryRecord incoming :
                            incomingJob.getLearnAppInfo().getLearnModules()) {
                        if (Objects.equals(existing.getModuleIndex(), incoming.getModuleIndex())) {
                            incoming.setID(existing.getID());
                            stillExists = true;
                            foundIndexes.add(existing.getModuleIndex());

                            break;
                        }
                    }
                }

                if(!stillExists) {
                    moduleIdsToDelete.add(existing.getID());
                }
            }

            moduleStorage.removeAll(moduleIdsToDelete);

            for(ConnectLearnModuleSummaryRecord module : incomingJob.getLearnAppInfo().getLearnModules()) {
                module.setJobId(incomingJob.getJobId());
                module.setLastUpdate(new Date());
                moduleStorage.write(module);
            }


            //Store the payment units
            //Delete payment units that are no longer available
            foundIndexes = new Vector<>();
            //Note: Reusing this vector
            paymentUnitIdsToDelete.clear();
            Vector<ConnectPaymentUnitRecord> existingPaymentUnits =
                    paymentUnitStorage.getRecordsForValues(
                            new String[]{ConnectPaymentUnitRecord.META_JOB_ID},
                            new Object[]{incomingJob.getJobId()});
            for (ConnectPaymentUnitRecord existing : existingPaymentUnits) {
                boolean stillExists = false;
                if(!foundIndexes.contains(existing.getUnitId())) {
                    for (ConnectPaymentUnitRecord incoming :
                            incomingJob.getPaymentUnits()) {
                        if (Objects.equals(existing.getUnitId(), incoming.getUnitId())) {
                            incoming.setID(existing.getID());
                            stillExists = true;
                            foundIndexes.add(existing.getUnitId());

                            break;
                        }
                    }
                }

                if(!stillExists) {
                    paymentUnitIdsToDelete.add(existing.getID());
                }
            }

            paymentUnitStorage.removeAll(paymentUnitIdsToDelete);

            for(ConnectPaymentUnitRecord record : incomingJob.getPaymentUnits()) {
                record.setJobId(incomingJob.getJobId());
                paymentUnitStorage.write(record);
            }
        }

        return newJobs;
    }

    public static void storeLearningRecords(Context context, List<ConnectJobLearningRecord> learnings, int jobId, boolean pruneMissing) {
        SqlStorage<ConnectJobLearningRecord> storage = getConnectStorage(context, ConnectJobLearningRecord.class);

        List<ConnectJobLearningRecord> existingList = getLearnings(context, jobId, storage);

        //Delete records that are no longer available
        Vector<Integer> recordIdsToDelete = new Vector<>();
        for (ConnectJobLearningRecord existing : existingList) {
            boolean stillExists = false;
            for (ConnectJobLearningRecord incoming : learnings) {
                if(existing.getModuleId() == incoming.getModuleId() && existing.getDate().equals(incoming.getDate())) {
                    incoming.setID(existing.getID());
                    stillExists = true;
                    break;
                }
            }

            if(!stillExists && pruneMissing) {
                //Mark the record for deletion
                //Remember the ID so we can delete them all at once after the loop
                recordIdsToDelete.add(existing.getID());
            }
        }

        if(pruneMissing) {
            storage.removeAll(recordIdsToDelete);
        }

        //Now insert/update records
        for (ConnectJobLearningRecord incomingRecord : learnings) {
            incomingRecord.setLastUpdate(new Date());

            //Now insert/update the record
            storage.write(incomingRecord);
        }
    }

    public static void storeAssessments(Context context, List<ConnectJobAssessmentRecord> assessments, int jobId, boolean pruneMissing) {
        SqlStorage<ConnectJobAssessmentRecord> storage = getConnectStorage(context, ConnectJobAssessmentRecord.class);

        List<ConnectJobAssessmentRecord> existingList = getAssessments(context, jobId, storage);

        //Delete records that are no longer available
        Vector<Integer> recordIdsToDelete = new Vector<>();
        for (ConnectJobAssessmentRecord existing : existingList) {
            boolean stillExists = false;
            for (ConnectJobAssessmentRecord incoming : assessments) {
                if(existing.getScore() == incoming.getScore() && existing.getDate().equals(incoming.getDate())) {
                    incoming.setID(existing.getID());
                    stillExists = true;
                    break;
                }
            }

            if(!stillExists && pruneMissing) {
                //Mark the record for deletion
                //Remember the ID so we can delete them all at once after the loop
                recordIdsToDelete.add(existing.getID());
            }
        }

        if(pruneMissing) {
            storage.removeAll(recordIdsToDelete);
        }

        //Now insert/update records
        for (ConnectJobAssessmentRecord incomingRecord : assessments) {
            incomingRecord.setLastUpdate(new Date());

            //Now insert/update the record
            storage.write(incomingRecord);
        }
    }

    public static void storeDeliveries(Context context, List<ConnectJobDeliveryRecord> deliveries, int jobId, boolean pruneMissing) {
        SqlStorage<ConnectJobDeliveryRecord> storage = getConnectStorage(context, ConnectJobDeliveryRecord.class);

        List<ConnectJobDeliveryRecord> existingList = getDeliveries(context, jobId, storage);

        //Delete jobs that are no longer available
        Vector<Integer> recordIdsToDelete = new Vector<>();
        for (ConnectJobDeliveryRecord existing : existingList) {
            boolean stillExists = false;
            for (ConnectJobDeliveryRecord incoming : deliveries) {
                if(existing.getDeliveryId() == incoming.getDeliveryId()) {
                    incoming.setID(existing.getID());
                    stillExists = true;
                    break;
                }
            }

            if(!stillExists && pruneMissing) {
                //Mark the delivery for deletion
                //Remember the ID so we can delete them all at once after the loop
                recordIdsToDelete.add(existing.getID());
            }
        }

        if(pruneMissing) {
            storage.removeAll(recordIdsToDelete);
        }

        //Now insert/update deliveries
        for (ConnectJobDeliveryRecord incomingRecord : deliveries) {
            incomingRecord.setLastUpdate(new Date());

            //Now insert/update the delivery
            storage.write(incomingRecord);
        }
    }

    public static void storePayment(Context context, ConnectJobPaymentRecord payment) {
        SqlStorage<ConnectJobPaymentRecord> storage = getConnectStorage(context, ConnectJobPaymentRecord.class);
        storage.write(payment);
    }

    public static void storePayments(Context context, List<ConnectJobPaymentRecord> payments, int jobId, boolean pruneMissing) {
        SqlStorage<ConnectJobPaymentRecord> storage = getConnectStorage(context, ConnectJobPaymentRecord.class);

        List<ConnectJobPaymentRecord> existingList = getPayments(context, jobId, storage);

        //Delete payments that are no longer available
        Vector<Integer> recordIdsToDelete = new Vector<>();
        for (ConnectJobPaymentRecord existing : existingList) {
            boolean stillExists = false;
            for (ConnectJobPaymentRecord incoming : payments) {
                if(existing.getDate() == incoming.getDate()) {
                    incoming.setID(existing.getID());
                    stillExists = true;
                    break;
                }
            }

            if(!stillExists && pruneMissing) {
                //Mark the delivery for deletion
                //Remember the ID so we can delete them all at once after the loop
                recordIdsToDelete.add(existing.getID());
            }
        }

        if(pruneMissing) {
            storage.removeAll(recordIdsToDelete);
        }

        //Now insert/update deliveries
        for (ConnectJobPaymentRecord incomingRecord : payments) {
            storage.write(incomingRecord);
        }
    }

    public static ConnectAppRecord getAppRecord(Context context, String appId) {
        Vector<ConnectAppRecord> records = getConnectStorage(context, ConnectAppRecord.class).getRecordsForValues(
                new String[]{ConnectAppRecord.META_APP_ID},
                new Object[]{appId});
        return records.isEmpty() ? null: records.firstElement();
    }

    public static ConnectJobRecord getJob(Context context, int jobId) {
        Vector<ConnectJobRecord> jobs = getConnectStorage(context, ConnectJobRecord.class).getRecordsForValues(
                    new String[]{ConnectJobRecord.META_JOB_ID},
                    new Object[]{jobId});

        populateJobs(context, jobs);

        return jobs.isEmpty() ? null : jobs.firstElement();
    }

    public static List<ConnectJobRecord> getJobs(Context context, int status, SqlStorage<ConnectJobRecord> jobStorage) {
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

        populateJobs(context, jobs);

        return new ArrayList<>(jobs);
    }

    private static void populateJobs(Context context, Vector<ConnectJobRecord> jobs) {
        SqlStorage<ConnectAppRecord> appInfoStorage = getConnectStorage(context, ConnectAppRecord.class);
        SqlStorage<ConnectLearnModuleSummaryRecord> moduleStorage = getConnectStorage(context, ConnectLearnModuleSummaryRecord.class);
        SqlStorage<ConnectJobDeliveryRecord> deliveryStorage = getConnectStorage(context, ConnectJobDeliveryRecord.class);
        SqlStorage<ConnectJobPaymentRecord> paymentStorage = getConnectStorage(context, ConnectJobPaymentRecord.class);
        SqlStorage<ConnectJobLearningRecord> learningStorage = getConnectStorage(context, ConnectJobLearningRecord.class);
        SqlStorage<ConnectJobAssessmentRecord> assessmentStorage = getConnectStorage(context, ConnectJobAssessmentRecord.class);
        SqlStorage<ConnectPaymentUnitRecord> paymentUnitStorage = getConnectStorage(context, ConnectPaymentUnitRecord.class);
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                modules.sort(Comparator.comparingInt(ConnectLearnModuleSummaryRecord::getModuleIndex));
            }
            //else {
                //TODO: Brute force sort
            //}

            if(job.getLearnAppInfo() != null) {
                job.getLearnAppInfo().setLearnModules(modules);
            }

            //Retrieve payment units
            job.setPaymentUnits(paymentUnitStorage.getRecordsForValues(
                    new String[]{ConnectPaymentUnitRecord.META_JOB_ID},
                    new Object[]{job.getJobId()}));

            //Retrieve related data
            job.setDeliveries(getDeliveries(context, job.getJobId(), deliveryStorage));
            job.setPayments(getPayments(context, job.getJobId(), paymentStorage));
            job.setLearnings(getLearnings(context, job.getJobId(), learningStorage));
            job.setAssessments(getAssessments(context, job.getJobId(), assessmentStorage));
        }
    }

    public static List<ConnectJobRecord> getAvailableJobs(Context context) {
        return getAvailableJobs(context, null);
    }
    public static List<ConnectJobRecord> getAvailableJobs(Context context, SqlStorage<ConnectJobRecord> jobStorage) {
        List<ConnectJobRecord> jobs = getJobs(context, ConnectJobRecord.STATUS_AVAILABLE, jobStorage);
        jobs.addAll(getJobs(context, ConnectJobRecord.STATUS_AVAILABLE_NEW, jobStorage));

        List<ConnectJobRecord> filtered = new ArrayList<>();
        for(ConnectJobRecord record : jobs) {
            if(!record.isFinished()) {
                filtered.add(record);
            }
        }

        return filtered;
    }

    public static List<ConnectJobRecord> getTrainingJobs(Context context) {
        return getTrainingJobs(context, null);
    }
    public static List<ConnectJobRecord> getTrainingJobs(Context context, SqlStorage<ConnectJobRecord> jobStorage) {
        List<ConnectJobRecord> jobs = getJobs(context, ConnectJobRecord.STATUS_LEARNING, jobStorage);

        List<ConnectJobRecord> filtered = new ArrayList<>();
        for(ConnectJobRecord record : jobs) {
            if(!record.isFinished()) {
                filtered.add(record);
            }
        }

        return filtered;
    }

    public static List<ConnectJobRecord> getDeliveryJobs(Context context) {
        return getDeliveryJobs(context, null);
    }
    public static List<ConnectJobRecord> getDeliveryJobs(Context context, SqlStorage<ConnectJobRecord> jobStorage) {
        List<ConnectJobRecord> jobs = getJobs(context, ConnectJobRecord.STATUS_DELIVERING, jobStorage);

        List<ConnectJobRecord> filtered = new ArrayList<>();
        for(ConnectJobRecord record : jobs) {
            if(!record.isFinished()) {
                filtered.add(record);
            }
        }

        return filtered;
    }

    public static List<ConnectJobRecord> getFinishedJobs(Context context) {
        return getFinishedJobs(context, null);
    }
    public static List<ConnectJobRecord> getFinishedJobs(Context context, SqlStorage<ConnectJobRecord> jobStorage) {
        List<ConnectJobRecord> jobs = getJobs(context, -1, jobStorage);

        List<ConnectJobRecord> filtered = new ArrayList<>();
        for(ConnectJobRecord record : jobs) {
            if(record.isFinished()) {
                filtered.add(record);
            }
        }

        return filtered;
    }

    public static List<ConnectJobDeliveryRecord> getDeliveries(Context context, int jobId, SqlStorage<ConnectJobDeliveryRecord> deliveryStorage) {
        if(deliveryStorage == null) {
            deliveryStorage = getConnectStorage(context, ConnectJobDeliveryRecord.class);
        }

        Vector<ConnectJobDeliveryRecord> deliveries = deliveryStorage.getRecordsForValues(
                new String[]{ConnectJobDeliveryRecord.META_JOB_ID},
                new Object[]{jobId});

        return new ArrayList<>(deliveries);
    }

    public static List<ConnectJobPaymentRecord> getPayments(Context context, int jobId, SqlStorage<ConnectJobPaymentRecord> paymentStorage) {
        if(paymentStorage == null) {
            paymentStorage = getConnectStorage(context, ConnectJobPaymentRecord.class);
        }

        Vector<ConnectJobPaymentRecord> payments = paymentStorage.getRecordsForValues(
                new String[]{ConnectJobPaymentRecord.META_JOB_ID},
                new Object[]{jobId});

        return new ArrayList<>(payments);
    }

    public static List<ConnectJobLearningRecord> getLearnings(Context context, int jobId, SqlStorage<ConnectJobLearningRecord> learningStorage) {
        if(learningStorage == null) {
            learningStorage = getConnectStorage(context, ConnectJobLearningRecord.class);
        }

        Vector<ConnectJobLearningRecord> learnings = learningStorage.getRecordsForValues(
                new String[]{ConnectJobLearningRecord.META_JOB_ID},
                new Object[]{jobId});

        return new ArrayList<>(learnings);
    }

    public static List<ConnectJobAssessmentRecord> getAssessments(Context context, int jobId, SqlStorage<ConnectJobAssessmentRecord> assessmentStorage) {
        if(assessmentStorage == null) {
            assessmentStorage = getConnectStorage(context, ConnectJobAssessmentRecord.class);
        }

        Vector<ConnectJobAssessmentRecord> assessments = assessmentStorage.getRecordsForValues(
                new String[]{ConnectJobAssessmentRecord.META_JOB_ID},
                new Object[]{jobId});

        return new ArrayList<>(assessments);
    }
}
