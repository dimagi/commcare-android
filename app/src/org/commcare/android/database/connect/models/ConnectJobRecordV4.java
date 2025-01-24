package org.commcare.android.database.connect.models;

import android.util.Log;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;

import java.io.Serializable;
import java.util.Date;

import androidx.annotation.NonNull;

/**
 * Data class for holding info related to a Connect job
 *
 * @author dviggiano
 */
@Table(ConnectJobRecordV4.STORAGE_KEY)
public class ConnectJobRecordV4 extends Persisted implements Serializable {
    /**
     * Name of database that stores Connect jobs/opportunities
     */
    public static final String STORAGE_KEY = "connect_jobs";

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

    public static final String META_DELIVERY_PROGRESS = "deliver_progress";
    public static final String META_CLAIM_DATE = "date_claimed";
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

    public ConnectJobRecordV4() {

    }

    public int getJobId() { return jobId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getShortDescription() { return shortDescription; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public int getCompletedVisits() { return completedVisits; }
    public int getMaxVisits() { return maxVisits; }
    public int getMaxDailyVisits() { return maxDailyVisits; }
    public int getBudgetPerVisit() { return budgetPerVisit; }
    public Date getProjectEndDate() { return projectEndDate; }
    public int getPaymentAccrued() {
        if (paymentAccrued == null || paymentAccrued.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(paymentAccrued);
        } catch (NumberFormatException e) {
            Log.e("ConnectJobRecordV4", "Failed to parse paymentAccrued: " + paymentAccrued, e);
            return 0;
        }
    }
    public String getCurrency() { return currency; }
    public int getNumLearningModules() { return numLearningModules; }
    public void setLastUpdate(Date lastUpdate) { this.lastUpdate = lastUpdate; }
    public Date getLastUpdate() { return lastUpdate; }
    public Date getLastLearnUpdate() { return lastLearnUpdate; }
    public Date getLastDeliveryUpdate() { return lastDeliveryUpdate; }
    public String getOrganization() { return organization; }
    public int getTotalBudget() { return totalBudget; }
    public Date getLastWorkedDate() { return lastWorkedDate; }
    public int getLearningModulesCompleted() { return learningModulesCompleted; }

    /**
     * Used for app db migration only
     */
    public static ConnectJobRecordV4 fromV2(@NonNull ConnectJobRecordV2 oldRecord) {
        ConnectJobRecordV4 newRecord = new ConnectJobRecordV4();

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
        newRecord.paymentAccrued = Integer.toString(oldRecord.getPaymentAccrued());
        newRecord.shortDescription = oldRecord.getShortDescription();
        newRecord.lastUpdate = oldRecord.getLastUpdate();
        newRecord.lastLearnUpdate = oldRecord.getLastLearnUpdate();
        newRecord.lastDeliveryUpdate = oldRecord.getLastDeliveryUpdate();
        newRecord.dateClaimed = new Date();

        return newRecord;
    }
}
