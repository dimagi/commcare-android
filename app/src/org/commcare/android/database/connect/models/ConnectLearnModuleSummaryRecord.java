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

    public ConnectLearnModuleSummaryRecord() {

    }

    public static ConnectLearnModuleSummaryRecord fromJson(JSONObject json, int moduleIndex) throws JSONException {
        ConnectLearnModuleSummaryRecord info = new ConnectLearnModuleSummaryRecord();

        info.moduleIndex = moduleIndex;

        info.slug = json.getString(META_SLUG);
        info.name = json.getString(META_NAME);
        info.description = json.getString(META_DESCRIPTION);
        info.timeEstimate = json.getInt(META_ESTIMATE);

        return info;
    }

    public void setJobId(int jobId) { this.jobId = jobId; }
    public String getSlug() { return slug; }
    public int getModuleIndex() { return moduleIndex; }
    public String getName() { return name; }
    public int getTimeEstimate() { return timeEstimate; }
    public void setLastUpdate(Date lastUpdate) { this.lastUpdate = lastUpdate; }
}
