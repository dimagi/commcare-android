package org.commcare.tasks;


import org.apache.http.HttpResponse;
import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.network.HttpRequestGenerator;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.notifications.MessageTag;
import org.commcare.xml.AppPropertiesXmlParser;
import org.javarosa.core.model.User;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Performs a refresh of certain app properties based on their current values on the server
 */
public abstract class UpdatePropertiesTask<R> extends CommCareTask<ApplicationRecord, Void, UpdatePropertiesTask.UpdatePropertiesResult, R> {

    public static final int UPDATE_PROPERTIES_TASK_ID = 11;

    // FOR TESTING, REMOVE
    private static final String xml_string = "<AppProperties appId=\"\" domain=\"\">\n" +
            "\t<property key=\"multiple-apps-compatible\" value=\"enabled\" signature=\"test-signature\" />\n" +
            "</AppProperties>";

    public UpdatePropertiesTask() {
        this.taskId = UPDATE_PROPERTIES_TASK_ID;
    }

    @Override
    protected UpdatePropertiesResult doTaskBackground(ApplicationRecord... params) {
        if (params.length > 0) {
            // If an app record is passed to the execute method, seats that app first and performs
            // a property update for it. If no app record is passed, assumes that it is acting
            // upon the currently seated app.
            CommCareApplication._().initializeAppResources(new CommCareApp(params[0]));
        }

        String propertyUpdateEndpoint = CommCareApplication._().getCurrentApp().getAppPreferences()
                .getString("properties-url", null);
        if (propertyUpdateEndpoint == null) {
            return UpdatePropertiesResult.ENDPOINT_NOT_SET;
        }

        HttpRequestGenerator requestGenerator;
        try {
            User user = CommCareApplication._().getSession().getLoggedInUser();
            requestGenerator = new HttpRequestGenerator(user);
        } catch (SessionUnavailableException e) {
            requestGenerator = new HttpRequestGenerator();
        }

        try {
            HttpResponse response = requestGenerator.get(propertyUpdateEndpoint);
            int responseCode = response.getStatusLine().getStatusCode();
            if (responseCode >= 200 && responseCode < 300) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                response.getEntity().writeTo(bos);
                return parseAndSaveResponse(new ByteArrayInputStream(bos.toByteArray()));
            } else {
                return UpdatePropertiesResult.REQUEST_ERROR;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return UpdatePropertiesResult.ERROR_PARSING_RESPONSE;
        }
    }

    private static UpdatePropertiesResult parseAndSaveResponse(InputStream responseBody) {
        try {
            AppPropertiesXmlParser parser = new AppPropertiesXmlParser(responseBody);
            parser.commit(parser.parse());
            return UpdatePropertiesResult.SUCCESS;
        } catch (IOException | InvalidStructureException | XmlPullParserException | UnfullfilledRequirementsException e) {
            return UpdatePropertiesResult.ERROR_PARSING_RESPONSE;
        }
    }

    public enum UpdatePropertiesResult implements MessageTag {
        SUCCESS(""),
        ENDPOINT_NOT_SET("notification.properties.update.error.endpoint"),
        REQUEST_ERROR("notification.properties.update.error.request"),
        ERROR_PARSING_RESPONSE("notification.properties.update.error.parsing");

        UpdatePropertiesResult(String root) {
            this.root = root;
        }

        private final String root;

        public String getLocaleKeyBase() {
            return root;
        }

        public String getCategory() {
            return "update_properties";
        }
    }
}
