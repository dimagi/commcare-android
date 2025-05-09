package org.commcare.connect.database;

import android.content.Context;
import android.os.Build;

import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryFlagRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobLearningRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.connect.ConnectIDManager;
import org.commcare.models.database.SqlStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Vector;

public class ConnectJobUtils {

    public static void upsertJob(Context context, ConnectJobRecord job) {
        List<ConnectJobRecord> list = new ArrayList<>();
        list.add(job);
        new JobStoreManager(context).storeJobs(context, list, false);
    }

    public static ConnectJobRecord getCompositeJob(Context context, int jobId) {
        Vector<ConnectJobRecord> jobs = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobRecord.class).getRecordsForValues(
                new String[]{ConnectJobRecord.META_JOB_ID},
                new Object[]{jobId});

        populateJobs(context, jobs);

        return jobs.isEmpty() ? null : jobs.firstElement();
    }

    public static List<ConnectJobRecord> getCompositeJobs(Context context, int status, SqlStorage<ConnectJobRecord> jobStorage) {
        if (jobStorage == null) {
            jobStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobRecord.class);
        }

        Vector<ConnectJobRecord> jobs;
        if (status != ConnectJobRecord.STATUS_ALL_JOBS) {
            jobs = jobStorage.getRecordsForValues(
                    new String[]{ConnectJobRecord.META_STATUS},
                    new Object[]{status});
        } else {
            jobs = jobStorage.getRecordsForValues(new String[]{}, new Object[]{});
        }

        populateJobs(context, jobs);

        return new ArrayList<>(jobs);
    }

    public static int storeJobs(Context context, List<ConnectJobRecord> jobs, boolean pruneMissing) {
        return new JobStoreManager(context).storeJobs(context, jobs, pruneMissing);
    }

    private static void populateJobs(Context context, Vector<ConnectJobRecord> jobs) {
        SqlStorage<ConnectAppRecord> appInfoStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectAppRecord.class);
        SqlStorage<ConnectLearnModuleSummaryRecord> moduleStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectLearnModuleSummaryRecord.class);
        SqlStorage<ConnectJobDeliveryRecord> deliveryStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobDeliveryRecord.class);
        SqlStorage<ConnectJobPaymentRecord> paymentStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobPaymentRecord.class);
        SqlStorage<ConnectJobLearningRecord> learningStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobLearningRecord.class);
        SqlStorage<ConnectJobAssessmentRecord> assessmentStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobAssessmentRecord.class);
        SqlStorage<ConnectPaymentUnitRecord> paymentUnitStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectPaymentUnitRecord.class);
        for (ConnectJobRecord job : jobs) {
            //Retrieve learn and delivery app info
            Vector<ConnectAppRecord> existingAppInfos = appInfoStorage.getRecordsForValues(
                    new String[]{ConnectAppRecord.META_JOB_ID},
                    new Object[]{job.getJobId()});

            for (ConnectAppRecord info : existingAppInfos) {
                if (info.getIsLearning()) {
                    job.setLearnAppInfo(info);
                } else {
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
            } else {
                Collections.sort(modules, new Comparator<ConnectLearnModuleSummaryRecord>() {
                    @Override
                    public int compare(ConnectLearnModuleSummaryRecord o1, ConnectLearnModuleSummaryRecord o2) {
                        return Integer.compare(o1.getModuleIndex(), o2.getModuleIndex());
                    }
                });
            }

            if (job.getLearnAppInfo() != null) {
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
        List<ConnectJobRecord> jobs = getCompositeJobs(context, ConnectJobRecord.STATUS_AVAILABLE, jobStorage);
        jobs.addAll(getCompositeJobs(context, ConnectJobRecord.STATUS_AVAILABLE_NEW, jobStorage));
        return getUnFinishedJobs(jobs);
    }

    public static List<ConnectJobRecord> getTrainingJobs(Context context) {
        return getTrainingJobs(context, null);
    }

    public static List<ConnectJobRecord> getTrainingJobs(Context context, SqlStorage<ConnectJobRecord> jobStorage) {
        List<ConnectJobRecord> jobs = getCompositeJobs(context, ConnectJobRecord.STATUS_LEARNING, jobStorage);
        return getUnFinishedJobs(jobs);
    }

    public static List<ConnectJobRecord> getDeliveryJobs(Context context) {
        return getDeliveryJobs(context, null);
    }

    public static List<ConnectJobRecord> getDeliveryJobs(Context context, SqlStorage<ConnectJobRecord> jobStorage) {
        List<ConnectJobRecord> jobs = getCompositeJobs(context, ConnectJobRecord.STATUS_DELIVERING, jobStorage);

        List<ConnectJobRecord> filtered = new ArrayList<>();
        for (ConnectJobRecord record : jobs) {
            if (!record.isFinished() && !record.getIsUserSuspended()) {
                filtered.add(record);
            }
        }

        return filtered;
    }

    public static List<ConnectJobRecord> getUnFinishedJobs(List<ConnectJobRecord> jobs) {
        List<ConnectJobRecord> filtered = new ArrayList<>();
        for (ConnectJobRecord record : jobs) {
            if (!record.isFinished()) {
                filtered.add(record);
            }
        }

        return filtered;
    }

    public static List<ConnectJobRecord> getFinishedJobs(Context context, SqlStorage<ConnectJobRecord> jobStorage) {
        List<ConnectJobRecord> jobs = getCompositeJobs(context, ConnectJobRecord.STATUS_ALL_JOBS, jobStorage);

        List<ConnectJobRecord> filtered = new ArrayList<>();
        for (ConnectJobRecord record : jobs) {
            if (record.isFinished() || record.getIsUserSuspended()) {
                filtered.add(record);
            }
        }

        return filtered;
    }

    public static void storeDeliveries(Context context, List<ConnectJobDeliveryRecord> deliveries, int jobId, boolean pruneMissing) {
        SqlStorage<ConnectJobDeliveryRecord> storage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobDeliveryRecord.class);

        List<ConnectJobDeliveryRecord> existingDeliveries = getDeliveries(context, jobId, storage);

        //Delete jobs that are no longer available
        Vector<Integer> recordIdsToDelete = new Vector<>();
        for (ConnectJobDeliveryRecord existing : existingDeliveries) {
            boolean stillExists = false;
            for (ConnectJobDeliveryRecord incoming : deliveries) {
                if (existing.getDeliveryId() == incoming.getDeliveryId()) {
                    incoming.setID(existing.getID());
                    stillExists = true;
                    break;
                }
            }

            if (!stillExists && pruneMissing) {
                //Mark the delivery for deletion
                //Remember the ID so we can delete them all at once after the loop
                recordIdsToDelete.add(existing.getID());
            }
        }

        if (pruneMissing) {
            storage.removeAll(recordIdsToDelete);
        }

        //Now insert/update deliveries
        for (ConnectJobDeliveryRecord incomingRecord : deliveries) {
            incomingRecord.setLastUpdate(new Date());

            //Now insert/update the delivery
            storage.write(incomingRecord);

            storeDeliveryFlags(context, incomingRecord.getFlags(), incomingRecord.getDeliveryId());
        }
    }

    public static void storeDeliveryFlags(Context context, List<ConnectJobDeliveryFlagRecord> flags,
                                          int deliveryId) {
        SqlStorage<ConnectJobDeliveryFlagRecord> storage = ConnectDatabaseHelper.getConnectStorage(context,
                ConnectJobDeliveryFlagRecord.class);
        ConnectDatabaseHelper.connectDatabase.beginTransaction();
        try {
            storage.removeAll(storage.getIDsForValues(new String[]{ConnectJobDeliveryFlagRecord.META_DELIVERY_ID},
                    new Object[]{deliveryId}));

            for (ConnectJobDeliveryFlagRecord incomingRecord : flags) {
                storage.write(incomingRecord);
            }
            ConnectDatabaseHelper.connectDatabase.setTransactionSuccessful();
        } finally {
            ConnectDatabaseHelper.connectDatabase.endTransaction();
        }

    }

    public static void storePayment(Context context, ConnectJobPaymentRecord payment) {
        SqlStorage<ConnectJobPaymentRecord> storage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobPaymentRecord.class);
        storage.write(payment);
    }

    public static void storePayments(Context context, List<ConnectJobPaymentRecord> payments, int jobId, boolean pruneMissing) {
        SqlStorage<ConnectJobPaymentRecord> storage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobPaymentRecord.class);

        List<ConnectJobPaymentRecord> existingList = getPayments(context, jobId, storage);

        //Delete payments that are no longer available
        Vector<Integer> recordIdsToDelete = new Vector<>();
        for (ConnectJobPaymentRecord existing : existingList) {
            boolean stillExists = false;
            for (ConnectJobPaymentRecord incoming : payments) {
                if (existing.getPaymentId().equals(incoming.getPaymentId())) {
                    incoming.setID(existing.getID());
                    stillExists = true;
                    break;
                }
            }

            if (!stillExists && pruneMissing) {
                //Mark the delivery for deletion
                //Remember the ID so we can delete them all at once after the loop
                recordIdsToDelete.add(existing.getID());
            }
        }

        if (pruneMissing) {
            storage.removeAll(recordIdsToDelete);
        }

        //Now insert/update deliveries
        for (ConnectJobPaymentRecord incomingRecord : payments) {
            storage.write(incomingRecord);
        }
    }

    public static List<ConnectJobDeliveryRecord> getDeliveries(Context context, int jobId, SqlStorage<ConnectJobDeliveryRecord> deliveryStorage) {
        if (deliveryStorage == null) {
            deliveryStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobDeliveryRecord.class);
        }

        Vector<ConnectJobDeliveryRecord> deliveries = deliveryStorage.getRecordsForValues(
                new String[]{ConnectJobDeliveryRecord.META_JOB_ID},
                new Object[]{jobId});

        return new ArrayList<>(deliveries);
    }

    public static List<ConnectJobDeliveryFlagRecord> getDeliveryFlags(Context context, int deliveryId, SqlStorage<ConnectJobDeliveryFlagRecord> flagStorage) {
        if (flagStorage == null) {
            flagStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobDeliveryFlagRecord.class);
        }

        Vector<ConnectJobDeliveryFlagRecord> flags = flagStorage.getRecordsForValues(
                new String[]{ConnectJobDeliveryFlagRecord.META_DELIVERY_ID},
                new Object[]{deliveryId});

        return new ArrayList<>(flags);
    }

    public static List<ConnectJobPaymentRecord> getPayments(Context context, int jobId, SqlStorage<ConnectJobPaymentRecord> paymentStorage) {
        if (paymentStorage == null) {
            paymentStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobPaymentRecord.class);
        }

        Vector<ConnectJobPaymentRecord> payments = paymentStorage.getRecordsForValues(
                new String[]{ConnectJobPaymentRecord.META_JOB_ID},
                new Object[]{jobId});

        return new ArrayList<>(payments);
    }

    public static List<ConnectJobLearningRecord> getLearnings(Context context, int jobId, SqlStorage<ConnectJobLearningRecord> learningStorage) {
        if (learningStorage == null) {
            learningStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobLearningRecord.class);
        }

        Vector<ConnectJobLearningRecord> learnings = learningStorage.getRecordsForValues(
                new String[]{ConnectJobLearningRecord.META_JOB_ID},
                new Object[]{jobId});

        return new ArrayList<>(learnings);
    }

    public static List<ConnectJobAssessmentRecord> getAssessments(Context context, int jobId, SqlStorage<ConnectJobAssessmentRecord> assessmentStorage) {
        if (assessmentStorage == null) {
            assessmentStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobAssessmentRecord.class);
        }

        Vector<ConnectJobAssessmentRecord> assessments = assessmentStorage.getRecordsForValues(
                new String[]{ConnectJobAssessmentRecord.META_JOB_ID},
                new Object[]{jobId});

        return new ArrayList<>(assessments);
    }

    public static void storeAssessments(Context context, List<ConnectJobAssessmentRecord> assessments, int jobId, boolean pruneMissing) {
        SqlStorage<ConnectJobAssessmentRecord> storage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobAssessmentRecord.class);

        List<ConnectJobAssessmentRecord> existingList = getAssessments(context, jobId, storage);

        //Delete records that are no longer available
        Vector<Integer> recordIdsToDelete = new Vector<>();
        for (ConnectJobAssessmentRecord existing : existingList) {
            boolean stillExists = false;
            for (ConnectJobAssessmentRecord incoming : assessments) {
                if (existing.getScore() == incoming.getScore() && existing.getDate().equals(incoming.getDate())) {
                    incoming.setID(existing.getID());
                    stillExists = true;
                    break;
                }
            }

            if (!stillExists && pruneMissing) {
                //Mark the record for deletion
                //Remember the ID so we can delete them all at once after the loop
                recordIdsToDelete.add(existing.getID());
            }
        }

        if (pruneMissing) {
            storage.removeAll(recordIdsToDelete);
        }

        //Now insert/update records
        for (ConnectJobAssessmentRecord incomingRecord : assessments) {
            incomingRecord.setLastUpdate(new Date());

            //Now insert/update the record
            storage.write(incomingRecord);
        }
    }

    public static void updateJobLearnProgress(Context context, ConnectJobRecord job) {
        SqlStorage<ConnectJobRecord> jobStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobRecord.class);

        job.setLastLearnUpdate(new Date());

        //Check for existing DB ID
        Vector<ConnectJobRecord> existingJobs =
                jobStorage.getRecordsForValues(
                        new String[]{ConnectJobRecord.META_JOB_ID},
                        new Object[]{job.getJobId()});

        if (existingJobs.size() > 0) {
            ConnectJobRecord existing = existingJobs.get(0);
            existing.setCompletedLearningModules(job.getCompletedLearningModules());
            existing.setLastUpdate(new Date());
            jobStorage.write(existing);

            //Also update learning and assessment records
            storeLearningRecords(context, job.getLearnings(), job.getJobId(), true);
            storeAssessments(context, job.getAssessments(), job.getJobId(), true);
        }
    }

    public static Date getLastJobsUpdate(Context context) {
        Date lastDate = null;
        for (ConnectJobRecord job : getCompositeJobs(context, ConnectJobRecord.STATUS_ALL_JOBS, null)) {
            if (lastDate == null || lastDate.before(job.getLastUpdate())) {
                lastDate = job.getLastUpdate();
            }
        }

        return lastDate != null ? lastDate : new Date();
    }

    public static void storeLearningRecords(Context context, List<ConnectJobLearningRecord> learnings, int jobId, boolean pruneMissing) {
        SqlStorage<ConnectJobLearningRecord> storage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobLearningRecord.class);

        List<ConnectJobLearningRecord> existingList = getLearnings(context, jobId, storage);

        //Delete records that are no longer available
        Vector<Integer> recordIdsToDelete = new Vector<>();
        for (ConnectJobLearningRecord existing : existingList) {
            boolean stillExists = false;
            for (ConnectJobLearningRecord incoming : learnings) {
                if (existing.getModuleId() == incoming.getModuleId() && existing.getDate().equals(incoming.getDate())) {
                    incoming.setID(existing.getID());
                    stillExists = true;
                    break;
                }
            }

            if (!stillExists && pruneMissing) {
                //Mark the record for deletion
                //Remember the ID so we can delete them all at once after the loop
                recordIdsToDelete.add(existing.getID());
            }
        }

        if (pruneMissing) {
            storage.removeAll(recordIdsToDelete);
        }

        //Now insert/update records
        for (ConnectJobLearningRecord incomingRecord : learnings) {
            incomingRecord.setLastUpdate(new Date());

            //Now insert/update the record
            storage.write(incomingRecord);
        }
    }

    public static ConnectAppRecord getAppRecord(Context context, String appId) {
        if (ConnectIDManager.getInstance().isloggedIn()) {
            Vector<ConnectAppRecord> records = ConnectDatabaseHelper.getConnectStorage(context, ConnectAppRecord.class).getRecordsForValues(
                    new String[]{ConnectAppRecord.META_APP_ID},
                    new Object[]{appId});
            return records.isEmpty() ? null : records.firstElement();
        }
        return null;
    }

}
