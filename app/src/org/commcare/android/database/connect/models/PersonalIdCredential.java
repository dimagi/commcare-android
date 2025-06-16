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

    // Constructors
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

    // Getters
    public String getAppName() {
        return appName;
    }

    public String getSlug() {
        return slug;
    }

    public String getType() {
        return type;
    }

    public String getIssuedDate() {
        return issuedDate;
    }

    public String getTitle() {
        return title;
    }

    public String getCredential() {
        return credential;
    }

    // Setters
    public void setAppName(String appName) {
        this.appName = appName;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setIssuedDate(String issuedDate) {
        this.issuedDate = issuedDate;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setCredential(String credential) {
        this.credential = credential;
    }

    /**
     * Parses full response string and returns valid & corrupt credentials separately.
     */
    public static PersonalIdValidAndCorruptCredential fromJsonArray(JSONArray jsonArray) {
        List<PersonalIdCredential> valid = new ArrayList<>();
        List<PersonalIdCredential> corrupt = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = null;
            try {
                obj = jsonArray.getJSONObject(i);

                PersonalIdCredential credential = new PersonalIdCredential();
                credential.setAppName(obj.getString(META_APP_NAME));
                credential.setSlug(obj.getString(META_SLUG));
                credential.setType(obj.getString(META_TYPE));
                credential.setIssuedDate(obj.getString(META_ISSUED_DATE));
                credential.setTitle(obj.getString(META_TITLE));
                credential.setCredential(obj.getString(META_CREDENTIAL));
                valid.add(credential);

            } catch (JSONException e) {
                Logger.exception("Corrupt PersonalIdCredential at index " + i, e);
                if (obj != null) {
                    corrupt.add(corruptCredentialFromJson(obj));
                }
            }
        }

        return new PersonalIdValidAndCorruptCredential(valid, corrupt);
    }


    /**
     * Creates a PersonalIdCredential with optStrings to recover partial corrupt data.
     */
    public static PersonalIdCredential corruptCredentialFromJson(JSONObject json) {
        PersonalIdCredential credItem = new PersonalIdCredential();
        credItem.appName = json.optString(META_APP_NAME,"");
        credItem.slug = json.optString(META_SLUG,"");
        credItem.type = json.optString(META_TYPE,"");
        credItem.issuedDate = json.optString(META_ISSUED_DATE,"");
        credItem.title = json.optString(META_TITLE,"");
        credItem.credential = json.optString(META_CREDENTIAL,"");
        return credItem;
    }
}
