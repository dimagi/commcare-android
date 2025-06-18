package org.commcare.connect.network.parser;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.PersonalIdCredential;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.android.database.connect.models.PersonalIdValidAndCorruptCredential;
import org.commcare.models.database.SqlStorage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PersonalIdCredentialParser implements PersonalIdApiResponseParser {

    @Override
    public void parse(JSONObject json, PersonalIdSessionData sessionData) throws JSONException {
        // Parse credentials array from response
        JSONArray credentialsArray = json.getJSONArray("credentials");

        PersonalIdValidAndCorruptCredential parseResult = PersonalIdCredential.fromJsonArray(credentialsArray);

        // Get storage for this model
        SqlStorage<PersonalIdCredential> storage =
                CommCareApplication.instance().getUserStorage(PersonalIdCredential.class);

        // Clear existing credentials
        storage.removeAll();

        // Save valid credentials
        for (PersonalIdCredential credential : parseResult.getValidCredentials()) {
            storage.write(credential);
        }
    }
}
