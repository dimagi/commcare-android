package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.javarosa.core.model.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.Date;

/**
 * Data class for holding info related to the completion of a Connect job learning module
 * This version (V2) includes additional fields for learning modules and payment tracking
 * compared to V1. Migration from V1 automatically copies existing fields and initializes
 * new fields with default values.
 *
 * @author dviggiano
 */
@Table(ConnectJobLearningRecord.STORAGE_KEY)
public class ConnectJobLearningRecord extends Persisted implements Serializable {
    /**
     * Name of database that stores Connect job learning records
     */
    public static final String STORAGE_KEY = "connect_learning_completion";

    public static final String META_JOB_ID = "id";
    public static final String META_DATE = "date";
    public static final String META_MODULE = "module";
    public static final String META_DURATION = "duration";
    public static final String META_LEARNING_UUID = "learning_id";  // todo The server needs to confirm this learning UUID field name.
    public static final String META_JOB_UUID = ConnectJobRecord.META_JOB_UUID;

    @Persisting(1)
    @MetaField(META_JOB_ID)
    private int jobId;
    @Persisting(2)
    @MetaField(META_DATE)
    private Date date;
    @Persisting(3)
    @MetaField(META_MODULE)
    private int moduleId;
    @Persisting(4)
    @MetaField(META_DURATION)
    private String duration;
    @Persisting(5)
    private Date lastUpdate;
    @Persisting(6)
    @MetaField(META_LEARNING_UUID)
    private String learningUUID;
    @Persisting(7)
    @MetaField(META_JOB_UUID)
    private String jobUUID;

    public ConnectJobLearningRecord() {
    }

    public static ConnectJobLearningRecord fromJson(JSONObject json, ConnectJobRecord job) throws JSONException {
        ConnectJobLearningRecord record = new ConnectJobLearningRecord();

        record.lastUpdate = new Date();

        record.jobId = job.getJobId();
        if (job.getJobUUID().isEmpty()) {
            record.jobUUID = Integer.toString(job.getJobId());
        } else {
            record.jobUUID = job.getJobUUID();
        }

        record.learningUUID = json.optString(META_LEARNING_UUID,"");

        record.date = DateUtils.parseDateTime(json.getString(META_DATE));
        record.moduleId = json.getInt(META_MODULE);
        record.duration = json.getString(META_DURATION);

        return record;
    }

    public int getModuleId() {
        return moduleId;
    }

    public Date getDate() {
        return date;
    }

    public void setLastUpdate(Date date) {
        lastUpdate = date;
    }

    /// getter setter for kolin
    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public void setModuleId(int moduleId) {
        this.moduleId = moduleId;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }

    public String getJobUUID() {
        return jobUUID;
    }

    public void setJobUUID(String jobUUID) {
        this.jobUUID = jobUUID;
    }

    public String getLearningUUID() {
        return learningUUID;
    }

    public void setLearningUUID(String learningUUID) {
        this.learningUUID = learningUUID;
    }

    ///
}
