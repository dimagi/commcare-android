package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.javarosa.core.model.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.ParseException;
import java.util.Date;

/**
 * Data class for holding info related to a Connect job assessment
 *
 * @author dviggiano
 */
@Table(ConnectJobAssessmentRecord.STORAGE_KEY)
public class ConnectJobAssessmentRecord extends Persisted implements Serializable {
    /**
     * Name of database that stores Connect job assessments
     */
    public static final String STORAGE_KEY = "connect_assessments";

    public static final String META_JOB_ID = "id";
    public static final String META_DATE = "date";
    public static final String META_SCORE = "score";
    public static final String META_PASSING_SCORE = "passing_score";
    public static final String META_PASSED = "passed";

    @Persisting(1)
    @MetaField(META_JOB_ID)
    private int jobId;
    @Persisting(2)
    @MetaField(META_DATE)
    private Date date;
    @Persisting(3)
    @MetaField(META_SCORE)
    private int score;
    @Persisting(4)
    @MetaField(META_PASSING_SCORE)
    private int passingScore;
    @Persisting(5)
    @MetaField(META_PASSED)
    private boolean passed;
    @Persisting(6)
    private Date lastUpdate;

    public ConnectJobAssessmentRecord() {

    }

    public static ConnectJobAssessmentRecord fromJson(JSONObject json, int jobId) throws JSONException, ParseException {
        ConnectJobAssessmentRecord record = new ConnectJobAssessmentRecord();

        record.lastUpdate = new Date();

        record.jobId = jobId;
        record.date = DateUtils.parseDateTime(json.getString(META_DATE));
        record.score = json.getInt(META_SCORE);
        record.passingScore = json.getInt(META_PASSING_SCORE);
        record.passed = json.has(META_PASSED) && json.getBoolean(META_PASSED);

        return record;
    }

    public Date getDate() { return date; }
    public int getScore() { return score; }
    public int getPassingScore() { return passingScore; }

    public void setLastUpdate(Date date) { lastUpdate = date; }
}
