package org.commcare.connect.database;

import static org.commcare.connect.ConnectConstants.OPPORTUNITY_STATUS_LEARN;
import static org.commcare.connect.ConnectConstants.CCC_GENERIC_OPPORTUNITY;
import static org.commcare.connect.ConnectConstants.CCC_DEST_PAYMENTS;
import static org.commcare.connect.ConnectConstants.CCC_DEST_DELIVERY_PROGRESS;
import static org.commcare.connect.ConnectConstants.CCC_DEST_LEARN_PROGRESS;
import static org.commcare.connect.ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryFlagRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobLearningRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.connect.PersonalIdManager;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.ConnectJobPreferences;
import org.javarosa.xform.util.CalendarUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

public class ConnectJobUtils {

    public static void upsertJob(ConnectJobRecord job) {
        Context context = CommCareApplication.instance();
        List<ConnectJobRecord> list = new ArrayList<>();
        list.add(job);
        new JobStoreManager().storeJobs(context, list, false);
    }

    public static ConnectJobPreferences getJobPreferences(String jobUUID) {
        return new ConnectJobPreferences(jobUUID);
    }

    public static ConnectJobRecord getCompositeJob(String jobUUID) {
        Vector<ConnectJobRecord> jobs = ConnectDatabaseHelper.getConnectStorage(
                ConnectJobRecord.class
        ).getRecordsForValues(
                new String[]{ConnectJobRecord.META_JOB_UUID},
                new Object[]{jobUUID}
        );

        populateJobs(jobs);

        return jobs.isEmpty() ? null : jobs.firstElement();
    }

    public static ConnectJobRecord getJobForApp(Context context, String appId) {
        ConnectAppRecord appRecord = getAppRecord(appId);
        if (appRecord == null) {
            return null;
        }

        return getCompositeJob(appRecord.getJobUUID());
    }

    public static List<ConnectJobRecord> getCompositeJobs(
            int status,
            SqlStorage<ConnectJobRecord> jobStorage
    ) {
        if (jobStorage == null) {
            jobStorage = ConnectDatabaseHelper.getConnectStorage(ConnectJobRecord.class);
        }

        Vector<ConnectJobRecord> jobs;
        if (status != ConnectJobRecord.STATUS_ALL_JOBS) {
            jobs = jobStorage.getRecordsForValues(
                    new String[]{ConnectJobRecord.META_STATUS},
                    new Object[]{status}
            );
        } else {
            jobs = jobStorage.getRecordsForValues(new String[]{}, new Object[]{});
        }

        populateJobs(jobs);

        return new ArrayList<>(jobs);
    }

    public static int storeJobs(
            Context context,
            List<ConnectJobRecord> jobs,
            boolean pruneMissing
    ) {
        return new JobStoreManager().storeJobs(context, jobs, pruneMissing);
    }

    private static void populateJobs(Vector<ConnectJobRecord> jobs) {
        SqlStorage<ConnectAppRecord> appInfoStorage = ConnectDatabaseHelper.getConnectStorage(
                ConnectAppRecord.class
        );
        SqlStorage<ConnectLearnModuleSummaryRecord> moduleStorage = ConnectDatabaseHelper.getConnectStorage(
                ConnectLearnModuleSummaryRecord.class
        );
        SqlStorage<ConnectJobDeliveryRecord> deliveryStorage = ConnectDatabaseHelper.getConnectStorage(
                ConnectJobDeliveryRecord.class
        );
        SqlStorage<ConnectJobPaymentRecord> paymentStorage = ConnectDatabaseHelper.getConnectStorage(
                ConnectJobPaymentRecord.class
        );
        SqlStorage<ConnectJobLearningRecord> learningStorage = ConnectDatabaseHelper.getConnectStorage(
                ConnectJobLearningRecord.class
        );
        SqlStorage<ConnectJobAssessmentRecord> assessmentStorage = ConnectDatabaseHelper.getConnectStorage(
                ConnectJobAssessmentRecord.class
        );
        SqlStorage<ConnectPaymentUnitRecord> paymentUnitStorage = ConnectDatabaseHelper.getConnectStorage(
                ConnectPaymentUnitRecord.class
        );
        for (ConnectJobRecord job : jobs) {
            //Retrieve learn and delivery app info
            Vector<ConnectAppRecord> existingAppInfos = appInfoStorage.getRecordsForValues(
                    new String[]{ConnectAppRecord.META_JOB_UUID},
                    new Object[]{job.getJobUUID()}
            );

            for (ConnectAppRecord info : existingAppInfos) {
                if (info.getIsLearning()) {
                    job.setLearnAppInfo(info);
                } else {
                    job.setDeliveryAppInfo(info);
                }
            }

            //Retrieve learn modules
            Vector<ConnectLearnModuleSummaryRecord> existingModules = moduleStorage.getRecordsForValues(
                    new String[]{ConnectLearnModuleSummaryRecord.META_JOB_UUID},
                    new Object[]{job.getJobUUID()}
            );

            List<ConnectLearnModuleSummaryRecord> modules = new ArrayList<>(existingModules);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                modules.sort(Comparator.comparingInt(ConnectLearnModuleSummaryRecord::getModuleIndex));
            } else {
                Collections.sort(
                        modules, new Comparator<>() {
                            @Override
                            public int compare(
                                    ConnectLearnModuleSummaryRecord o1,
                                    ConnectLearnModuleSummaryRecord o2
                            ) {
                                return Integer.compare(o1.getModuleIndex(), o2.getModuleIndex());
                            }
                        }
                );
            }

            if (job.getLearnAppInfo() != null) {
                job.getLearnAppInfo().setLearnModules(modules);
            }

            //Retrieve payment units
            job.setPaymentUnits(paymentUnitStorage.getRecordsForValues(
                    new String[]{ConnectPaymentUnitRecord.META_JOB_UUID},
                    new Object[]{job.getJobUUID()}
            ));

            //Retrieve related data
            job.setDeliveries(getDeliveries(job.getJobUUID(), deliveryStorage));
            job.setPayments(getPayments(job.getJobUUID(), paymentStorage));
            job.setLearnings(getLearnings(job.getJobUUID(), learningStorage));
            job.setAssessments(getAssessments(job.getJobUUID(), assessmentStorage));
        }
    }

    public static void storeDeliveries(
            Context context,
            List<ConnectJobDeliveryRecord> deliveries,
            String jobUUID,
            boolean pruneMissing
    ) {
        SqlStorage<ConnectJobDeliveryRecord> storage = ConnectDatabaseHelper.getConnectStorage(
                ConnectJobDeliveryRecord.class
        );

        List<ConnectJobDeliveryRecord> existingDeliveries = getDeliveries(
                jobUUID,
                storage
        );

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

            storeDeliveryFlags(incomingRecord.getFlags(), incomingRecord.getDeliveryId());
        }
    }

    public static void storeDeliveryFlags(
            List<ConnectJobDeliveryFlagRecord> flags,
            int deliveryId
    ) {
        SqlStorage<ConnectJobDeliveryFlagRecord> storage = ConnectDatabaseHelper.getConnectStorage(
                ConnectJobDeliveryFlagRecord.class
        );
        ConnectDatabaseHelper.connectDatabase.beginTransaction();
        try {
            storage.removeAll(storage.getIDsForValues(
                    new String[]{ConnectJobDeliveryFlagRecord.META_DELIVERY_ID},
                    new Object[]{deliveryId}
            ));

            for (ConnectJobDeliveryFlagRecord incomingRecord : flags) {
                storage.write(incomingRecord);
            }
            ConnectDatabaseHelper.connectDatabase.setTransactionSuccessful();
        } finally {
            ConnectDatabaseHelper.connectDatabase.endTransaction();
        }
    }

    public static void storePayment(ConnectJobPaymentRecord payment) {
        SqlStorage<ConnectJobPaymentRecord> storage = ConnectDatabaseHelper.getConnectStorage(
                ConnectJobPaymentRecord.class
        );
        storage.write(payment);
    }

    public static void storePayments(
            Context context,
            List<ConnectJobPaymentRecord> payments,
            String jobUUID,
            boolean pruneMissing
    ) {
        SqlStorage<ConnectJobPaymentRecord> storage = ConnectDatabaseHelper.getConnectStorage(
                ConnectJobPaymentRecord.class
        );

        List<ConnectJobPaymentRecord> existingList = getPayments(jobUUID, storage);
        Set<String> matchedIncomingIds = new HashSet<>();

        //Delete payments that are no longer available
        Vector<Integer> recordIdsToDelete = new Vector<>();
        for (ConnectJobPaymentRecord existing : existingList) {
            boolean stillExists = false;
            for (ConnectJobPaymentRecord incoming : payments) {
                if (existing.getPaymentUUID().equals(incoming.getPaymentUUID())) {
                    incoming.setID(existing.getID());
                    stillExists = true;
                    matchedIncomingIds.add(incoming.getPaymentUUID());
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
        boolean newPaymentReceived = false;
        for (ConnectJobPaymentRecord incomingRecord : payments) {
            storage.write(incomingRecord);

            if (!matchedIncomingIds.contains(incomingRecord.getPaymentUUID())) {
                newPaymentReceived = true;
            }
        }

        if (newPaymentReceived) {
            getJobPreferences(jobUUID).resetPaymentConfirmationHiddenSinceTime();
        }
    }

    public static List<ConnectJobDeliveryRecord> getDeliveries(
            String jobUUID,
            SqlStorage<ConnectJobDeliveryRecord> deliveryStorage
    ) {
        if (deliveryStorage == null) {
            deliveryStorage = ConnectDatabaseHelper.getConnectStorage(
                    ConnectJobDeliveryRecord.class
            );
        }

        Vector<ConnectJobDeliveryRecord> deliveries = deliveryStorage.getRecordsForValues(
                new String[]{ConnectJobDeliveryRecord.META_JOB_UUID},
                new Object[]{jobUUID}
        );

        return new ArrayList<>(deliveries);
    }

    public static List<ConnectJobPaymentRecord> getPayments(
            String jobUUID,
            SqlStorage<ConnectJobPaymentRecord> paymentStorage
    ) {
        if (paymentStorage == null) {
            paymentStorage = ConnectDatabaseHelper.getConnectStorage(
                    ConnectJobPaymentRecord.class
            );
        }

        Vector<ConnectJobPaymentRecord> payments = paymentStorage.getRecordsForValues(
                new String[]{ConnectJobPaymentRecord.META_JOB_UUID},
                new Object[]{jobUUID}
        );

        return new ArrayList<>(payments);
    }

    public static List<ConnectJobLearningRecord> getLearnings(
            String jobUUID,
            SqlStorage<ConnectJobLearningRecord> learningStorage
    ) {
        if (learningStorage == null) {
            learningStorage = ConnectDatabaseHelper.getConnectStorage(
                    ConnectJobLearningRecord.class
            );
        }

        Vector<ConnectJobLearningRecord> learnings = learningStorage.getRecordsForValues(
                new String[]{ConnectJobLearningRecord.META_JOB_UUID},
                new Object[]{jobUUID}
        );

        return new ArrayList<>(learnings);
    }

    public static List<ConnectJobAssessmentRecord> getAssessments(
            String jobUUID,
            SqlStorage<ConnectJobAssessmentRecord> assessmentStorage
    ) {
        if (assessmentStorage == null) {
            assessmentStorage = ConnectDatabaseHelper.getConnectStorage(
                    ConnectJobAssessmentRecord.class
            );
        }

        Vector<ConnectJobAssessmentRecord> assessments = assessmentStorage.getRecordsForValues(
                new String[]{ConnectJobAssessmentRecord.META_JOB_UUID},
                new Object[]{jobUUID}
        );

        return new ArrayList<>(assessments);
    }

    public static void storeAssessments(
            Context context,
            List<ConnectJobAssessmentRecord> assessments,
            String jobUUID,
            boolean pruneMissing
    ) {
        SqlStorage<ConnectJobAssessmentRecord> storage = ConnectDatabaseHelper.getConnectStorage(
                ConnectJobAssessmentRecord.class
        );

        List<ConnectJobAssessmentRecord> existingList = getAssessments(jobUUID, storage);

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
        SqlStorage<ConnectJobRecord> jobStorage = ConnectDatabaseHelper.getConnectStorage(
                ConnectJobRecord.class
        );

        job.setLastLearnUpdate(new Date());

        //Check for existing DB ID
        Vector<ConnectJobRecord> existingJobs =
                jobStorage.getRecordsForValues(
                        new String[]{ConnectJobRecord.META_JOB_UUID},
                        new Object[]{job.getJobUUID()}
                );

        if (existingJobs.size() > 0) {
            ConnectJobRecord existing = existingJobs.get(0);
            existing.setCompletedLearningModules(job.getCompletedLearningModules());
            existing.setLastUpdate(new Date());
            jobStorage.write(existing);

            //Also update learning and assessment records
            storeLearningRecords(context, job.getLearnings(), job.getJobUUID(), true);
            storeAssessments(context, job.getAssessments(), job.getJobUUID(), true);
        }
    }

    public static void storeLearningRecords(
            Context context,
            List<ConnectJobLearningRecord> learnings,
            String jobUUID,
            boolean pruneMissing
    ) {
        SqlStorage<ConnectJobLearningRecord> storage = ConnectDatabaseHelper.getConnectStorage(
                ConnectJobLearningRecord.class
        );

        List<ConnectJobLearningRecord> existingList = getLearnings(jobUUID, storage);

        //Delete records that are no longer available
        Vector<Integer> recordIdsToDelete = new Vector<>();
        for (ConnectJobLearningRecord existing : existingList) {
            boolean stillExists = false;
            for (ConnectJobLearningRecord incoming : learnings) {
                if (existing.getModuleId() == incoming.getModuleId()
                        && existing.getDate().equals(incoming.getDate())) {
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

    public static ConnectAppRecord getAppRecord(String appId) {
        if (PersonalIdManager.getInstance().isloggedIn()) {
            Vector<ConnectAppRecord> records = ConnectDatabaseHelper.getConnectStorage(
                    ConnectAppRecord.class
            ).getRecordsForValues(
                    new String[]{ConnectAppRecord.META_APP_ID},
                    new Object[]{appId}
            );
            return records.isEmpty() ? null : records.firstElement();
        }
        return null;
    }

    /**
     * Returns the CommCare appId for the given opportunity. Will return the learn appId if the opportunity
     * status is "learn" and delivery appId otherwise.
     */
    public static String getAppIdForOpportunity(
            Context context,
            String opportunityID,
            String opportunityStatus) {
        if (TextUtils.isEmpty(opportunityID)) {
            throw new IllegalArgumentException("opportunityID can't be empty");
        }
        ConnectJobRecord job = getCompositeJob(opportunityID);
        if (job == null) {
            throw new IllegalArgumentException("No Opportunity found for given opportunityID " + opportunityID);
        }
        boolean isLearning = OPPORTUNITY_STATUS_LEARN.equals(opportunityStatus);
        ConnectAppRecord appInfo = isLearning
                ? job.getLearnAppInfo()
                : job.getDeliveryAppInfo();
        return appInfo.getAppId();
    }

    public static List<ConnectJobPaymentRecord> getPaymentsSortedByDate(ConnectJobRecord job) {
        List<ConnectJobPaymentRecord> payments = job.getPayments();
        Collections.sort(
                payments,
                (payment1, payment2) ->
                        payment2.getDate().compareTo(payment1.getDate())
        );

        return payments;
    }

    public static boolean isExpiryDateUnderFiveDays(Date expiryDate) {
        Calendar expiry = Calendar.getInstance();
        expiry.setTime(expiryDate);
        CalendarUtils.toMidnight(expiry);
        Calendar today = Calendar.getInstance();
        CalendarUtils.toMidnight(today);
        Calendar upperBound = (Calendar) today.clone();
        upperBound.add(Calendar.DAY_OF_YEAR, 5);
        return !expiry.before(today) && !expiry.after(upperBound);
    }

    public static ConnectJobRecord getJobForSeatedApp(Context context) {
        String appId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        ConnectAppRecord appRecord = getAppRecord(appId);
        if (appRecord == null) {
            return null;
        }
        return getCompositeJob(appRecord.getJobUUID());
    }

    public static boolean shouldShowJobStatus(Context context, String appId) {
        ConnectAppRecord record = getAppRecord(appId);
        if (record == null) {
            return false;
        }
        ConnectJobRecord job = getJobForApp(context, appId);
        if (job == null) {
            return false;
        }
        // Only time not to show is when we're in learn app but job is in delivery state
        return !record.getIsLearning() || job.getStatus() != ConnectJobRecord.STATUS_DELIVERING;
    }

    public static String resolveGenericOpportunityDestination(
            String currentAction,
            ConnectJobRecord job,
            String paymentUuid
    ) {
        if (!CCC_GENERIC_OPPORTUNITY.equals(currentAction) || job == null) {
            return currentAction;
        }
        int status = job.getStatus();
        if (status == ConnectJobRecord.STATUS_DELIVERING) {
            return (paymentUuid != null && !paymentUuid.isEmpty())
                    ? CCC_DEST_PAYMENTS
                    : CCC_DEST_DELIVERY_PROGRESS;
        } else if (status == ConnectJobRecord.STATUS_LEARNING) {
            return CCC_DEST_LEARN_PROGRESS;
        } else if (status == ConnectJobRecord.STATUS_AVAILABLE
                || status == ConnectJobRecord.STATUS_AVAILABLE_NEW) {
            return CCC_DEST_OPPORTUNITY_SUMMARY_PAGE;
        } else {
            return currentAction;
        }
    }
}
