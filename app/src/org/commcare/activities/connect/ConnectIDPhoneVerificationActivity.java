package org.commcare.activities.connect;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.commcare.activities.CommCareActivity;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class ConnectIDPhoneVerificationActivity extends CommCareActivity<ConnectIDPhoneVerificationActivity>
implements WithUIController {
    public static final int MethodRegistrationPrimary = 1;
    public static final int MethodRecoveryPrimary = 2;
    public static final int MethodRecoveryAlternate = 3;

    private int method;
    private String primaryPhone;
    private String username;
    private String password;
    private String recoveryPhone;
    private boolean allowChange;
    private ConnectIDPhoneVerificationActivityUIController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        method = Integer.parseInt(getIntent().getStringExtra(ConnectIDConstants.METHOD));
        primaryPhone = getIntent().getStringExtra(ConnectIDConstants.PHONE);
        allowChange = getIntent().getStringExtra(ConnectIDConstants.CHANGE).equals("true");
        username = getIntent().getStringExtra(ConnectIDConstants.USERNAME);
        password = getIntent().getStringExtra(ConnectIDConstants.PASSWORD);

        uiController.setupUI();

        updateMessage();

        requestSMSCode();
    }

    @Override
    public void onResume() {
        super.onResume();

        if(allowChange) {
            uiController.showChangeOption();
        }

        uiController.requestInputFocus();
    }

    @Override
    public CommCareActivityUIController getUIController() { return uiController; }

    @Override
    public void initUIController() { uiController = new ConnectIDPhoneVerificationActivityUIController(this); }

    public void updateMessage() {
        boolean alternate = method == MethodRecoveryAlternate;
        int labelId = alternate ?
                R.string.connect_verify_phone_label_alternate :
                R.string.connect_verify_phone_label;

        String phone = alternate ? recoveryPhone : primaryPhone;
        if(phone != null) {
            phone = " (" + phone + ")";
        }
        else {
            phone = "";
        }

        uiController.setLabelText(getString(labelId, phone));
    }

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
                authInfo = new AuthInfo.ProvidedAuth(username, password, false);
            }
        }
        String url = getString(R.string.ConnectURL) + command;

        Context context = this;
        ConnectIDNetworkHelper.post(this, url, authInfo, params, new ConnectIDNetworkHelper.INetworkResultHandler() {
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

                        key = "secondary_phone";
                        if (json.has(key)) {
                            recoveryPhone = json.getString(key);
                            updateMessage();
                        }
                    }
                }
                catch(IOException | JSONException e) {
                    Logger.exception("Parsing return from OTP request", e);
                }
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                //Fail with indication to change number
                finish(false, true, null, null);
            }
        });
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
                authInfo = new AuthInfo.ProvidedAuth(username, password, false);
            }
        }
        String url = getString(R.string.ConnectURL) + command;

        //params.put("device_id", CommCareApplication.instance().getPhoneId());
        params.put("token", uiController.getCode());

        final Context self = this;
        ConnectIDNetworkHelper.post(this, url, authInfo, params, new ConnectIDNetworkHelper.INetworkResultHandler() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                String username = "";
                String displayName = "";
                if(method == MethodRecoveryAlternate) {
                    try {
                        String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                        JSONObject json = new JSONObject(responseAsString);
                        String key = "username";
                        if (json.has(key)) {
                            username = json.getString(key);
                        }

                        key = "name";
                        if (json.has(key)) {
                            displayName = json.getString(key);
                        }
                    } catch (IOException | JSONException e) {
                        Logger.exception("Parsing return from confirm_secondary_otp", e);
                    }
                }
                finish(true, false, username, displayName);
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Toast.makeText(self, "SMS Verify error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void changeNumber() {
        finish(true, true, null, null);
    }

    public void finish(boolean success, boolean changeNumber, String username, String name) {
        Intent intent = new Intent(getIntent());
        if(method == MethodRecoveryPrimary) {
            intent.putExtra(ConnectIDConstants.SECRET, password);
            intent.putExtra(ConnectIDConstants.CHANGE, changeNumber);
        }
        else if(method == MethodRecoveryAlternate) {
            intent.putExtra(ConnectIDConstants.USERNAME, username);
            intent.putExtra(ConnectIDConstants.NAME, name);
        }
        else {
            intent.putExtra(ConnectIDConstants.CHANGE, changeNumber);
        }

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }
}
