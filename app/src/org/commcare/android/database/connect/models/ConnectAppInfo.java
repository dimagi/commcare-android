package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.json.JSONException;
import org.json.JSONObject;

@Table(ConnectAppInfo.STORAGE_KEY)
public class ConnectAppInfo extends Persisted {
    /**
     * Name of database that stores app info for Connect jobs
     */
    public static final String STORAGE_KEY = "connect_apps";

    public static final String META_DOMAIN = "cc_domain";
    public static final String META_APP_ID = "cc_app_id";
    public static final String META_NAME = "name";
    public static final String META_DESCRIPTION = "description";
    public static final String META_ORGANIZATION = "organization";

    @Persisting(1)
    public int jobId;
    @Persisting(2)
    public boolean isLearning;
    @Persisting(3)
    @MetaField(META_DOMAIN)
    public String domain;
    @Persisting(4)
    @MetaField(META_APP_ID)
    public String appId;
    @Persisting(5)
    @MetaField(META_NAME)
    public String name;
    @Persisting(6)
    @MetaField(META_DESCRIPTION)
    public String description;
    @Persisting(7)
    @MetaField(META_ORGANIZATION)
    public String organization;

    public ConnectAppInfo() {

    }

    public static ConnectAppInfo fromJson(JSONObject json, int jobId) throws JSONException {
        ConnectAppInfo app = new ConnectAppInfo();

        app.jobId = jobId;

        app.domain = json.has(META_DOMAIN) ? json.getString(META_DOMAIN) : null;
        app.appId = json.has(META_APP_ID) ? json.getString(META_APP_ID) : null;
        app.name = json.has(META_NAME) ? json.getString(META_NAME) : null;
        app.description = json.has(META_DESCRIPTION) ? json.getString(META_DESCRIPTION) : null;
        app.organization = json.has(META_ORGANIZATION) ? json.getString(META_ORGANIZATION) : null;

        return app;
    }
}
