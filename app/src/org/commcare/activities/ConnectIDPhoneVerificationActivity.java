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
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import okhttp3.MediaType;
import okhttp3.RequestBody;

public class ConnectIDPhoneVerificationActivity extends CommCareActivity<ConnectIDPhoneVerificationActivity>
implements WithUIController {
    public static final int MethodRegistrationPrimary = 1;
    public static final int MethodRecoveryPrimary = 2;
    public static final int MethodRecoveryAlternate = 3;

    public static final String METHOD = "METHOD";
    public static final String USERNAME = "USERNAME";
    public static final String PASSWORD = "PASSWORD";

    private int method;
    private String username;
    private String password;
    private ConnectIDPhoneVerificationActivityUIController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        method = getIntent().getIntExtra(METHOD, MethodRegistrationPrimary);

        username = getIntent().getStringExtra(USERNAME);
        password = getIntent().getStringExtra(PASSWORD);

        uiController.setupUI();

        String labelKey = method == MethodRecoveryAlternate ?
                "connect.verify.phone.label.alternate" :
                "connect.verify.phone.label";

        uiController.setLabelText(Localization.get(labelKey));

        requestSMSCode();
    }

    @Override
    public void onResume() {
        super.onResume();

        uiController.requestInputFocus(this);
    }

    @Override
    public CommCareActivityUIController getUIController() { return uiController; }

    @Override
    public void initUIController() { uiController = new ConnectIDPhoneVerificationActivityUIController(this); }

    public void requestSMSCode() {
        String command;
        HashMap<String, String> params = new HashMap<>();
        AuthInfo authInfo = new AuthInfo.NoAuth();
        switch(method) {
            case MethodRecoveryPrimary -> {
                command = "/users/recover";
                params.put("phone", username);
            }
            case MethodRecoveryAlternate -> {
                command = "/users/recover/secondary";
                params.put("phone", username);
                params.put("secret_key", password);
            }
            default -> {
                command = "/users/validate_phone";
                authInfo = new AuthInfo.BasicAuth(username, password);
            }
        }
        String url = getString(R.string.ConnectURL) + command;

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
                        authInfo);
        postTask.connect(new ConnectorWithHttpResponseProcessor<>() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if(responseAsString.length() > 0) {
                        JSONObject json = new JSONObject(responseAsString);
                        String key = "secret";
                        if (json.has(key)) {
                            password = json.getString(key);
                        }
                    }
                }
                catch(IOException | JSONException e) {
                    Logger.exception("Parsing return from OTP request", e);
                }

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
        String command;
        HashMap<String, String> params = new HashMap<>();
        AuthInfo authInfo = new AuthInfo.NoAuth();
        switch(method) {
            case MethodRecoveryPrimary -> {
                command = "/users/recover/confirm_otp";
                params.put("phone", username);
                params.put("secret_key", password);
            }
            case MethodRecoveryAlternate -> {
                command = "/users/recover/confirm_secondary_otp";
                params.put("phone", username);
                params.put("secret_key", password);
            }
            default -> {
                command = "/users/confirm_otp";
                authInfo = new AuthInfo.BasicAuth(username, password);
            }
        }
        String url = getString(R.string.ConnectURL) + command;

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
                        authInfo);
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
            }

            @Override
            public void processServerError(int responseCode) {
                Toast.makeText(self, "SMS Verify: Server error", Toast.LENGTH_SHORT).show();
                //500 error for internal server error
            }

            @Override
            public void processOther(int responseCode) {
                Toast.makeText(self, "SMS Verify: Other error", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void handleIOException(IOException exception) {
                Toast.makeText(self, "SMS Verify: Exception", Toast.LENGTH_SHORT).show();
                //UnknownHostException if host not found
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
        if(method == MethodRecoveryPrimary) {
            intent.putExtra(PASSWORD, password);
        }

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }
}
