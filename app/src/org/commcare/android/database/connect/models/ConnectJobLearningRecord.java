package org.commcare.android.database.connect.models;

import org.commcare.activities.connect.ConnectNetworkHelper;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.ParseException;
import java.util.Date;

/**
 * Data class for holding info related to the completion of a Connect job learning module
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

    public ConnectJobLearningRecord() {

    }

    public static ConnectJobLearningRecord fromJson(JSONObject json, int jobId) throws JSONException, ParseException {
        ConnectJobLearningRecord record = new ConnectJobLearningRecord();

        record.lastUpdate = new Date();

        record.jobId = jobId;
        record.date = json.has(META_DATE) ? ConnectNetworkHelper.parseDate(json.getString(META_DATE)) : new Date();
        record.moduleId = json.has(META_MODULE) ? json.getInt(META_MODULE) : -1;
        record.duration = json.has(META_DURATION) ? json.getString(META_DURATION) : "";

        return record;
    }

    public int getModuleId() { return moduleId; }
    public Date getDate() { return date; }

    public void setLastUpdate(Date date) { lastUpdate = date; }
}
