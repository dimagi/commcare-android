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
 * Data class for holding info relatde to a Connect job
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
    public static final int STATUS_COMPLETE = 5;

    public static final String META_JOB_ID = "id";
    public static final String META_NAME = "name";
    public static final String META_DESCRIPTION = "description";
    public static final String META_DATE_CREATED = "date_created";
    public static final String META_DATE_MODIFIED = "date_modified";
    public static final String META_ORGANIZATION = "organization";
    public static final String META_END_DATE = "end_date";
    public static final String META_MAX_VISITS = "max_visits_per_user";
    public static final String META_MAX_DAILY_VISITS = "daily_max_visits_per_user";
    public static final String META_BUDGET_PER_VISIT = "budget_per_visit";
    public static final String META_BUDGET_TOTAL = "total_budget";
    public static final String META_COMPLETED_VISITS = "completed_visits";
    public static final String META_LAST_WORKED_DATE = "last_worked";
    public static final String META_STATUS = "status";
    public static final String META_LEARN_MODULES = "total_modules";
    public static final String META_COMPLETED_MODULES = "completed_modules";

    public static final String META_LEARN_PROGRESS = "learn_progress";
    public static final String META_LEARN_APP = "learn_app";
    public static final String META_DELIVER_APP = "deliver_app";

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
    @MetaField(META_COMPLETED_VISITS)
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
//    private ConnectJobLearningModule[] learningModules;
    private List<ConnectJobDeliveryRecord> deliveries;
    private ConnectAppRecord learnAppInfo;
    private ConnectAppRecord deliveryAppInfo;

    public ConnectJobRecord() {

    }

    public ConnectJobRecord(int jobId, String title, String description, int status,
                            int completedVisits, int maxVisits, int maxDailyVisits, int budgetPerVisit, int totalBudget,
                            Date projectEnd, Date lastWorkedDate,
//                      ConnectJobLearningModule[] learningModules,
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
//        this.learningModules = learningModules;
        this.deliveries = deliveries;
    }

    public static ConnectJobRecord fromJson(JSONObject json) throws JSONException, ParseException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        ConnectJobRecord job = new ConnectJobRecord();

        job.jobId = json.has(META_JOB_ID) ? json.getInt(META_JOB_ID) : -1;
        job.title = json.has(META_NAME) ? json.getString(META_NAME) : null;
        job.description = json.has(META_DESCRIPTION) ? json.getString(META_DESCRIPTION) : null;
        job.organization = json.has(META_ORGANIZATION) ? json.getString(META_ORGANIZATION) : null;
        job.projectEndDate = json.has(META_END_DATE) ? df.parse(json.getString(META_END_DATE)) : new Date();
        job.maxVisits = json.has(META_MAX_VISITS) ? json.getInt(META_MAX_VISITS) : -1;
        job.maxDailyVisits = json.has(META_MAX_DAILY_VISITS) ? json.getInt(META_MAX_DAILY_VISITS) : -1;
        job.budgetPerVisit = json.has(META_BUDGET_PER_VISIT) ? json.getInt(META_BUDGET_PER_VISIT) : -1;
        job.totalBudget = json.has(META_BUDGET_TOTAL) ? json.getInt(META_BUDGET_TOTAL) : -1;

//        job.learningModules = new ConnectJobLearningModule[]{};
        job.deliveries = new ArrayList<>();

        JSONObject learning = json.getJSONObject(META_LEARN_PROGRESS);
        job.numLearningModules = learning.getInt(META_LEARN_MODULES);
        job.learningModulesCompleted = learning.getInt(META_COMPLETED_MODULES);

        job.learnAppInfo = ConnectAppRecord.fromJson(json.getJSONObject(META_LEARN_APP), job.jobId, true);
        job.deliveryAppInfo = ConnectAppRecord.fromJson(json.getJSONObject(META_DELIVER_APP), job.jobId, false);

        //In JSON but not in model
        //job.? = json.has(META_DATE_CREATED) ? df.parse(json.getString(META_DATE_CREATED)) : null;
        //job.? = json.has(META_DATE_MODIFIED) ? df.parse(json.getString(META_DATE_MODIFIED)) : null;

        //In model but not in JSON
        //job.completedVisits = 0;
        job.lastWorkedDate = new Date();

        job.status = STATUS_AVAILABLE;
        if(job.getLearningCompletePercentage() > 0) {
            job.status = STATUS_LEARNING;
            if(false) {//TODO: Check claim date
                job.status = STATUS_DELIVERING;
                if(false) { //TODO: Check num_deliveries == maxDeliveries
                    job.status = STATUS_COMPLETE;
                }
            }
        }

        return job;
    }

    public int getJobId() { return jobId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getOrganization() { return organization; }
    public boolean getIsNew() { return status == STATUS_AVAILABLE_NEW; }
    public int getStatus() { return status; }
    public int getCompletedVisits() { return completedVisits; }
    public int getMaxVisits() { return maxVisits; }
    public int getMaxDailyVisits() { return maxDailyVisits; }
    public int getBudgetPerVisit() { return budgetPerVisit; }
    public int getTotalBudget() { return totalBudget; }
    public int getPercentComplete() { return maxVisits > 0 ? 100 * completedVisits / maxVisits : 0; }
    public Date getDateCompleted() { return lastWorkedDate; }
    public Date getProjectEndDate() { return projectEndDate; }
    public int getNumLearningModules() { return numLearningModules; }
    public int getCompletedLearningModules() { return learningModulesCompleted; }
    public ConnectAppRecord getLearnAppInfo() { return learnAppInfo; }
    public void setLearnAppInfo(ConnectAppRecord appInfo) { this.learnAppInfo = appInfo; }
    public ConnectAppRecord getDeliveryAppInfo() { return deliveryAppInfo; }
    public void setDeliveryAppInfo(ConnectAppRecord appInfo) { this.deliveryAppInfo = appInfo; }
    //public ConnectJobLearningModule[] getLearningModules() { return learningModules; }
    public List<ConnectJobDeliveryRecord> getDeliveries() { return deliveries; }

    public int getDaysRemaining() {
        double millis = projectEndDate.getTime() - (new Date()).getTime();
        return (int)(millis / 1000 / 3600 / 24);
    }

    public int getMaxPossibleVisits() {
        int maxVisitsBudgeted = totalBudget / budgetPerVisit;
        int minDaysRequired = maxVisitsBudgeted / maxDailyVisits;
        int daysRemaining = getDaysRemaining();
        return minDaysRequired > daysRemaining ? (daysRemaining * maxDailyVisits) : maxVisitsBudgeted;
    }

    public int getLearningCompletePercentage() {
        int numLearning = getNumLearningModules();
        return numLearning > 0 ? (100 * getCompletedLearningModules() / getNumLearningModules()) : 100;
    }
}
