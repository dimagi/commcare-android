package org.commcare.connect.database;

import android.content.Context;

import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.models.database.SqlStorage;
import org.javarosa.core.services.Logger;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class JobStoreManager {

    private final SqlStorage<ConnectJobRecord> jobStorage;
    private final SqlStorage<ConnectAppRecord> appInfoStorage;
    private final SqlStorage<ConnectLearnModuleSummaryRecord> moduleStorage;
    private final SqlStorage<ConnectPaymentUnitRecord> paymentUnitStorage;
    private final ReentrantLock lock = new ReentrantLock();

    public JobStoreManager(Context context) {
        this.jobStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectJobRecord.class);
        this.appInfoStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectAppRecord.class);
        this.moduleStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectLearnModuleSummaryRecord.class);
        this.paymentUnitStorage = ConnectDatabaseHelper.getConnectStorage(context, ConnectPaymentUnitRecord.class);
    }

    public int storeJobs(Context context, List<ConnectJobRecord> jobs, boolean pruneMissing) {
        lock.lock();
        try {
            List<ConnectJobRecord> existingList = getJobs(context, -1, jobStorage);

            if (pruneMissing) {
                pruneOldJobs(existingList, jobs);
            }

            return processAndStoreJobs(existingList,jobs);
        } catch (Exception e) {
            Logger.exception("Error storing jobs", e);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    private void pruneOldJobs(List<ConnectJobRecord> existingList, List<ConnectJobRecord> jobs) {
        Set<Integer> incomingJobIds = new HashSet<>();
        for (ConnectJobRecord job : jobs) {
            incomingJobIds.add(job.getJobId());
        }

        Vector<Integer> jobIdsToDelete = new Vector<>();
        Vector<Integer> appInfoIdsToDelete = new Vector<>();
        Vector<Integer> moduleIdsToDelete = new Vector<>();
        Vector<Integer> paymentUnitIdsToDelete = new Vector<>();

        for (ConnectJobRecord existing : existingList) {
            if (!incomingJobIds.contains(existing.getJobId())) {
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

    private int processAndStoreJobs(List<ConnectJobRecord> existingJobs,List<ConnectJobRecord> jobs) {
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

    public boolean storeOrUpdateJob(List<ConnectJobRecord> existingJobs,ConnectJobRecord job) {
        lock.lock();
        try {
            // Store or update related entities
            storeAppInfo(job);
            storeModules(job);
            storePaymentUnits(job);
            if (!existingJobs.isEmpty()) {
                // Job exists, update the existing record
                ConnectJobRecord existingJob = existingJobs.get(0);
                job.setID(existingJob.getID());
                job.setLastUpdate(new Date());
                jobStorage.write(job);
                return true;
            } else {
                // Job does not exist, create a new record
                job.setLastUpdate(new Date());
                if (job.getStatus() == ConnectJobRecord.STATUS_AVAILABLE) {
                    job.setStatus(ConnectJobRecord.STATUS_AVAILABLE_NEW);
                }
                jobStorage.write(job);
                return false;
            }

        } catch (Exception e) {
            Logger.exception("Error storing or updating job: " + job.getTitle(), e);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    private void storeAppInfo(ConnectJobRecord job) {
        job.getLearnAppInfo().setJobId(job.getJobId());
        job.getDeliveryAppInfo().setJobId(job.getJobId());
        job.getLearnAppInfo().setLastUpdate(new Date());
        job.getDeliveryAppInfo().setLastUpdate(new Date());

        appInfoStorage.write(job.getLearnAppInfo());
        appInfoStorage.write(job.getDeliveryAppInfo());
    }

    private void storeModules(ConnectJobRecord job) {
        Vector<ConnectLearnModuleSummaryRecord> existingModules = moduleStorage.getRecordsForValues(
                new String[]{ConnectLearnModuleSummaryRecord.META_JOB_ID},
                new Object[]{job.getJobId()}
        );

        // Prune old modules that are not present in the incoming data
        Vector<Integer> moduleIdsToDelete = new Vector<>();
        for (ConnectLearnModuleSummaryRecord existing : existingModules) {
            boolean stillExists = false;
            for (ConnectLearnModuleSummaryRecord incoming : job.getLearnAppInfo().getLearnModules()) {
                if (Objects.equals(existing.getModuleIndex(), incoming.getModuleIndex())) {
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
            module.setLastUpdate(new Date());
            moduleStorage.write(module);
        }
    }

    private void storePaymentUnits(ConnectJobRecord job) {
        Vector<ConnectPaymentUnitRecord> existingPaymentUnits = paymentUnitStorage.getRecordsForValues(
                new String[]{ConnectPaymentUnitRecord.META_JOB_ID},
                new Object[]{job.getJobId()}
        );

        // Prune old payment units that are not present in the incoming data
        Vector<Integer> paymentUnitIdsToDelete = new Vector<>();
        for (ConnectPaymentUnitRecord existing : existingPaymentUnits) {
            boolean stillExists = false;
            for (ConnectPaymentUnitRecord incoming : job.getPaymentUnits()) {
                if (Objects.equals(existing.getUnitId(), incoming.getUnitId())) {
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
            paymentUnitStorage.write(record);
        }
    }

    private List<ConnectJobRecord> getJobs(Context context, int limit, SqlStorage<ConnectJobRecord> jobStorage) {
        // Placeholder for job retrieval logic
        return ConnectJobUtils.getJobs(context, ConnectJobRecord.STATUS_ALL_JOBS, jobStorage);
    }
}
