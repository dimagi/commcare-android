package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

@Table(ConnectAppRecord.STORAGE_KEY)
public class ConnectAppRecord extends Persisted {
    /**
     * Name of database that stores app info for Connect jobs
     */
    public static final String STORAGE_KEY = "connect_apps";

    public static final String META_JOB_ID = "job_id";
    public static final String META_DOMAIN = "cc_domain";
    public static final String META_APP_ID = "cc_app_id";
    public static final String META_NAME = "name";
    public static final String META_DESCRIPTION = "description";
    public static final String META_ORGANIZATION = "organization";
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

    private List<ConnectLearnModuleSummaryRecord> learnModules;

    public ConnectAppRecord() {

    }

    public static ConnectAppRecord fromJson(JSONObject json, int jobId, boolean isLearning) throws JSONException {
        ConnectAppRecord app = new ConnectAppRecord();

        app.jobId = jobId;
        app.isLearning = isLearning;

        app.domain = json.has(META_DOMAIN) ? json.getString(META_DOMAIN) : null;
        app.appId = json.has(META_APP_ID) ? json.getString(META_APP_ID) : null;
        app.name = json.has(META_NAME) ? json.getString(META_NAME) : null;
        app.description = json.has(META_DESCRIPTION) ? json.getString(META_DESCRIPTION) : null;
        app.organization = json.has(META_ORGANIZATION) ? json.getString(META_ORGANIZATION) : null;

        JSONArray array = json.getJSONArray(META_MODULES);
        app.learnModules = new ArrayList<>();
        for(int i=0; i<array.length(); i++) {
            JSONObject obj = (JSONObject)array.get(i);
            app.learnModules.add(ConnectLearnModuleSummaryRecord.fromJson(obj, i));
        }

        return app;
    }

    public boolean getIsLearning() { return isLearning; }
    public int getJobId() { return jobId; }
    public void setJobId(int jobId) { this.jobId = jobId; }

    public String getAppId() { return appId; }

    public List<ConnectLearnModuleSummaryRecord> getLearnModules() { return learnModules; }
    public void setLearnModules(List<ConnectLearnModuleSummaryRecord> modules) { learnModules = modules; }
}
