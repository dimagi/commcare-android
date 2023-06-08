package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class ConnectIDPasswordVerificationActivity extends CommCareActivity<ConnectIDPasswordVerificationActivity>
implements WithUIController {
    private ConnectIDPasswordVerificationActivityUIController uiController;

    private String phone = null;
    private String secretKey = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        phone = getIntent().getStringExtra(ConnectIDConstants.PHONE);
        secretKey = getIntent().getStringExtra(ConnectIDConstants.SECRET);

        uiController.setupUI();
    }

    @Override
    public void onResume() {
        super.onResume();

        uiController.requestInputFocus();
    }

    @Override
    public CommCareActivityUIController getUIController() { return uiController; }

    @Override
    public void initUIController() { uiController = new ConnectIDPasswordVerificationActivityUIController(this); }

    public void finish(boolean success, boolean forgot, String username, String name, String password) {
        Intent intent = new Intent(getIntent());

        intent.putExtra(ConnectIDConstants.FORGOT, forgot);
        intent.putExtra(ConnectIDConstants.USERNAME, username);
        intent.putExtra(ConnectIDConstants.NAME, name);
        intent.putExtra(ConnectIDConstants.PASSWORD, password);

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    public void showWrongPasswordDialog() {
        Intent messageIntent = new Intent(this, ConnectIDMessageActivity.class);
        messageIntent.putExtra(ConnectIDConstants.TITLE, Localization.get("connect.password.fail.title"));
        messageIntent.putExtra(ConnectIDConstants.MESSAGE, Localization.get("connect.password.fail.message"));
        messageIntent.putExtra(ConnectIDConstants.BUTTON, Localization.get("connect.password.fail.button"));

        startActivityForResult(messageIntent, 1);
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
                finish(true, false, null, null, null);
            }
            else {
                showWrongPasswordDialog();
            }
        }
        else {
            HashMap<String, String> params = new HashMap<>();
            params.put("password", password);
            params.put("phone", phone);
            params.put("secret_key", secretKey);
            String url = getString(R.string.ConnectURL) + "/users/recover/confirm_password";

            ConnectIDNetworkHelper.post(this, url, new AuthInfo.NoAuth(), params, new ConnectIDNetworkHelper.INetworkResultHandler() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    String username = null;
                    String name = null;
                    try {
                        String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                        if(responseAsString.length() > 0) {
                            JSONObject json = new JSONObject(responseAsString);
                            String key = "username";
                            if (json.has(key)) {
                                username = json.getString(key);
                            }

                            key = "name";
                            if (json.has(key)) {
                                name = json.getString(key);
                            }
                        }
                    }
                    catch(IOException | JSONException e) {
                        Logger.exception("Parsing return from OTP request", e);
                    }

                    finish(true, false, username, name, password);
                }

                @Override
                public void processFailure(int responseCode, IOException e) {
                    showWrongPasswordDialog();
                }
            });
        }
    }
}
