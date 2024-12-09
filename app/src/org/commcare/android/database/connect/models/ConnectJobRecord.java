package org.commcare.android.database.connect.models;

import android.view.View;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.commcare.utils.CrashUtil;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

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

    public static final String META_USER_SUSPENDED = "is_user_suspended";
    public static final String META_PAYMENT_INFO_REQUIRED = "payment_info_required";

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

    private List<ConnectJobDeliveryRecord> deliveries;
    private List<ConnectJobPaymentRecord> payments;
    private List<ConnectJobLearningRecord> learnings;
    private List<ConnectJobAssessmentRecord> assessments;
    private ConnectAppRecord learnAppInfo;
    private ConnectAppRecord deliveryAppInfo;
    private List<ConnectPaymentUnitRecord> paymentUnits;

    @Persisting(24)
    @MetaField(META_PAYMENT_INFO_REQUIRED)
    private Boolean isPaymentInfoRequired;

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

    public static ConnectJobRecord fromJson(JSONObject json) throws JSONException, ParseException {
        ConnectJobRecord job = new ConnectJobRecord();

        job.jobId = json.getInt(META_JOB_ID);
        job.title = json.has(META_NAME) ? json.getString(META_NAME) : "";
        job.description = json.has(META_DESCRIPTION) ? json.getString(META_DESCRIPTION) : "";
        job.organization = json.has(META_ORGANIZATION) ? json.getString(META_ORGANIZATION) : "";
        job.projectEndDate = json.has(META_END_DATE) ? DateUtils.parseDate(json.getString(META_END_DATE)) : new Date();
        job.projectStartDate = json.has(META_START_DATE) ? DateUtils.parseDate(json.getString(META_START_DATE)) : new Date();
        job.maxVisits = json.has(META_MAX_VISITS_PER_USER) ? json.getInt(META_MAX_VISITS_PER_USER) : -1;
        job.maxDailyVisits = json.has(META_MAX_DAILY_VISITS) ? json.getInt(META_MAX_DAILY_VISITS) : -1;
        job.budgetPerVisit = json.has(META_BUDGET_PER_VISIT) ? json.getInt(META_BUDGET_PER_VISIT) : -1;
        String budgetPerUserKey = "budget_per_user";
        job.totalBudget = json.has(budgetPerUserKey) ? json.getInt(budgetPerUserKey) : -1;
        job.currency = json.has(META_CURRENCY) && !json.isNull(META_CURRENCY) ? json.getString(META_CURRENCY) : "";
        job.shortDescription = json.has(META_SHORT_DESCRIPTION) && !json.isNull(META_SHORT_DESCRIPTION) ?
                json.getString(META_SHORT_DESCRIPTION) : "";

        job.paymentAccrued = "";

        job.deliveries = new ArrayList<>();
        job.payments = new ArrayList<>();
        job.learnings = new ArrayList<>();
        job.assessments = new ArrayList<>();
        job.completedVisits = json.has(META_DELIVERY_PROGRESS) ? json.getInt(META_DELIVERY_PROGRESS) : -1;

        job.claimed = json.has(META_CLAIM) &&!json.isNull(META_CLAIM);

        job.isActive = !json.has(META_IS_ACTIVE) || json.getBoolean(META_IS_ACTIVE);

        job.isUserSuspended = json.has(META_USER_SUSPENDED) && json.getBoolean(META_USER_SUSPENDED);

        //verification_flags -> {"form_submission_start":"07:30:00","form_submission_end":"18:45:00"}
        String flagsKey = "verification_flags";
        JSONObject flags = json.has(flagsKey) && !json.isNull(flagsKey) ? json.getJSONObject(flagsKey) : null;
        if(flags != null) {
            job.dailyStartTime = flags.has(META_DAILY_START_TIME) ? flags.getString(META_DAILY_START_TIME) : "";
            if(job.dailyStartTime.equals("null")) {
                job.dailyStartTime = "";
            }
            job.dailyFinishTime = flags.has(META_DAILY_FINISH_TIME) ? flags.getString(META_DAILY_FINISH_TIME) : "";
            if(job.dailyFinishTime.equals("null")) {
                job.dailyFinishTime = "";
            }
        }

        JSONArray unitsJson = json.getJSONArray(META_PAYMENT_UNITS);
        job.paymentUnits = new ArrayList<>();
        for(int i=0; i<unitsJson.length(); i++) {
            job.paymentUnits.add(ConnectPaymentUnitRecord.fromJson(unitsJson.getJSONObject(i), job.getJobId()));
        }

        if(job.claimed) {
            JSONObject claim = json.getJSONObject(META_CLAIM);

            String key = META_MAX_PAYMENTS;
            if (claim.has(key)) {
                job.maxVisits = claim.getInt(key);
            }

            key = META_END_DATE;
            if (claim.has(key)) {
                job.projectEndDate = DateUtils.parseDate(claim.getString(key));
            }

            key = META_CLAIM_DATE;
            if (claim.has(key)) {
                job.dateClaimed = DateUtils.parseDate(claim.getString(key));
            }

            key = META_PAYMENT_UNITS;
            if (claim.has(key)) {
                //Update payment units
                JSONArray unitsArray = claim.getJSONArray(META_PAYMENT_UNITS);
                for(int i=0; i< unitsArray.length(); i++) {
                    JSONObject unitObj = unitsArray.getJSONObject(i);
                    int unitId = unitObj.getInt(META_PAYMENT_UNIT);
                    for(int j=0; j < job.paymentUnits.size(); j++) {
                        if(job.paymentUnits.get(j).getUnitId() == unitId) {
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

        job.learnAppInfo = ConnectAppRecord.fromJson(json.getJSONObject(META_LEARN_APP), job.jobId, true);
        job.deliveryAppInfo = ConnectAppRecord.fromJson(json.getJSONObject(META_DELIVER_APP), job.jobId, false);

        //In JSON but not in model
        //job.? = json.has(META_DATE_CREATED) ? df.parse(json.getString(META_DATE_CREATED)) : null;
        //job.? = json.has(META_DATE_MODIFIED) ? df.parse(json.getString(META_DATE_MODIFIED)) : null;

        job.status = STATUS_AVAILABLE;
        if(job.getLearningCompletePercentage() > 0) {
            job.status = STATUS_LEARNING;
            if(job.claimed) {
                job.status = STATUS_DELIVERING;
            }
        }

        return job;
    }

    public boolean isFinished() {
        return !isActive || getDaysRemaining() <= 0;
    }

    public int getJobId() { return jobId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getShortDescription() { return shortDescription; }
    public boolean getIsNew() { return status == STATUS_AVAILABLE_NEW; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public int getCompletedVisits() { return completedVisits; }
    public int getMaxVisits() { return maxVisits; }
    public void setMaxVisits(int max) { maxVisits = max; }
    public int getMaxDailyVisits() { return maxDailyVisits; }
    public int getBudgetPerVisit() { return budgetPerVisit; }
    public int getPercentComplete() { return maxVisits > 0 ? 100 * completedVisits / maxVisits : 0; }
    public Date getProjectStartDate() { return projectStartDate; }
    public Date getProjectEndDate() { return projectEndDate; }
    public String getDailyStartTime() { return dailyStartTime; }
    public String getDailyFinishTime() { return dailyFinishTime; }
    public void setProjectEndDate(Date date) { projectEndDate = date; }
    public int getPaymentAccrued() { return paymentAccrued != null && paymentAccrued.length() > 0 ? Integer.parseInt(paymentAccrued) : 0; }
    public void setPaymentAccrued(int paymentAccrued) { this.paymentAccrued = Integer.toString(paymentAccrued); }
    public String getCurrency() { return currency; }
    public int getNumLearningModules() { return numLearningModules; }
    public int getCompletedLearningModules() { return learningModulesCompleted; }
    public int getLearningPercentComplete() {
        return numLearningModules > 0 ? (100 * learningModulesCompleted / numLearningModules) : 100;
    }
    public void setComletedLearningModules(int numCompleted) { this.learningModulesCompleted = numCompleted; }
    public ConnectAppRecord getLearnAppInfo() { return learnAppInfo; }
    public void setLearnAppInfo(ConnectAppRecord appInfo) { this.learnAppInfo = appInfo; }
    public ConnectAppRecord getDeliveryAppInfo() { return deliveryAppInfo; }
    public void setDeliveryAppInfo(ConnectAppRecord appInfo) { this.deliveryAppInfo = appInfo; }
    public List<ConnectJobDeliveryRecord> getDeliveries() { return deliveries; }

    public Boolean getPaymentInfoRequired() {
        return isPaymentInfoRequired;
    }

    public void setPaymentInfoRequired(Boolean paymentInfoRequired) {
        isPaymentInfoRequired = paymentInfoRequired;
    }

    public void setDeliveries(List<ConnectJobDeliveryRecord> deliveries) {
        this.deliveries = deliveries;
        if(deliveries.size() > 0) {
            completedVisits = deliveries.size();
        }
    }
    public List<ConnectJobPaymentRecord> getPayments() { return payments; }
    public void setPayments(List<ConnectJobPaymentRecord> payments) {
        this.payments = payments;
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
    public void setLastUpdate(Date lastUpdate) { this.lastUpdate = lastUpdate; }

    public int getDaysRemaining() {
        Date startDate = new Date();
        if(projectStartDate != null && projectStartDate.after(startDate)) {
            startDate = projectStartDate;
        }
        double millis = projectEndDate.getTime() - (startDate).getTime();
        //Ceiling means we'll get 0 within 24 hours of the end date
        //(since the end date has 00:00 time, but project is valid until midnight)
        int days = (int)Math.ceil(millis / 1000 / 3600 / 24);
        //Now plus 1 so we report i.e. 1 day remaining on the last day
        return days >= 0 ? (days + 1) : 0;
    }

    public String getWorkingHours() {
        String dailyStart = getDailyStartTime();
        String dailyFinish = getDailyFinishTime();

        if (dailyStart.length() == 0 || dailyFinish.length() == 0) {
            return null;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            LocalTime start = LocalTime.parse(dailyStart, formatter);
            LocalTime end = LocalTime.parse(dailyFinish, formatter);
            formatter = DateTimeFormatter.ofPattern("h:mm a");

            return formatter.format(start) + " - " + formatter.format(end);
        } catch(Exception e) {
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

    public boolean attemptedAssessment() {
        return getLearningCompletePercentage() >= 100 && assessments != null && assessments.size() > 0;
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

    public Date getLastUpdate() { return lastUpdate; }

    public Date getLastLearnUpdate() { return lastLearnUpdate; }
    public void setLastLearnUpdate(Date date) { lastLearnUpdate = date; }
    public Date getLastDeliveryUpdate() { return lastDeliveryUpdate; }
    public void setLastDeliveryUpdate(Date date) { lastDeliveryUpdate = date; }
    public String getOrganization() { return organization; }
    public int getTotalBudget() { return totalBudget; }
    public Date getLastWorkedDate() { return lastWorkedDate; }
    public Date getDateClaimed() { return dateClaimed; }
    public boolean getIsActive() { return isActive; }

    public boolean setIsUserSuspended(boolean isUserSuspended) { return this.isUserSuspended=isUserSuspended; }

    public boolean getIsUserSuspended(){
        return isUserSuspended;
    }

    public String getMoneyString(int value) {
        String currency = "";
        if(this.currency != null && this.currency.length() > 0) {
            currency = " " + this.currency;
        }

        return String.format(Locale.getDefault(), "%d%s", value, currency);
    }


    public int numberOfDeliveriesToday() {
        int dailyVisitCount = 0;
        Date today = new Date();
        for (ConnectJobDeliveryRecord record : deliveries) {
            if(sameDay(today, record.getDate())) {
                dailyVisitCount++;
            }
        }

        return dailyVisitCount;
    }

    private static boolean sameDay(Date date1, Date date2) {
        LocalDate dt1 = new LocalDate(date1);
        LocalDate dt2 = new LocalDate(date2);

        return dt1.equals(dt2);
    }

    public List<ConnectPaymentUnitRecord> getPaymentUnits() {
        return paymentUnits;
    }

    public boolean isMultiPayment() {
        return paymentUnits.size() > 1;
    }



    public Hashtable<String, Integer> getDeliveryCountsPerPaymentUnit(boolean todayOnly) {
        Hashtable<String, Integer> paymentCounts = new Hashtable<>();
        for(int i = 0; i < deliveries.size(); i++) {
            ConnectJobDeliveryRecord delivery = deliveries.get(i);
            if(!todayOnly || sameDay(new Date(), delivery.getDate())) {
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

    public static ConnectJobRecord fromV10(ConnectJobRecordV10 oldRecord) {
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
        newRecord.deliveries = new ArrayList<>();
        newRecord.payments = new ArrayList<>();
        newRecord.learnings = new ArrayList<>();
        newRecord.assessments = new ArrayList<>();
        newRecord.paymentUnits = new ArrayList<>();

        newRecord.organization = oldRecord.getOrganization();
        newRecord.numLearningModules = oldRecord.getNumLearningModules();
        newRecord.learningModulesCompleted = oldRecord.getCompletedLearningModules();
        newRecord.currency = oldRecord.getCurrency();
        newRecord.paymentAccrued = Integer.toString(oldRecord.getPaymentAccrued());
        newRecord.shortDescription = oldRecord.getShortDescription();
        newRecord.lastUpdate = oldRecord.getLastUpdate();
        newRecord.lastLearnUpdate = oldRecord.getLastLearnUpdate();
        newRecord.lastDeliveryUpdate = oldRecord.getLastDeliveryUpdate();
        newRecord.dateClaimed = oldRecord.getDateClaimed();
        newRecord.projectStartDate = oldRecord.getProjectStartDate();
        newRecord.isActive = oldRecord.getIsActive();
        newRecord.isUserSuspended= oldRecord.getIsUserSuspended();
        newRecord.dailyStartTime = "";
        newRecord.dailyFinishTime = "";

        return newRecord;
    }
}
