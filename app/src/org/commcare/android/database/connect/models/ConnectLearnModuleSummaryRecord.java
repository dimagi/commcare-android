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
    public ConnectLearnModuleSummaryRecord() {

    }

    public static ConnectLearnModuleSummaryRecord fromJson(JSONObject json, int moduleIndex, ConnectJobRecord job) throws JSONException {
        ConnectLearnModuleSummaryRecord info = new ConnectLearnModuleSummaryRecord();
        info.moduleIndex = moduleIndex;
        info.slug = json.getString(META_SLUG);
        info.name = json.getString(META_NAME);
        info.description = json.getString(META_DESCRIPTION);
        info.timeEstimate = json.getInt(META_ESTIMATE);
        info.lastUpdate = new Date();

        info.jobId = job.getJobId();
        info.jobUUID = job.getJobUUID();

        return info;
    }

    public static ConnectLearnModuleSummaryRecord fromV21(ConnectLearnModuleSummaryRecordV21 connectLearnModuleSummaryRecordV21) {
        ConnectLearnModuleSummaryRecord learnModuleSummaryRecord = new ConnectLearnModuleSummaryRecord();
        learnModuleSummaryRecord.moduleIndex = connectLearnModuleSummaryRecordV21.getModuleIndex();
        learnModuleSummaryRecord.slug = connectLearnModuleSummaryRecordV21.getSlug();
        learnModuleSummaryRecord.name = connectLearnModuleSummaryRecordV21.getName();
        learnModuleSummaryRecord.description = connectLearnModuleSummaryRecordV21.getDescription();
        learnModuleSummaryRecord.timeEstimate = connectLearnModuleSummaryRecordV21.getTimeEstimate();
        learnModuleSummaryRecord.lastUpdate = connectLearnModuleSummaryRecordV21.getLastUpdate();
        learnModuleSummaryRecord.jobId = connectLearnModuleSummaryRecordV21.getJobId();
        learnModuleSummaryRecord.jobUUID = String.valueOf(connectLearnModuleSummaryRecordV21.getJobId());
        return learnModuleSummaryRecord;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
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
