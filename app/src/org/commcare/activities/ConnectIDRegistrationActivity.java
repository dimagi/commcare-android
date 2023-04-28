package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;

import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.HTTPMethod;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.ConnectorWithHttpResponseProcessor;
import org.commcare.interfaces.WithUIController;
import org.commcare.tasks.ModernHttpTask;
import org.commcare.tasks.templates.CommCareTask;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Random;

import okhttp3.MediaType;
import okhttp3.RequestBody;

public class ConnectIDRegistrationActivity extends CommCareActivity<ConnectIDRegistrationActivity>
implements WithUIController {
    public static final String USERNAME = "USERNAME";
    public static final String PASSWORD = "PASSWORD";
    public static final String NAME = "NAME";

    private ConnectIDRegistrationActivityUIController uiController;

    private ConnectUserRecord user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiController.setupUI();

        uiController.setUserId(generateUserId());
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    public void initUIController() {
        uiController = new ConnectIDRegistrationActivityUIController(this);
    }

    private String generateUserId() {
        int idLength = 7;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder userId = new StringBuilder();
        for (int i = 0; i < idLength; i++) {
            userId.append(charSet.charAt(new Random().nextInt(charSet.length())));
        }

        return userId.toString();
    }

    public static String generatePassword() {
        int passwordLength = 10;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+,<.>?;:[{]}|~";
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < passwordLength; i++) {
            password.append(charSet.charAt(new Random().nextInt(charSet.length())));
        }

        return password.toString();
    }

    public void finish(boolean success) {
        Intent intent = new Intent(getIntent());
        user.putUserInIntent(intent);
        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    public void createAccount() {
        String url = getString(R.string.ConnectURL) + "/users/register";

        user = new ConnectUserRecord(uiController.getUserIdText(),
                generatePassword(), uiController.getNameText());
        String dob = uiController.getDOBText();
        String phone = uiController.getPhoneText();
        String altPhone = uiController.getAltPhoneText();

        HashMap<String, String> params = new HashMap<>();
        //params.put("device_id", CommCareApplication.instance().getPhoneId());
        params.put("username", user.getUserID());
        params.put("password", user.getPassword());
        params.put("name", user.getName());
        params.put("dob", dob);
        params.put("phone_number", phone);
        params.put("recovery_phone", altPhone);

        Gson gson = new Gson();
        String json = gson.toJson(params);

        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), json);
        ModernHttpTask postTask =
                new ModernHttpTask(this, url,
                        ImmutableMultimap.of(),
                        new HashMap<>(),
                        requestBody,
                        HTTPMethod.POST,
                        new AuthInfo.NoAuth());
        final ConnectIDRegistrationActivity self = this;
        postTask.connect(new ConnectorWithHttpResponseProcessor<>() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                Toast.makeText(self, "Success!", Toast.LENGTH_SHORT).show();
                finish(true);
            }

            @Override
            public void processClientError(int responseCode) {
                //400 error
                Toast.makeText(self, "Client error", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void processServerError(int responseCode) {
                //500 error for internal server error
                Toast.makeText(self, "Server error", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void processOther(int responseCode) {
                finish(false);
            }

            @Override
            public void handleIOException(IOException exception) {
                //UnknownHostException if host not found
                Toast.makeText(self, "Exception", Toast.LENGTH_SHORT).show();
            }
            @Override
            public <A, B, C> void connectTask(CommCareTask<A, B, C, HttpResponseProcessor> task) {}

            @Override
            public void startBlockingForTask(int id) {}

            @Override
            public void stopBlockingForTask(int id) {}

            @Override
            public void taskCancelled() {}

            @Override
            public HttpResponseProcessor getReceiver() { return this; }

            @Override
            public void startTaskTransition() {}

            @Override
            public void stopTaskTransition(int taskId) {}

            @Override
            public void hideTaskCancelButton() {}
        });
        postTask.executeParallel();
    }
}
