package org.commcare.connect.database;

import android.content.Context;

import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.models.database.SqlStorage;
import org.javarosa.core.services.Logger;

import java.util.*;

public class JobStoreManager {

    private final SqlStorage<ConnectJobRecord> jobStorage;
    private final SqlStorage<ConnectAppRecord> appInfoStorage;
    private final SqlStorage<ConnectLearnModuleSummaryRecord> moduleStorage;
    private final SqlStorage<ConnectPaymentUnitRecord> paymentUnitStorage;

    public JobStoreManager(Context context) {
        this.jobStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobRecord.class);
        this.appInfoStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectAppRecord.class);
        this.moduleStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectLearnModuleSummaryRecord.class);
        this.paymentUnitStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectPaymentUnitRecord.class);
    }

    public int storeJobs(Context context, List<ConnectJobRecord> jobs, boolean pruneMissing) {
        try {
            ConnectDatabaseHelper.connectDatabase.beginTransaction();
            List<ConnectJobRecord> existingList = getCompositeJobs(context, -1, jobStorage);

            if (pruneMissing) {
                pruneOldJobs(existingList, jobs);
            }
            int newJob = processAndStoreJobs(existingList, jobs);
            ConnectDatabaseHelper.connectDatabase.setTransactionSuccessful();
            return newJob;
        } catch(Exception e) {
            Logger.exception("Error storing jobs", e);
            throw e;
        } finally {
            ConnectDatabaseHelper.connectDatabase.endTransaction();
        }
    }

    private void pruneOldJobs(List<ConnectJobRecord> existingList, List<ConnectJobRecord> serverList) {
        Set<String> incomingJobUUIDs = new HashSet<>();
        for (ConnectJobRecord job : serverList) {
            incomingJobUUIDs.add(job.getJobUUID());
        }

        Vector<Integer> jobIdsToDelete = new Vector<>();
        Vector<Integer> appInfoIdsToDelete = new Vector<>();
        Vector<Integer> moduleIdsToDelete = new Vector<>();
        Vector<Integer> paymentUnitIdsToDelete = new Vector<>();

        for (ConnectJobRecord existing : existingList) {
            if (!incomingJobUUIDs.contains(existing.getJobUUID())) {
                jobIdsToDelete.add(existing.getID());
                appInfoIdsToDelete.add(existing.getLearnAppInfo().getID());
                appInfoIdsToDelete.add(existing.getDeliveryAppInfo().getID());

                for (ConnectLearnModuleSummaryRecord module : existing.getLearnAppInfo().getLearnModules()) {
                    moduleIdsToDelete.add(module.getID());
                }

                for (ConnectPaymentUnitRecord record : existing.getPaymentUnits()) {
                    paymentUnitIdsToDelete.add(record.getID());
                }
            }
        }

        deleteRecords(jobIdsToDelete, appInfoIdsToDelete, moduleIdsToDelete, paymentUnitIdsToDelete);
    }

    private void deleteRecords(Vector<Integer> jobIds, Vector<Integer> appInfoIds, Vector<Integer> moduleIds, Vector<Integer> paymentUnitIds) {
        jobStorage.removeAll(jobIds);
        appInfoStorage.removeAll(appInfoIds);
        moduleStorage.removeAll(moduleIds);
        paymentUnitStorage.removeAll(paymentUnitIds);
    }

    private int processAndStoreJobs(List<ConnectJobRecord> existingJobs, List<ConnectJobRecord> jobs) {
        int newJobs = 0;

        for (ConnectJobRecord job : jobs) {
            job.setLastUpdate(new Date());
            boolean isExisting = storeOrUpdateJob(existingJobs, job);
            if (!isExisting) {
                newJobs++;
            }
        }

        return newJobs;
    }

    private boolean storeOrUpdateJob(List<ConnectJobRecord> existingJobs, ConnectJobRecord job) {
        // Check if the job already exists
        boolean isExisting = false;
        for (ConnectJobRecord existingJob : existingJobs) {
            if (existingJob.getJobUUID().equals(job.getJobUUID())) {
                job.setID(existingJob.getID());  // Set ID for updating
                if(existingJob.getStatus() > job.getStatus()) {
                    //Status should never go backwards
                    job.setStatus(existingJob.getStatus());
                }
                isExisting = true;
                break;
            }
        }

        // If not existing, create a new record
        if (!isExisting) {
            if (job.getStatus() == ConnectJobRecord.STATUS_AVAILABLE) {
                job.setStatus(ConnectJobRecord.STATUS_AVAILABLE_NEW);
            }
        }
        job.setLastUpdate(new Date());
        jobStorage.write(job);
        // Store or update related entities
        storeAppInfo(job);
        storeModules(job);
        storePaymentUnits(job);
        return isExisting;
    }

    private void storeAppInfo(ConnectJobRecord job) {
        // Check if LearnAppInfo already exists
        Vector<ConnectAppRecord> existingAppInfos = appInfoStorage.getRecordsForValues(
                new String[]{ConnectAppRecord.META_JOB_UUID},
                new Object[]{job.getJobUUID()}
        );

        // Update LearnAppInfo and DeliveryAppInfo if they already exist
        for (ConnectAppRecord existing : existingAppInfos) {
            if (existing.getIsLearning()) {
                job.getLearnAppInfo().setID(existing.getID());  // Set ID for updating
            } else {
                job.getDeliveryAppInfo().setID(existing.getID());  // Set ID for updating
            }
        }
        job.getLearnAppInfo().setJobId(job.getJobId());
        job.getDeliveryAppInfo().setJobId(job.getJobId());
        job.getLearnAppInfo().setJobUUID(job.getJobUUID());
        job.getDeliveryAppInfo().setJobUUID(job.getJobUUID());
        job.getLearnAppInfo().setLastUpdate(new Date());
        job.getDeliveryAppInfo().setLastUpdate(new Date());

        appInfoStorage.write(job.getLearnAppInfo());
        appInfoStorage.write(job.getDeliveryAppInfo());
    }

    private void storeModules(ConnectJobRecord job) {
        Vector<ConnectLearnModuleSummaryRecord> existingModules = moduleStorage.getRecordsForValues(
                new String[]{ConnectLearnModuleSummaryRecord.META_JOB_UUID},
                new Object[]{job.getJobUUID()}
        );

        // Prune old modules that are not present in the incoming data
        Vector<Integer> moduleIdsToDelete = new Vector<>();
        for (ConnectLearnModuleSummaryRecord existing : existingModules) {
            boolean stillExists = false;
            for (ConnectLearnModuleSummaryRecord incoming : job.getLearnAppInfo().getLearnModules()) {
                if (Objects.equals(existing.getSlug(), incoming.getSlug())) {
                    incoming.setID(existing.getID());  // Set ID for updating
                    stillExists = true;
                    break;
                }
            }
            if (!stillExists) {
                moduleIdsToDelete.add(existing.getID());
            }
        }
        moduleStorage.removeAll(moduleIdsToDelete);

        // Store or update current modules
        for (ConnectLearnModuleSummaryRecord module : job.getLearnAppInfo().getLearnModules()) {
            module.setJobId(job.getJobId());
            module.setJobUUID(job.getJobUUID());
            module.setLastUpdate(new Date());
            moduleStorage.write(module);
        }
    }

    private void storePaymentUnits(ConnectJobRecord job) {
        Vector<ConnectPaymentUnitRecord> existingPaymentUnits = paymentUnitStorage.getRecordsForValues(
                new String[]{ConnectPaymentUnitRecord.META_JOB_UUID},
                new Object[]{job.getJobUUID()}
        );

        // Prune old payment units that are not present in the incoming data
        Vector<Integer> paymentUnitIdsToDelete = new Vector<>();
        for (ConnectPaymentUnitRecord existing : existingPaymentUnits) {
            boolean stillExists = false;
            for (ConnectPaymentUnitRecord incoming : job.getPaymentUnits()) {
                if (Objects.equals(existing.getUnitUUID(), incoming.getUnitUUID())) {
                    incoming.setID(existing.getID());  // Set ID for updating
                    stillExists = true;
                    break;
                }
            }
            if (!stillExists) {
                paymentUnitIdsToDelete.add(existing.getID());
            }
        }
        paymentUnitStorage.removeAll(paymentUnitIdsToDelete);

        // Store or update current payment units
        for (ConnectPaymentUnitRecord record : job.getPaymentUnits()) {
            record.setJobId(job.getJobId());
            record.setJobUUID(job.getJobUUID());
            paymentUnitStorage.write(record);
        }
    }

    private List<ConnectJobRecord> getCompositeJobs(Context context, int limit, SqlStorage<ConnectJobRecord> jobStorage) {
        // Placeholder for job retrieval logic
        return ConnectJobUtils.getCompositeJobs(context, ConnectJobRecord.STATUS_ALL_JOBS, jobStorage);
    }
}
