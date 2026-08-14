package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.Date;

@Table(ConnectLearnModuleSummaryRecord.STORAGE_KEY)
public class ConnectLearnModuleSummaryRecord extends Persisted implements Serializable {
    /**
     * Name of database that stores info for Connect learn modules
     */
    public static final String STORAGE_KEY = "connect_learn_modules";

    public static final String META_MODULE_ID = "module_id";
    /** Key the server sends the module id under; distinct from the local storage column. */
    private static final String META_SERVER_ID = "id";
    public static final String META_SLUG = "slug";
    public static final String META_NAME = "name";
    public static final String META_DESCRIPTION = "description";
    public static final String META_ESTIMATE = "time_estimate";
    public static final String META_JOB_ID = "job_id";
    public static final String META_INDEX = "module_index";

    public static final String META_JOB_UUID = ConnectJobRecord.META_JOB_UUID;

    @Persisting(1)
    @MetaField(META_SLUG)
    private String slug;

    @Persisting(2)
    @MetaField(META_NAME)
    private String name;

    @Persisting(3)
    @MetaField(META_DESCRIPTION)
    private String description;

    @Persisting(4)
    @MetaField(META_ESTIMATE)
    private int timeEstimate;

    @Persisting(5)
    @MetaField(META_JOB_ID)
    private int jobId;

    @Persisting(6)
    @MetaField(META_INDEX)
    private int moduleIndex;

    @Persisting(7)
    private Date lastUpdate;

    @Persisting(8)
    @MetaField(META_JOB_UUID)
    private String jobUUID;

    /**
     * Server-assigned id for the module, matching {@link ConnectJobLearningRecord#getModuleId()}.
     * The only key that ties a completed-module record back to the module it completed.
     */
    @Persisting(9)
    @MetaField(META_MODULE_ID)
    private int moduleId;

    public ConnectLearnModuleSummaryRecord() {

    }

    public static ConnectLearnModuleSummaryRecord fromJson(JSONObject json, int moduleIndex, ConnectJobRecord job) throws JSONException {
        ConnectLearnModuleSummaryRecord info = new ConnectLearnModuleSummaryRecord();
        info.moduleIndex = moduleIndex;
        info.slug = json.getString(META_SLUG);
        info.name = json.getString(META_NAME);
        info.description = json.getString(META_DESCRIPTION);
        info.timeEstimate = json.getInt(META_ESTIMATE);
        info.moduleId = json.getInt(META_SERVER_ID);
        info.lastUpdate = new Date();

        info.jobId = job.getJobId();
        info.jobUUID = job.getJobUUID();

        return info;
    }

    public static ConnectLearnModuleSummaryRecord fromV28(ConnectLearnModuleSummaryRecordV28 oldRecord) {
        ConnectLearnModuleSummaryRecord record = new ConnectLearnModuleSummaryRecord();
        record.slug = oldRecord.getSlug();
        record.name = oldRecord.getName();
        record.description = oldRecord.getDescription();
        record.timeEstimate = oldRecord.getTimeEstimate();
        record.jobId = oldRecord.getJobId();
        record.moduleIndex = oldRecord.getModuleIndex();
        record.lastUpdate = oldRecord.getLastUpdate();
        record.jobUUID = oldRecord.getJobUUID();
        // Unknown until the next opportunities sync rewrites the module from the server payload.
        record.moduleId = 0;
        return record;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public int getModuleId() {
        return moduleId;
    }

    public String getSlug() {
        return slug;
    }

    public int getModuleIndex() {
        return moduleIndex;
    }

    public String getName() {
        return name;
    }

    public int getTimeEstimate() {
        return timeEstimate;
    }

    public void setLastUpdate(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public void setJobUUID(String jobUUID) {
        this.jobUUID = jobUUID;
    }
}
