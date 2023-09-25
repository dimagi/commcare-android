package org.commcare.android.database.connect.models;

import java.io.Serializable;
import java.util.Date;

/**
 * Data class for holding info relatde to a Connect job learning module
 *
 * @author dviggiano
 */
public class ConnectJobLearningModuleRecord implements Serializable {
    private final String toLearn;
    private final int estimatedHours;

    private Date completedDate;

    public ConnectJobLearningModuleRecord(String toLearn, int estimatedHours, Date completedDate) {
        this.toLearn = toLearn;
        this.estimatedHours = estimatedHours;
        this.completedDate = completedDate;
    }

    public String getToLearn() { return toLearn; }
    public int getEstimatedHours() { return estimatedHours; }
    public Date getCompletedDate() { return completedDate; }
}
