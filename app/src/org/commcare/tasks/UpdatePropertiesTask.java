package org.commcare.tasks;

import android.content.SharedPreferences;

import org.apache.http.HttpResponse;
import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.network.HttpRequestGenerator;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.model.User;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by amstone326 on 4/10/16.
 */
public class UpdatePropertiesTask<R> extends CommCareTask<String, Integer, UpdatePropertiesTask.UpdatePropertiesResult, R> {

    @Override
    protected UpdatePropertiesResult doTaskBackground(String... params) {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        SharedPreferences prefs = app.getAppPreferences();
        String propertyUpdateEndpoint = prefs.getString("properties-url", null);
        if (propertyUpdateEndpoint == null) {
            return UpdatePropertiesResult.ENDPOINT_NOT_SET;
        }

        HttpRequestGenerator generator;
        try {
            User user = CommCareApplication._().getSession().getLoggedInUser();
            generator = new HttpRequestGenerator(user);
        } catch (SessionUnavailableException e) {
            generator = new HttpRequestGenerator();
        }

        try {
            HttpResponse response = generator.get(propertyUpdateEndpoint);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            response.getEntity().writeTo(bos);
            return processResponse(new String(bos.toByteArray()));
        } catch (IOException e) {
            e.printStackTrace();
            return UpdatePropertiesResult.ERROR_WRITING_RESULT;
        }
    }

    private static UpdatePropertiesResult processResponse(String response) {
        try {
            JSONObject json = new JSONObject(response);

            return UpdatePropertiesResult.SUCCESS;
        } catch (JSONException e) {
            return UpdatePropertiesResult.ERROR_PARSING_RESULT;
        }
    }

    @Override
    protected void deliverResult(R r, UpdatePropertiesResult error) {

    }

    @Override
    protected void deliverUpdate(R r, Integer... update) {

    }

    @Override
    protected void deliverError(R r, Exception e) {

    }

    public enum UpdatePropertiesResult {
        SUCCESS,
        ENDPOINT_NOT_SET,
        ERROR_WRITING_RESULT,
        ERROR_PARSING_RESULT
    }
}
