package org.commcare.android.database.connect.models;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.commcare.CommCareApplication;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.dalvik.R;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.commcare.utils.CrashUtil;
import org.commcare.utils.JsonExtensions;
import org.javarosa.core.model.utils.DateUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

/**
 * Data class for holding info related to a Connect job
 *
 * @author dviggiano
 */
@Table(ConnectJobRecord.STORAGE_KEY)
public class ConnectJobRecord extends Persisted implements Serializable {
    /**
     * Name of database that stores Connect jobs/opportunities
     */
    public static final String STORAGE_KEY = "connect_jobs";

    public static final int STATUS_AVAILABLE_NEW = 1;
    public static final int STATUS_AVAILABLE = 2;
    public static final int STATUS_LEARNING = 3;
    public static final int STATUS_DELIVERING = 4;
    public static final int STATUS_ALL_JOBS = 5;

    public static final String META_JOB_ID = "id";
    public static final String META_NAME = "name";
    public static final String META_DESCRIPTION = "description";
    public static final String META_ORGANIZATION = "organization";
    public static final String META_END_DATE = "end_date";
    public static final String META_MAX_VISITS_PER_USER = "max_visits_per_user";
    public static final String META_MAX_DAILY_VISITS = "daily_max_visits_per_user";
    public static final String META_BUDGET_PER_VISIT = "budget_per_visit";
    public static final String META_BUDGET_TOTAL = "total_budget";
    public static final String META_LAST_WORKED_DATE = "last_worked";
    public static final String META_STATUS = "status";
    public static final String META_LEARN_MODULES = "total_modules";
    public static final String META_COMPLETED_MODULES = "completed_modules";

    public static final String META_LEARN_PROGRESS = "learn_progress";
    public static final String META_DELIVERY_PROGRESS = "deliver_progress";
    public static final String META_LEARN_APP = "learn_app";
    public static final String META_DELIVER_APP = "deliver_app";
    public static final String META_CLAIM = "claim";
    public static final String META_CLAIM_DATE = "date_claimed";
    public static final String META_MAX_PAYMENTS = "max_payments";
    public static final String META_CURRENCY = "currency";
    public static final String META_ACCRUED = "payment_accrued";
    public static final String META_SHORT_DESCRIPTION = "short_description";
    public static final String META_START_DATE = "start_date";
    public static final String META_DAILY_START_TIME = "form_submission_start";
    public static final String META_DAILY_FINISH_TIME = "form_submission_end";
    public static final String META_IS_ACTIVE = "is_active";
    public static final String META_PAYMENT_UNITS = "payment_units";
    public static final String META_PAYMENT_UNIT = "payment_unit";
    public static final String META_MAX_VISITS = "max_visits";

    private static final String WORKING_HOURS_SOURCE_FORMAT = "HH:mm:ss";
    private static final String WORKING_HOURS_TARGET_FORMAT = "h:mm a";
    private static final String WORKING_HOURS_PATTERN = "%s - %s";

    public static final String META_USER_SUSPENDED = "is_user_suspended";

    public static final String META_JOB_UUID = "opportunity_id";

    @Persisting(1)
    @MetaField(META_JOB_ID)
    private int jobId;
    @Persisting(2)
    @MetaField(META_NAME)
    private String title;
    @Persisting(3)
    @MetaField(META_DESCRIPTION)
    private String description;
    @Persisting(4)
    @MetaField(META_ORGANIZATION)
    private String organization;
    @Persisting(5)
    @MetaField(META_END_DATE)
    private Date projectEndDate;
    @Persisting(6)
    @MetaField(META_BUDGET_PER_VISIT)
    private int budgetPerVisit;
    @Persisting(7)
    @MetaField(META_BUDGET_TOTAL)
    private int totalBudget;
    @Persisting(8)
    @MetaField(META_MAX_VISITS_PER_USER)
    private int maxVisits;
    @Persisting(9)
    @MetaField(META_MAX_DAILY_VISITS)
    private int maxDailyVisits;
    @Persisting(10)
    @MetaField(META_DELIVERY_PROGRESS)
    private int completedVisits;
    @Persisting(11)
    @MetaField(META_LAST_WORKED_DATE)
    private Date lastWorkedDate;
    @Persisting(12)
    @MetaField(META_STATUS)
    private int status;
    @Persisting(13)
    @MetaField(META_LEARN_MODULES)
    private int numLearningModules;
    @Persisting(14)
    @MetaField(META_COMPLETED_MODULES)
    private int learningModulesCompleted;
    @Persisting(15)
    @MetaField(META_CURRENCY)
    private String currency;
    @Persisting(16)
    @MetaField(META_ACCRUED)
    private String paymentAccrued;
    @Persisting(17)
    @MetaField(META_SHORT_DESCRIPTION)
    private String shortDescription;
    @Persisting(18)
    private Date lastUpdate;
    @Persisting(19)
    private Date lastLearnUpdate;
    @Persisting(20)
    private Date lastDeliveryUpdate;
    @Persisting(21)
    @MetaField(META_CLAIM_DATE)
    private Date dateClaimed;
    @Persisting(22)
    @MetaField(META_START_DATE)
    private Date projectStartDate;
    @Persisting(23)
    @MetaField(META_IS_ACTIVE)
    private boolean isActive;

    @Persisting(24)
    @MetaField(META_USER_SUSPENDED)
    private boolean isUserSuspended;

    @Persisting(25)
    @MetaField(META_DAILY_START_TIME)
    private String dailyStartTime;

    @Persisting(26)
    @MetaField(META_DAILY_FINISH_TIME)
    private String dailyFinishTime;

    @Persisting(27)
    @MetaField(META_JOB_UUID)
    private String jobUUID;

    private List<ConnectJobDeliveryRecord> deliveries;
    private List<ConnectJobPaymentRecord> payments;
    private List<ConnectJobLearningRecord> learnings;
    private List<ConnectJobAssessmentRecord> assessments;
    private ConnectAppRecord learnAppInfo;
    private ConnectAppRecord deliveryAppInfo;
    private List<ConnectPaymentUnitRecord> paymentUnits;
    static Date startDate = new Date();

    private boolean claimed;

    public ConnectJobRecord() {
        lastUpdate = new Date();
        lastLearnUpdate = new Date();
        dateClaimed = new Date();
        lastDeliveryUpdate = new Date();
        lastWorkedDate = new Date();
        dailyStartTime = "";
        dailyFinishTime = "";
    }

    public static ConnectJobRecord corruptJobFromJson(JSONObject json) throws JSONException {
        ConnectJobRecord job = new ConnectJobRecord();
        job.title = json.has(META_NAME) ? json.getString(META_NAME) : "";
        job.description = json.has(META_DESCRIPTION) ? json.getString(META_DESCRIPTION) : "";
        job.organization = json.has(META_ORGANIZATION) ? json.getString(META_ORGANIZATION) : "";
        return job;
    }

    public static ConnectJobRecord fromJson(JSONObject json) throws JSONException {
        ConnectJobRecord job = new ConnectJobRecord();
        job.jobId = json.getInt(META_JOB_ID);   //  This will be eventually removed
        job.jobUUID = json.getString(META_JOB_UUID);
        migrateOtherModelsForUUID(job.jobId, job.jobUUID);
        job.title = json.getString(META_NAME);
        job.description = json.getString(META_DESCRIPTION);
        job.organization = json.getString(META_ORGANIZATION);
        job.projectEndDate = DateUtils.parseDate(json.getString(META_END_DATE));
        job.projectStartDate = DateUtils.parseDate(json.getString(META_START_DATE));
        if (job.projectStartDate != null && job.projectStartDate.after(startDate)) {
            startDate = job.projectStartDate;
        }
        job.maxVisits = json.getInt(META_MAX_VISITS_PER_USER);
        job.maxDailyVisits = json.getInt(META_MAX_DAILY_VISITS);
        job.budgetPerVisit = json.getInt(META_BUDGET_PER_VISIT);
        String budgetPerUserKey = "budget_per_user";
        job.totalBudget = json.getInt(budgetPerUserKey);
        job.currency = json.getString(META_CURRENCY);
        job.shortDescription = json.getString(META_SHORT_DESCRIPTION);
        job.paymentAccrued = "";
        job.deliveries = new ArrayList<>();
        job.payments = new ArrayList<>();
        job.learnings = new ArrayList<>();
        job.assessments = new ArrayList<>();
        job.completedVisits = json.getInt(META_DELIVERY_PROGRESS);

        job.claimed = json.has(META_CLAIM) && !json.isNull(META_CLAIM);

        job.isActive = json.optBoolean(META_IS_ACTIVE, true);

        job.isUserSuspended = json.optBoolean(META_USER_SUSPENDED, false);

        //verification_flags -> {"form_submission_start":"07:30:00","form_submission_end":"18:45:00"}
        String flagsKey = "verification_flags";
        JSONObject flags = json.has(flagsKey) && !json.isNull(flagsKey) ? json.getJSONObject(flagsKey) : null;
        if (flags != null) {
            job.dailyStartTime = JsonExtensions.optStringSafe(flags, META_DAILY_START_TIME, "");
            job.dailyFinishTime = JsonExtensions.optStringSafe(flags, META_DAILY_FINISH_TIME, "");
        }

        JSONArray unitsJson = json.getJSONArray(META_PAYMENT_UNITS);
        job.paymentUnits = new ArrayList<>();
        for (int i = 0; i < unitsJson.length(); i++) {
            ConnectPaymentUnitRecord payment = ConnectPaymentUnitRecord.fromJson(unitsJson.getJSONObject(i), job);
            if (payment != null) {
                job.paymentUnits.add(payment);
            }
        }

        if (job.claimed) {
            JSONObject claim = json.getJSONObject(META_CLAIM);

            if (claim.has(META_MAX_PAYMENTS)) {
                job.maxVisits = claim.getInt(META_MAX_PAYMENTS);
            }

            if (claim.has(META_END_DATE)) {
                job.projectEndDate = DateUtils.parseDate(claim.getString(META_END_DATE));
            }

            if (claim.has(META_CLAIM_DATE)) {
                job.dateClaimed = DateUtils.parseDate(claim.getString(META_CLAIM_DATE));
            }

            if (claim.has(META_PAYMENT_UNITS)) {
                //Update payment units
                JSONArray unitsArray = claim.getJSONArray(META_PAYMENT_UNITS);
                for (int i = 0; i < unitsArray.length(); i++) {
                    JSONObject unitObj = unitsArray.getJSONObject(i);
                    int unitId = unitObj.getInt(META_PAYMENT_UNIT);
                    for (int j = 0; j < job.paymentUnits.size(); j++) {
                        if (job.paymentUnits.get(j).getUnitId() == unitId) {
                            int newMax = unitObj.getInt(META_MAX_VISITS);
                            job.paymentUnits.get(j).setMaxTotal(newMax);
                            break;
                        }
                    }
                }
            }
        }

        JSONObject learning = json.getJSONObject(META_LEARN_PROGRESS);
        job.numLearningModules = learning.getInt(META_LEARN_MODULES);
        job.learningModulesCompleted = learning.getInt(META_COMPLETED_MODULES);

        job.learnAppInfo = ConnectAppRecord.fromJson(json.getJSONObject(META_LEARN_APP), job, true);
        job.deliveryAppInfo = ConnectAppRecord.fromJson(json.getJSONObject(META_DELIVER_APP), job, false);

        job.status = STATUS_AVAILABLE;
        if (job.getLearningCompletePercentage() > 0) {
            job.status = STATUS_LEARNING;
            if (job.claimed) {
                job.status = STATUS_DELIVERING;
            }
        }

        return job;
    }

    /**
     * UUIDs are defaulted to ID value. Whenever the application has a new valid UUID for an opportunity from
     * the server response, this function will upgrade all other models from the default UUID value to the new
     * valid UUID. This will ensure that all Connect models are in sync for UUID values, and thus it will reduce
     * the error happening due to mismatched UUID values. Also, this makes the code less complex, as it has to now
     * check for UUID only and can ignore ID, which is our long-term  goal
     * @param jobId
     * @param jobUUID
     */
    private static void migrateOtherModelsForUUID(int jobId, String jobUUID) {
        if (jobId != -1 && !TextUtils.isEmpty(jobUUID)) {

            SqlStorage<ConnectJobRecord> connectJobStorage = ConnectDatabaseHelper.getConnectStorage(CommCareApplication.instance(), ConnectJobRecord.class);
            SqlStorage<ConnectAppRecord> appInfoStorage = ConnectDatabaseHelper.getConnectStorage(CommCareApplication.instance(), ConnectAppRecord.class);
            SqlStorage<ConnectLearnModuleSummaryRecord> moduleStorage = ConnectDatabaseHelper.getConnectStorage(CommCareApplication.instance(), ConnectLearnModuleSummaryRecord.class);
            SqlStorage<ConnectJobDeliveryRecord> deliveryStorage = ConnectDatabaseHelper.getConnectStorage(CommCareApplication.instance(), ConnectJobDeliveryRecord.class);
            SqlStorage<ConnectJobPaymentRecord> paymentStorage = ConnectDatabaseHelper.getConnectStorage(CommCareApplication.instance(), ConnectJobPaymentRecord.class);
            SqlStorage<ConnectJobLearningRecord> learningStorage = ConnectDatabaseHelper.getConnectStorage(CommCareApplication.instance(), ConnectJobLearningRecord.class);
            SqlStorage<ConnectJobAssessmentRecord> assessmentStorage = ConnectDatabaseHelper.getConnectStorage(CommCareApplication.instance(), ConnectJobAssessmentRecord.class);
            SqlStorage<ConnectPaymentUnitRecord> paymentUnitStorage = ConnectDatabaseHelper.getConnectStorage(CommCareApplication.instance(), ConnectPaymentUnitRecord.class);
            SqlStorage<PushNotificationRecord> notificationStorage = ConnectDatabaseHelper.getConnectStorage(CommCareApplication.instance(), PushNotificationRecord.class);

            //  migrate ConnectJobRecord
            Vector<ConnectJobRecord> connectJobRecords = connectJobStorage.getRecordsForValues(
                    new String[]{ConnectJobRecord.META_JOB_ID},
                    new Object[]{jobId});
            for (ConnectJobRecord connectJobRecord : connectJobRecords) {
                connectJobRecord.setJobUUID(jobUUID);
                connectJobStorage.write(connectJobRecord);
            }

            // migrate ConnectAppRecord
            Vector<ConnectAppRecord> existingAppInfos = appInfoStorage.getRecordsForValues(
                    new String[]{ConnectAppRecord.META_JOB_ID},
                    new Object[]{jobId});
            for (ConnectAppRecord connectAppRecord : existingAppInfos) {
                connectAppRecord.setJobUUID(jobUUID);
                appInfoStorage.write(connectAppRecord);
            }

            // migrate ConnectLearnModuleSummaryRecord
            Vector<ConnectLearnModuleSummaryRecord> connectLearnModuleSummaryRecords = moduleStorage.getRecordsForValues(
                    new String[]{ConnectAppRecord.META_JOB_ID},
                    new Object[]{jobId});
            for (ConnectLearnModuleSummaryRecord connectLearnModuleSummaryRecord : connectLearnModuleSummaryRecords) {
                connectLearnModuleSummaryRecord.setJobUUID(jobUUID);
                moduleStorage.write(connectLearnModuleSummaryRecord);
            }

            // migrate ConnectJobDeliveryRecord
            Vector<ConnectJobDeliveryRecord> deliveries = deliveryStorage.getRecordsForValues(
                    new String[]{ConnectJobDeliveryRecord.META_JOB_ID},
                    new Object[]{jobId});
            for (ConnectJobDeliveryRecord deliveryRecord : deliveries) {
                deliveryRecord.setJobUUID(jobUUID);
                deliveryStorage.write(deliveryRecord);
            }

            // migrate ConnectJobLearningRecord
            Vector<ConnectJobLearningRecord> learnings = learningStorage.getRecordsForValues(
                    new String[]{ConnectJobLearningRecord.META_JOB_ID},
                    new Object[]{jobId});
            for (ConnectJobLearningRecord learningRecord : learnings) {
                learningRecord.setJobUUID(jobUUID);
                learningStorage.write(learningRecord);
            }

            // migrate ConnectJobPaymentRecord
            Vector<ConnectJobPaymentRecord> payments = paymentStorage.getRecordsForValues(
                    new String[]{ConnectJobPaymentRecord.META_JOB_ID},
                    new Object[]{jobId});
            for (ConnectJobPaymentRecord paymentRecord : payments) {
                paymentRecord.setJobUUID(jobUUID);
                paymentStorage.write(paymentRecord);
            }

            // migrate ConnectJobAssessmentRecord
            Vector<ConnectJobAssessmentRecord> assessments = assessmentStorage.getRecordsForValues(
                    new String[]{ConnectJobAssessmentRecord.META_JOB_ID},
                    new Object[]{jobId});
            for (ConnectJobAssessmentRecord assessmentRecord : assessments) {
                assessmentRecord.setJobUUID(jobUUID);
                assessmentStorage.write(assessmentRecord);
            }

            // migrate ConnectPaymentUnitRecord
            Vector<ConnectPaymentUnitRecord> paymentUnitRecords = paymentUnitStorage.getRecordsForValues(
                    new String[]{ConnectPaymentUnitRecord.META_JOB_ID},
                    new Object[]{jobId});
            for (ConnectPaymentUnitRecord paymentUnitRecord : paymentUnitRecords) {
                paymentUnitRecord.setJobUUID(jobUUID);
                paymentUnitStorage.write(paymentUnitRecord);
            }

            // migrate PushNotificationRecord
            Vector<PushNotificationRecord> pushNotificationRecords = notificationStorage.getRecordsForValues(
                    new String[]{PushNotificationRecord.META_OPPORTUNITY_ID},
                    new Object[]{jobId});
            for (PushNotificationRecord pushNotificationRecord : pushNotificationRecords) {
                pushNotificationRecord.setOpportunityUUID(jobUUID);
                notificationStorage.write(pushNotificationRecord);
            }
        }
    }

    public boolean isFinished() {
        return !isActive || getDaysRemaining() <= 0;
    }

    public int getJobId() {
        return jobId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getCompletedVisits() {
        return completedVisits;
    }

    public int getMaxVisits() {
        return maxVisits;
    }

    public void setMaxVisits(int max) {
        maxVisits = max;
    }

    public int getMaxDailyVisits() {
        return maxDailyVisits;
    }

    public Date getProjectStartDate() {
        return projectStartDate;
    }

    public Date getProjectEndDate() {
        return projectEndDate;
    }

    public void setProjectEndDate(Date date) {
        projectEndDate = date;
    }

    public int getPaymentAccrued() {
        return paymentAccrued == null || paymentAccrued.isEmpty() ? 0 : Integer.parseInt(paymentAccrued);
    }

    public int getLearningPercentComplete() {
        return numLearningModules > 0 ? (100 * learningModulesCompleted / numLearningModules) : 100;
    }

    public String getDailyStartTime() {
        return dailyStartTime;
    }

    public String getDailyFinishTime() {
        return dailyFinishTime;
    }

    public void setPaymentAccrued(int paymentAccrued) {
        this.paymentAccrued = Integer.toString(paymentAccrued);
    }

    public String getCurrency() {
        return currency;
    }

    public int getNumLearningModules() {
        return numLearningModules;
    }

    public int getCompletedLearningModules() {
        return learningModulesCompleted;
    }

    public void setCompletedLearningModules(int numCompleted) {
        this.learningModulesCompleted = numCompleted;
    }

    public ConnectAppRecord getLearnAppInfo() {
        return learnAppInfo;
    }

    public void setLearnAppInfo(ConnectAppRecord appInfo) {
        this.learnAppInfo = appInfo;
    }

    public ConnectAppRecord getDeliveryAppInfo() {
        return deliveryAppInfo;
    }

    public void setDeliveryAppInfo(ConnectAppRecord appInfo) {
        this.deliveryAppInfo = appInfo;
    }

    public List<ConnectJobDeliveryRecord> getDeliveries() {
        return deliveries;
    }

    public void setDeliveries(List<ConnectJobDeliveryRecord> deliveries) {
        this.deliveries = deliveries;
        if (!deliveries.isEmpty()) {
            completedVisits = deliveries.size();
        }
    }

    public List<ConnectJobPaymentRecord> getPayments() {
        return payments;
    }

    public void setPayments(List<ConnectJobPaymentRecord> payments) {
        this.payments = payments;
    }

    public int getPaymentTotal() {
        int total = 0;
        for (ConnectJobPaymentRecord payment : getPayments()) {
            total += Integer.parseInt(payment.getAmount());
        }

        return total;
    }

    public List<ConnectJobLearningRecord> getLearnings() {
        return learnings;
    }

    public void setLearnings(List<ConnectJobLearningRecord> learnings) {
        this.learnings = learnings;
    }

    public List<ConnectJobAssessmentRecord> getAssessments() {
        return assessments;
    }

    public void setAssessments(List<ConnectJobAssessmentRecord> assessments) {
        this.assessments = assessments;
    }

    public void setLastUpdate(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public int getDaysRemaining() {
        long millis = projectEndDate.getTime() - (startDate).getTime();
        //End date has 00:00 time, but project is valid until midnight
        //So add just under one day to the difference
        millis += 86399999; //one ms less than 24 hours
        int days = (int)TimeUnit.MILLISECONDS.toDays(millis);
        //Now plus 1 so we report i.e. 1 day remaining on the last day
        //But for any negative difference, we return 0
        return millis >= 0 ? (days + 1) : 0;
    }

    public String getWorkingHours() {
        String dailyStart = getDailyStartTime();
        String dailyFinish = getDailyFinishTime();

        if (dailyStart == null || dailyFinish == null || dailyStart.isEmpty() || dailyFinish.isEmpty()) {
            return null;
        }

        try {
            SimpleDateFormat utcFormat = new SimpleDateFormat(WORKING_HOURS_SOURCE_FORMAT, Locale.getDefault());
            utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date startTime = utcFormat.parse(dailyStart);
            Date endTime = utcFormat.parse(dailyFinish);
            SimpleDateFormat localFormat = new SimpleDateFormat(WORKING_HOURS_TARGET_FORMAT, Locale.getDefault());

            String startTimeLocal = localFormat.format(startTime);
            String endTimeLocal = localFormat.format(endTime);

            return String.format(WORKING_HOURS_PATTERN, startTimeLocal, endTimeLocal);
        } catch (ParseException e) {
            CrashUtil.reportException(new Exception("Error parsing working hours", e));
            return null;
        }
    }

    public int getMaxPossibleVisits() {
        return maxVisits;
    }

    public int getLearningCompletePercentage() {
        int numLearning = getNumLearningModules();
        return numLearning > 0 ? (100 * getCompletedLearningModules() / numLearning) : 100;
    }

    public int getDeliveryProgressPercentage() {
        int completed = getCompletedVisits();
        int total = getMaxVisits();
        return total > 0 ? (100 * completed / total) : 100;
    }

    public boolean attemptedAssessment() {
        return getLearningCompletePercentage() >= 100 && assessments != null && !assessments.isEmpty();
    }

    public boolean passedAssessment() {
        return status == STATUS_DELIVERING || (getLearningCompletePercentage() >= 100 && getAssessmentScore() >= getLearnAppInfo().getPassingScore());
    }

    public int getAssessmentScore() {
        int mostRecentFailingScore = 0;
        int firstPassingScore = -1;

        if (assessments != null) {
            for (ConnectJobAssessmentRecord record : assessments) {
                int score = record.getScore();
                if (score >= record.getPassingScore()) {
                    if (firstPassingScore == -1) {
                        firstPassingScore = score;
                    }
                } else {
                    mostRecentFailingScore = score;
                }
            }
        }

        if (firstPassingScore != -1) {
            return firstPassingScore;
        } else {
            return mostRecentFailingScore;
        }
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }

    public void setLastLearnUpdate(Date date) {
        lastLearnUpdate = date;
    }

    public Date getLastDeliveryUpdate() {
        return lastDeliveryUpdate;
    }

    public void setLastDeliveryUpdate(Date date) {
        lastDeliveryUpdate = date;
    }

    public boolean getIsActive() {
        return isActive;
    }

    public void setIsUserSuspended(boolean isUserSuspended) {
        this.isUserSuspended = isUserSuspended;
    }

    public boolean getIsUserSuspended() {
        return isUserSuspended;
    }

    public String getMoneyString(int value) {
        String currency = "";
        if (this.currency != null && !this.currency.isEmpty()) {
            currency = " " + this.currency;
        }

        return String.format(Locale.getDefault(), "%d%s", value, currency);
    }


    public int numberOfDeliveriesToday() {
        int dailyVisitCount = 0;
        Date today = new Date();
        for (ConnectJobDeliveryRecord record : deliveries) {
            if (DateUtils.dateDiff(today, record.getDate()) == 0) {
                dailyVisitCount++;
            }
        }

        return dailyVisitCount;
    }

    public List<ConnectPaymentUnitRecord> getPaymentUnits() {
        return paymentUnits;
    }

    public boolean isMultiPayment() {
        return paymentUnits.size() > 1;
    }


    public HashMap<String, Integer> getDeliveryCountsPerPaymentUnit(boolean todayOnly) {
        HashMap<String, Integer> paymentCounts = new HashMap<>();
        for (int i = 0; i < deliveries.size(); i++) {
            ConnectJobDeliveryRecord delivery = deliveries.get(i);
            if (!todayOnly || DateUtils.dateDiff(new Date(), delivery.getDate()) == 0) {
                int oldCount = 0;
                if (paymentCounts.containsKey(delivery.getSlug())) {
                    oldCount = paymentCounts.get(delivery.getSlug());
                }

                paymentCounts.put(delivery.getSlug(), oldCount + 1);
            }
        }

        return paymentCounts;
    }

    public void setPaymentUnits(List<ConnectPaymentUnitRecord> units) {
        paymentUnits = units;
    }

    public boolean readyToTransitionToDelivery() {
        return status == STATUS_LEARNING && passedAssessment();
    }

    @Nullable
    public String getCardMessageText(Context context) {
        if (isFinished()) {
            return context.getString(R.string.connect_progress_warning_ended);
        } else if (getIsUserSuspended()) {
            return context.getString(R.string.user_suspended);
        } else if (status == STATUS_DELIVERING && getProjectStartDate().after(new Date())) {
            return context.getString(R.string.connect_progress_warning_not_started);
        } else if (readyToTransitionToDelivery()) {
            return context.getString(R.string.connect_progress_ready_for_transition_to_delivery);
        } else if (isMultiPayment()) {
            return getMultiVisitWarnings(context);
        } else if (getDeliveries().size() >= getMaxVisits()) {
            return context.getString(R.string.connect_progress_warning_max_reached_single);
        } else if (numberOfDeliveriesToday() >= getMaxDailyVisits()) {
            return context.getString(R.string.connect_progress_warning_daily_max_reached_single);
        }

        return null;
    }

    @Nullable
    private String getMultiVisitWarnings(Context context) {
        HashMap<String, Integer> total = getDeliveryCountsPerPaymentUnit(false);
        HashMap<String, Integer> today = getDeliveryCountsPerPaymentUnit(true);
        List<String> dailyMaxes = new ArrayList<>();
        List<String> totalMaxes = new ArrayList<>();

        for (ConnectPaymentUnitRecord unit : getPaymentUnits()) {
            String key = String.valueOf(unit.getUnitId());

            int totalCount = total.containsKey(key) ? total.get(key) : 0;
            if (totalCount >= unit.getMaxTotal()) {
                totalMaxes.add(unit.getName());
            } else {
                int todayCount = today.containsKey(key) ? today.get(key) : 0;
                if (todayCount >= unit.getMaxDaily()) {
                    dailyMaxes.add(unit.getName());
                }
            }
        }

        List<String> lines = new ArrayList<>();
        if (!totalMaxes.isEmpty()) {
            lines.add(context.getString(R.string.connect_progress_warning_max_reached_multi,
                    TextUtils.join(", ", totalMaxes)));
        }

        if (!dailyMaxes.isEmpty()) {
            lines.add(context.getString(R.string.connect_progress_warning_daily_max_reached_multi,
                    TextUtils.join(", ", dailyMaxes)));
        }

        return lines.isEmpty() ? null : TextUtils.join("\n", lines);
    }

    public static ConnectJobRecord fromV21(ConnectJobRecordV21 oldRecord) {
        ConnectJobRecord newRecord = new ConnectJobRecord();
        newRecord.jobId = oldRecord.getJobId();
        newRecord.title = oldRecord.getTitle();
        newRecord.description = oldRecord.getDescription();
        newRecord.status = oldRecord.getStatus();
        newRecord.completedVisits = oldRecord.getCompletedVisits();
        newRecord.maxDailyVisits = oldRecord.getMaxDailyVisits();
        newRecord.maxVisits = oldRecord.getMaxVisits();
        newRecord.budgetPerVisit = oldRecord.getBudgetPerVisit();
        newRecord.totalBudget = oldRecord.getTotalBudget();
        newRecord.projectEndDate = oldRecord.getProjectEndDate();
        newRecord.lastWorkedDate = oldRecord.getLastWorkedDate();
        newRecord.organization = oldRecord.getOrganization();
        newRecord.numLearningModules = oldRecord.getNumLearningModules();
        newRecord.learningModulesCompleted = oldRecord.getLearningModulesCompleted();
        newRecord.currency = oldRecord.getCurrency();
        newRecord.paymentAccrued = oldRecord.getPaymentAccrued();
        newRecord.shortDescription = oldRecord.getShortDescription();
        newRecord.lastUpdate = oldRecord.getLastUpdate();
        newRecord.lastLearnUpdate = oldRecord.getLastLearnUpdate();
        newRecord.lastDeliveryUpdate = oldRecord.getLastDeliveryUpdate();
        newRecord.dateClaimed = oldRecord.getDateClaimed();
        newRecord.projectStartDate = oldRecord.getProjectStartDate();
        newRecord.isActive = oldRecord.isActive();
        newRecord.isUserSuspended = oldRecord.isUserSuspended();
        newRecord.dailyStartTime = oldRecord.getDailyStartTime();
        newRecord.dailyFinishTime = oldRecord.getDailyFinishTime();
        newRecord.jobUUID = String.valueOf(oldRecord.getJobId());
        return newRecord;
    }




    public boolean deliveryComplete() {
        return isFinished() || getDeliveries().size() >= getMaxVisits();
    }

    //  getter and setter for kotlin
    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public int getBudgetPerVisit() {
        return budgetPerVisit;
    }

    public void setBudgetPerVisit(int budgetPerVisit) {
        this.budgetPerVisit = budgetPerVisit;
    }

    public int getTotalBudget() {
        return totalBudget;
    }

    public void setTotalBudget(int totalBudget) {
        this.totalBudget = totalBudget;
    }

    public void setMaxDailyVisits(int maxDailyVisits) {
        this.maxDailyVisits = maxDailyVisits;
    }

    public void setCompletedVisits(int completedVisits) {
        this.completedVisits = completedVisits;
    }

    public Date getLastWorkedDate() {
        return lastWorkedDate;
    }

    public void setLastWorkedDate(Date lastWorkedDate) {
        this.lastWorkedDate = lastWorkedDate;
    }

    public void setNumLearningModules(int numLearningModules) {
        this.numLearningModules = numLearningModules;
    }

    public int getLearningModulesCompleted() {
        return learningModulesCompleted;
    }

    public void setLearningModulesCompleted(int learningModulesCompleted) {
        this.learningModulesCompleted = learningModulesCompleted;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setPaymentAccrued(String paymentAccrued) {
        this.paymentAccrued = paymentAccrued;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public Date getLastLearnUpdate() {
        return lastLearnUpdate;
    }

    public Date getDateClaimed() {
        return dateClaimed;
    }

    public void setDateClaimed(Date dateClaimed) {
        this.dateClaimed = dateClaimed;
    }

    public void setProjectStartDate(Date projectStartDate) {
        this.projectStartDate = projectStartDate;
    }

    public void setIsActive(boolean active) {
        isActive = active;
    }

    public void setDailyStartTime(String dailyStartTime) {
        this.dailyStartTime = dailyStartTime;
    }

    public void setDailyFinishTime(String dailyFinishTime) {
        this.dailyFinishTime = dailyFinishTime;
    }

    public String getJobUUID() {
        return jobUUID;
    }

    public void setJobUUID(String modelId) {
        this.jobUUID = modelId;
    }

    public static Date getStartDate() {
        return startDate;
    }

    public static void setStartDate(Date startDate) {
        ConnectJobRecord.startDate = startDate;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }
}
