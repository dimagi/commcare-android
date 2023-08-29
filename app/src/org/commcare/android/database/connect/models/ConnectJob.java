package org.commcare.android.database.connect.models;

import java.io.Serializable;
import java.util.Date;

/**
 * Data class for holding info relatde to a Connect job
 *
 * @author dviggiano
 */
public class ConnectJob implements Serializable {
    private final boolean isNew;
    private final String title;
    private final String description;
    private final Date learnDeadline;
    private final Date beginDeadline;
    private final Date projectEndDate;
    private final int maxVisits;
    private final int maxDailyVisits;
    private final int completedVisits;
    private final Date completedDate;
    private final ConnectJobLearningModule[] learningModules;
    private final ConnectJobDelivery[] deliveries;

    public ConnectJob(String title, String description, boolean isNew,
                      int completedVisits, int maxVisits, int maxDailyVisits,
                      Date learnDeadline, Date beginDeadline, Date projectEnd, Date completedDate,
                      ConnectJobLearningModule[] learningModules,
                      ConnectJobDelivery[] deliveries) {
        this.title = title;
        this.description = description;
        this.isNew = isNew;
        this.completedVisits = completedVisits;
        this.maxDailyVisits = maxDailyVisits;
        this.maxVisits = maxVisits;
        this.learnDeadline = learnDeadline;
        this.beginDeadline = beginDeadline;
        this.projectEndDate = projectEnd;
        this.completedDate = completedDate;
        this.learningModules = learningModules;
        this.deliveries = deliveries;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public boolean getIsNew() { return isNew; }
    public int getCompletedVisits() { return completedVisits; }
    public int getMaxVisits() { return maxVisits; }
    public int getMaxDailyVisits() { return maxDailyVisits; }
    public int getPercentComplete() { return maxVisits > 0 ? 100 * completedVisits / maxVisits : 0; }
    public Date getDateCompleted() { return completedDate; }
    public Date getLearnDeadline() { return learnDeadline; }
    public Date getBeginDeadline() { return beginDeadline; }
    public Date getProjectEndDate() { return projectEndDate; }
    public ConnectJobLearningModule[] getLearningModules() { return learningModules; }
    public ConnectJobDelivery[] getDeliveries() { return deliveries; }
}
