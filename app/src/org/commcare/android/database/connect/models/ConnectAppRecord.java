package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Table(ConnectAppRecord.STORAGE_KEY)
public class ConnectAppRecord extends Persisted implements Serializable {
    /**
     * Name of table that stores app info for Connect jobs
     */
    public static final String STORAGE_KEY = "connect_apps";

    public static final String META_JOB_ID = "job_id";
    public static final String META_DOMAIN = "cc_domain";
    public static final String META_APP_ID = "cc_app_id";
    public static final String META_NAME = "name";
    public static final String META_DESCRIPTION = "description";
    public static final String META_ORGANIZATION = "organization";
    public static final String META_PASSING_SCORE = "passing_score";
    public static final String META_INSTALL_URL = "install_url";
    public static final String META_MODULES = "learn_modules";

    @Persisting(1)
    @MetaField(META_JOB_ID)
    private int jobId;
    @Persisting(2)
    private boolean isLearning;
    @Persisting(3)
    @MetaField(META_DOMAIN)
    private String domain;
    @Persisting(4)
    @MetaField(META_APP_ID)
    private String appId;
    @Persisting(5)
    @MetaField(META_NAME)
    private String name;
    @Persisting(6)
    @MetaField(META_DESCRIPTION)
    private String description;
    @Persisting(7)
    @MetaField(META_ORGANIZATION)
    private String organization;

    @Persisting(8)
    @MetaField(META_PASSING_SCORE)
    private int passingScore;
    @Persisting(9)
    @MetaField(META_INSTALL_URL)
    private String installUrl;
    @Persisting(10)
    private Date lastUpdate;

    private List<ConnectLearnModuleSummaryRecord> learnModules;

    public ConnectAppRecord() {

    }

    public static ConnectAppRecord fromJson(JSONObject json, int jobId, boolean isLearning) throws JSONException {
        ConnectAppRecord app = new ConnectAppRecord();

        app.jobId = jobId;
        app.isLearning = isLearning;

        app.domain = json.getString(META_DOMAIN);
        app.appId = json.getString(META_APP_ID);
        app.name = json.getString(META_NAME);
        app.description = json.getString(META_DESCRIPTION);
        app.organization = json.getString(META_ORGANIZATION);
        app.passingScore = json.getInt(META_PASSING_SCORE);
        app.installUrl = json.getString(META_INSTALL_URL);

        JSONArray array = json.getJSONArray(META_MODULES);
        app.learnModules = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = (JSONObject)array.get(i);
            app.learnModules.add(ConnectLearnModuleSummaryRecord.fromJson(obj, i));
        }

        return app;
    }

    public boolean getIsLearning() {
        return isLearning;
    }

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public String getAppId() {
        return appId;
    }

    public String getDomain() {
        return domain;
    }

    public int getPassingScore() {
        return passingScore;
    }

    public List<ConnectLearnModuleSummaryRecord> getLearnModules() {
        return learnModules;
    }

    public String getInstallUrl() {
        return installUrl;
    }

    public void setLearnModules(List<ConnectLearnModuleSummaryRecord> modules) {
        learnModules = modules;
    }

    public void setLastUpdate(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }
}
