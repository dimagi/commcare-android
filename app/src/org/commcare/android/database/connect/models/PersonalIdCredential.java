package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.javarosa.core.services.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DB model for personal_id_credential table
 */
@Table(PersonalIdCredential.STORAGE_KEY)
public class PersonalIdCredential extends Persisted implements Serializable {
    public static final String STORAGE_KEY = "personal_id_credential";

    public static final String META_APP_NAME = "app_name";
    public static final String META_SLUG = "slug";
    public static final String META_TYPE = "type";
    public static final String META_ISSUED_DATE = "issued_date";
    public static final String META_TITLE = "title";
    public static final String META_CREDENTIAL = "credential";

    @Persisting(1)
    @MetaField(META_APP_NAME)
    private String appName;

    @Persisting(2)
    @MetaField(META_SLUG)
    private String slug;

    @Persisting(3)
    @MetaField(META_TYPE)
    private String type;

    @Persisting(4)
    @MetaField(META_ISSUED_DATE)
    private String issuedDate;

    @Persisting(5)
    @MetaField(META_TITLE)
    private String title;

    @Persisting(6)
    @MetaField(META_CREDENTIAL)
    private String credential;

    public PersonalIdCredential() {

    }

    public PersonalIdCredential(String appName, String slug, String type,
                                String issuedDate, String title, String credential) {
        this.appName = appName;
        this.slug = slug;
        this.type = type;
        this.issuedDate = issuedDate;
        this.title = title;
        this.credential = credential;
    }

    public String getAppName() { return appName; }
    public String getSlug() { return slug; }
    public String getType() { return type; }
    public String getIssuedDate() { return issuedDate; }
    public String getTitle() { return title; }
    public String getCredential() { return credential; }


    public void setAppName(String appName) { this.appName = appName; }
    public void setSlug(String slug) { this.slug = slug; }
    public void setType(String type) { this.type = type; }
    public void setIssuedDate(String issuedDate) { this.issuedDate = issuedDate; }
    public void setTitle(String title) { this.title = title; }
    public void setCredential(String credential) { this.credential = credential; }

    public static List<PersonalIdCredential> fromJsonArray(JSONArray jsonArray) {
        List<PersonalIdCredential> credentials = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject obj = jsonArray.getJSONObject(i);
                PersonalIdCredential credential = new PersonalIdCredential();
                credential.setAppName(obj.optString(META_APP_NAME));
                credential.setSlug(obj.optString(META_SLUG));
                credential.setType(obj.optString(META_TYPE));
                credential.setIssuedDate(obj.optString(META_ISSUED_DATE));
                credential.setTitle(obj.optString(META_TITLE));
                credential.setCredential(obj.optString(META_CREDENTIAL));
                credentials.add(credential);
            } catch (JSONException e) {
                Logger.exception("Error parsing PersonalIdCredential json", e);
            }
        }
        return credentials;
    }
}
