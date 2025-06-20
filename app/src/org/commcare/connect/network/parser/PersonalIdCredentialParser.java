package org.commcare.connect.network.parser;

import android.content.Context;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.javarosa.core.services.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static org.commcare.connect.database.ConnectAppDatabaseUtil.storeCredentialDataInTable;

public class PersonalIdCredentialParser implements PersonalIdApiResponseParser {
    private final Context context;

    public PersonalIdCredentialParser(Context context) {
        this.context = context;
    }

    @Override
    public void parse(JSONObject json, PersonalIdSessionData sessionData) throws JSONException {
        JSONArray credentialsArray = json.optJSONArray("credentials");

        if (context == null) {
            Logger.log("PersonalIdCredentialParser", "Context must not be null");
            return;
        }

        if (credentialsArray == null) {
            Logger.log("PersonalIdCredentialParser", "credentialsArray must not be null");
            return;
        }

        storeCredentialDataInTable(context, credentialsArray);
    }
}
