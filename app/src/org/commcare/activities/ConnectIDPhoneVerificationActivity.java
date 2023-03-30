package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;

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

import okhttp3.MediaType;
import okhttp3.RequestBody;

public class ConnectIDPhoneVerificationActivity extends CommCareActivity<ConnectIDPhoneVerificationActivity>
implements WithUIController {

    public static final String USERNAME = "USERNAME";
    public static final String PASSWORD = "PASSWORD";
    private String username;
    private String password;
    private ConnectIDPhoneVerificationActivityUIController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        username = getIntent().getStringExtra(USERNAME);
        password = getIntent().getStringExtra(PASSWORD);

        uiController.setupUI();

        requestSMSCode();
    }

    @Override
    public CommCareActivityUIController getUIController() { return uiController; }

    @Override
    public void initUIController() { uiController = new ConnectIDPhoneVerificationActivityUIController(this); }

    public void requestSMSCode() {
        String url = getString(R.string.ConnectURL) + "/users/validate_phone";

        HashMap<String, String> params = new HashMap<>();
        //params.put("device_id", CommCareApplication.instance().getPhoneId());

        Gson gson = new Gson();
        String json = gson.toJson(params);

        final ConnectIDPhoneVerificationActivity self = this;
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), json);
        ModernHttpTask postTask =
                new ModernHttpTask(this, url,
                        ImmutableMultimap.of(),
                        new HashMap<>(),
                        requestBody,
                        HTTPMethod.POST,
                        new AuthInfo.BasicAuth(username, password));
        postTask.connect(new ConnectorWithHttpResponseProcessor<>() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                Toast.makeText(self, "Requested SMS code!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void processClientError(int responseCode) {
                //400 error
                Toast.makeText(self, "SMS Request: Client error", Toast.LENGTH_SHORT).show();
                //finish(false);
            }

            @Override
            public void processServerError(int responseCode) {
                Toast.makeText(self, "SMS Request: Server error", Toast.LENGTH_SHORT).show();
                //500 error for internal server error
                //finish(false);
            }

            @Override
            public void processOther(int responseCode) {
                Toast.makeText(self, "SMS Request: Other error", Toast.LENGTH_SHORT).show();
                //finish(false);
            }

            @Override
            public void handleIOException(IOException exception) {
                Toast.makeText(self, "SMS Request: Exception", Toast.LENGTH_SHORT).show();
                //UnknownHostException if host not found
                //finish(false);
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

    public void verifySMSCode() {
        String url = getString(R.string.ConnectURL) + "/users/confirm_otp";

        HashMap<String, String> params = new HashMap<>();
        //params.put("device_id", CommCareApplication.instance().getPhoneId());
        params.put("token", uiController.getCode());

        Gson gson = new Gson();
        String json = gson.toJson(params);

        final ConnectIDPhoneVerificationActivity self = this;
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), json);
        ModernHttpTask postTask =
                new ModernHttpTask(this, url,
                        ImmutableMultimap.of(),
                        new HashMap<>(),
                        requestBody,
                        HTTPMethod.POST,
                        new AuthInfo.BasicAuth(username, password));
        postTask.connect(new ConnectorWithHttpResponseProcessor<>() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                Toast.makeText(self, "Phone number verified!", Toast.LENGTH_SHORT).show();
                finish(true);
            }

            @Override
            public void processClientError(int responseCode) {
                //400 error
                Toast.makeText(self, "SMS Verify: Client error", Toast.LENGTH_SHORT).show();
                //finish(false);
            }

            @Override
            public void processServerError(int responseCode) {
                Toast.makeText(self, "SMS Verify: Server error", Toast.LENGTH_SHORT).show();
                //500 error for internal server error
                //finish(false);
            }

            @Override
            public void processOther(int responseCode) {
                Toast.makeText(self, "SMS Verify: Other error", Toast.LENGTH_SHORT).show();
                //finish(false);
            }

            @Override
            public void handleIOException(IOException exception) {
                Toast.makeText(self, "SMS Verify: Exception", Toast.LENGTH_SHORT).show();
                //UnknownHostException if host not found
                //finish(false);
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

    public void finish(boolean success) {
        Intent intent = new Intent(getIntent());
        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }
}
