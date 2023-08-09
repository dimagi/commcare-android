package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class ConnectIDPasswordVerificationActivity extends CommCareActivity<ConnectIDPasswordVerificationActivity>
implements WithUIController {
    public static final int PASSWORD_FAIL = 1;
    public static final int PASSWORD_LOCK = 2;
    private ConnectIDPasswordVerificationActivityUIController uiController;

    private String phone = null;
    private String secretKey = null;

    private static final int MaxFailures = 3;
    private int failureCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.connect_password));

        phone = getIntent().getStringExtra(ConnectIDConstants.PHONE);
        secretKey = getIntent().getStringExtra(ConnectIDConstants.SECRET);

        uiController.setupUI();

        failureCount = 0;
    }

    @Override
    public void onResume() {
        super.onResume();

        uiController.requestInputFocus();
    }

    @Override
    protected boolean shouldShowBreadcrumbBar() {
        return false;
    }

    @Override
    public CommCareActivityUIController getUIController() { return uiController; }

    @Override
    public void initUIController() { uiController = new ConnectIDPasswordVerificationActivityUIController(this); }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if(requestCode == PASSWORD_LOCK) {
            finish(true, true, null, null, null);
        }
    }

    public void finish(boolean success, boolean forgot, String username, String name, String password) {
        Intent intent = new Intent(getIntent());

        intent.putExtra(ConnectIDConstants.FORGOT, forgot);
        intent.putExtra(ConnectIDConstants.USERNAME, username);
        intent.putExtra(ConnectIDConstants.NAME, name);
        intent.putExtra(ConnectIDConstants.PASSWORD, password);

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    public void handleWrongPassword() {
        failureCount++;
        logRecoveryResult(false);
        uiController.clearPassword();

        int requestCode = PASSWORD_FAIL;
        int message = R.string.connect_password_fail_message;

        if(failureCount >= MaxFailures) {
            requestCode = PASSWORD_LOCK;
            message = R.string.connect_password_recovery_message;
        }

        Intent messageIntent = new Intent(this, ConnectIDMessageActivity.class);
        messageIntent.putExtra(ConnectIDConstants.TITLE, R.string.connect_password_fail_title);
        messageIntent.putExtra(ConnectIDConstants.MESSAGE, message);
        messageIntent.putExtra(ConnectIDConstants.BUTTON, R.string.connect_password_fail_button);

        startActivityForResult(messageIntent, requestCode);
    }

    private void logRecoveryResult(boolean success) {
        FirebaseAnalyticsUtil.reportCccRecovery(success, AnalyticsParamValue.CCC_RECOVERY_METHOD_PASSWORD);
    }

    public void handleForgotPress() {
        finish(true, true, null, null, null);
    }

    public void handleButtonPress() {
        String password = uiController.getPassword();
        ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(this);
        if(user != null) {
            //If we have the password stored locally, no need for network call
            if(password.equals(user.getPassword())) {
                logRecoveryResult(true);
                finish(true, false, null, null, null);
            }
            else {
                handleWrongPassword();
            }
        }
        else {
            HashMap<String, String> params = new HashMap<>();
            params.put("password", password);
            params.put("phone", phone);
            params.put("secret_key", secretKey);
            String url = getString(R.string.ConnectURL) + "/users/recover/confirm_password";

            ConnectIDNetworkHelper.post(this, url, new AuthInfo.NoAuth(), params, false, new ConnectIDNetworkHelper.INetworkResultHandler() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    String username = null;
                    String name = null;
                    try {
                        String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                        if(responseAsString.length() > 0) {
                            JSONObject json = new JSONObject(responseAsString);
                            String key = ConnectIDConstants.CONNECT_KEY_USERNAME;
                            if (json.has(key)) {
                                username = json.getString(key);
                            }

                            key = ConnectIDConstants.CONNECT_KEY_NAME;
                            if (json.has(key)) {
                                name = json.getString(key);
                            }
                        }
                    }
                    catch(IOException | JSONException e) {
                        Logger.exception("Parsing return from OTP request", e);
                    }
                    logRecoveryResult(true);
                    finish(true, false, username, name, password);
                }

                @Override
                public void processFailure(int responseCode, IOException e) {
                    handleWrongPassword();
                }
            });
        }
    }
}
