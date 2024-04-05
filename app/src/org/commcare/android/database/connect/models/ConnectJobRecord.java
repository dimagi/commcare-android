package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
    public static final String META_MAX_VISITS = "max_visits_per_user";
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
    @MetaField(META_MAX_VISITS)
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


    private List<ConnectJobDeliveryRecord> deliveries;
    private List<ConnectJobPaymentRecord> payments;
    private List<ConnectJobLearningRecord> learnings;
    private List<ConnectJobAssessmentRecord> assessments;
    private ConnectAppRecord learnAppInfo;
    private ConnectAppRecord deliveryAppInfo;

    private boolean claimed;

    public ConnectJobRecord() {

    }

    public ConnectJobRecord(int jobId, String title, String description, int status,
                            int completedVisits, int maxVisits, int maxDailyVisits, int budgetPerVisit, int totalBudget,
                            Date projectEnd, Date lastWorkedDate,
                            List<ConnectJobDeliveryRecord> deliveries) {
        this.jobId = jobId;
        this.title = title;
        this.description = description;
        this.status = status;
        this.completedVisits = completedVisits;
        this.maxDailyVisits = maxDailyVisits;
        this.maxVisits = maxVisits;
        this.budgetPerVisit = budgetPerVisit;
        this.totalBudget = totalBudget;
        this.projectEndDate = projectEnd;
        this.lastWorkedDate = lastWorkedDate;
        this.deliveries = deliveries;
        this.payments = new ArrayList<>();
        this.learnings = new ArrayList<>();
        this.assessments = new ArrayList<>();
    }

    public static ConnectJobRecord fromJson(JSONObject json) throws JSONException, ParseException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        ConnectJobRecord job = new ConnectJobRecord();

        job.lastUpdate = new Date();
        job.lastLearnUpdate = new Date();
        job.lastDeliveryUpdate = new Date();

        job.jobId = json.getInt(META_JOB_ID);
        job.title = json.has(META_NAME) ? json.getString(META_NAME) : "";
        job.description = json.has(META_DESCRIPTION) ? json.getString(META_DESCRIPTION) : "";
        job.organization = json.has(META_ORGANIZATION) ? json.getString(META_ORGANIZATION) : "";
        job.projectEndDate = json.has(META_END_DATE) ? df.parse(json.getString(META_END_DATE)) : new Date();
        job.maxVisits = json.has(META_MAX_VISITS) ? json.getInt(META_MAX_VISITS) : -1;
        job.maxDailyVisits = json.has(META_MAX_DAILY_VISITS) ? json.getInt(META_MAX_DAILY_VISITS) : -1;
        job.budgetPerVisit = json.has(META_BUDGET_PER_VISIT) ? json.getInt(META_BUDGET_PER_VISIT) : -1;
        job.totalBudget = json.has(META_BUDGET_TOTAL) ? json.getInt(META_BUDGET_TOTAL) : -1;
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
        job.dateClaimed = new Date();

        if(job.claimed) {
            //Actual claim object: {"max_payments", "end_date", "date_claimed" }
            JSONObject claim = json.getJSONObject(META_CLAIM);

            String key = META_MAX_PAYMENTS;
            if (claim.has(key)) {
                job.maxVisits = claim.getInt(key);
            }

            key = META_END_DATE;
            if (claim.has(key)) {
                job.projectEndDate = df.parse(claim.getString(key));
            }

            key = META_CLAIM_DATE;
            if (claim.has(key)) {
                job.dateClaimed = df.parse(claim.getString(key));
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

        //In model but not in JSON
        job.lastWorkedDate = new Date();

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
        return getDaysRemaining() <= 0;
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
    public Date getDateCompleted() { return lastWorkedDate; }
    public Date getProjectEndDate() { return projectEndDate; }
    public void setProjectEndDate(Date date) { projectEndDate = date; }
    public int getPaymentAccrued() { return paymentAccrued != null && paymentAccrued.length() > 0 ? Integer.parseInt(paymentAccrued) : 0; }
    public void setPaymentAccrued(int paymentAccrued) { this.paymentAccrued = Integer.toString(paymentAccrued); }
    public String getCurrency() { return currency; }
    public int getNumLearningModules() { return numLearningModules; }
    public int getCompletedLearningModules() { return learningModulesCompleted; }
    public void setComletedLearningModules(int numCompleted) { this.learningModulesCompleted = numCompleted; }
    public ConnectAppRecord getLearnAppInfo() { return learnAppInfo; }
    public void setLearnAppInfo(ConnectAppRecord appInfo) { this.learnAppInfo = appInfo; }
    public ConnectAppRecord getDeliveryAppInfo() { return deliveryAppInfo; }
    public void setDeliveryAppInfo(ConnectAppRecord appInfo) { this.deliveryAppInfo = appInfo; }
    public List<ConnectJobDeliveryRecord> getDeliveries() { return deliveries; }
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
        double millis = projectEndDate.getTime() - (new Date()).getTime();
        //Ceiling means we'll get 0 within 24 hours of the end date
        //(since the end date has 00:00 time, but project is valid until midnight)
        int days = (int)Math.ceil(millis / 1000 / 3600 / 24);
        //Now plus 1 so we report i.e. 1 day remaining on the last day
        return days >= 0 ? (days + 1) : 0;
    }

    public int getMaxPossibleVisits() {
        int maxVisitsBudgeted = totalBudget / budgetPerVisit;
        int minDaysRequired = maxVisitsBudgeted / maxDailyVisits;
        int daysRemaining = getDaysRemaining();
        int max = minDaysRequired > daysRemaining ? (daysRemaining * maxDailyVisits) : maxVisitsBudgeted;
        return Math.min(max, maxVisits);
    }

    public int getLearningCompletePercentage() {
        int numLearning = getNumLearningModules();
        return numLearning > 0 ? (100 * getCompletedLearningModules() / getNumLearningModules()) : 100;
    }

    public boolean attemptedAssessment() {
        return getLearningCompletePercentage() >= 100 && assessments != null && assessments.size() > 0;
    }

    public boolean passedAssessment() {
        return getLearningCompletePercentage() >= 100 && getAssessmentScore() >= getLearnAppInfo().getPassingScore();
    }

    public int getAssessmentScore() {
        int maxScore = 0;
        if(assessments != null) {
            for(ConnectJobAssessmentRecord record : assessments) {
                maxScore  = Math.max(maxScore, record.getScore());
            }
        }
        return maxScore;
    }

    public Date getLastUpdate() { return lastUpdate; }

    public Date getLastLearnUpdate() { return lastLearnUpdate; }
    public void setLastLearnUpdate(Date date) { lastLearnUpdate = date; }
    public Date getLastDeliveryUpdate() { return lastDeliveryUpdate; }
    public void setLastDeliveryUpdate(Date date) { lastDeliveryUpdate = date; }
    public String getOrganization() { return organization; }
    public int getTotalBudget() { return totalBudget; }
    public Date getLastWorkedDate() { return lastWorkedDate; }
    public int getLearningModulesCompleted() { return learningModulesCompleted; }
    public Date getDateClaimed() { return dateClaimed; }

    public String getMoneyString(int value) {
        String currency = "";
        if(this.currency != null && this.currency.length() > 0) {
            currency = " " + this.currency;
        }

        return String.format(Locale.getDefault(), "%d%s", value, currency);
    }


    /**
     * Used for app db migration only
     */
    public static ConnectJobRecord fromV2(ConnectJobRecordV2 oldRecord) {
        ConnectJobRecord newRecord = new ConnectJobRecord(
                oldRecord.getJobId(),
                oldRecord.getTitle(),
                oldRecord.getDescription(),
                oldRecord.getStatus(),
                oldRecord.getCompletedVisits(),
                oldRecord.getMaxVisits(),
                oldRecord.getMaxDailyVisits(),
                oldRecord.getBudgetPerVisit(),
                oldRecord.getTotalBudget(),
                oldRecord.getProjectEndDate(),
                oldRecord.getLastUpdate(),
                new ArrayList<>()
        );

         newRecord.organization = oldRecord.getOrganization();
         newRecord.lastWorkedDate = oldRecord.getLastWorkedDate();
         newRecord.numLearningModules = oldRecord.getNumLearningModules();
         newRecord.learningModulesCompleted = oldRecord.getLearningModulesCompleted();
         newRecord.currency = oldRecord.getCurrency();
         newRecord.paymentAccrued = Integer.toString(oldRecord.getPaymentAccrued());
         newRecord.shortDescription = oldRecord.getShortDescription();
         newRecord.lastUpdate = oldRecord.getLastUpdate();
         newRecord.lastLearnUpdate = oldRecord.getLastLearnUpdate();
         newRecord.lastDeliveryUpdate = oldRecord.getLastDeliveryUpdate();
         newRecord.dateClaimed = new Date();

        return newRecord;
    }
}