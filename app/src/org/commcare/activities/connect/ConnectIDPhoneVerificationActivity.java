package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import org.commcare.activities.CommCareActivity;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;

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

    private DateTime smsTime = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.connect_verify_phone_title));

        method = Integer.parseInt(getIntent().getStringExtra(ConnectIDConstants.METHOD));
        primaryPhone = getIntent().getStringExtra(ConnectIDConstants.PHONE);
        allowChange = getIntent().getStringExtra(ConnectIDConstants.CHANGE).equals("true");
        username = getIntent().getStringExtra(ConnectIDConstants.USERNAME);
        password = getIntent().getStringExtra(ConnectIDConstants.PASSWORD);

        uiController.setupUI();

        updateMessage();

        requestSMSCode();

        startHandler();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (allowChange) {
            uiController.showChangeOption();
        }

        uiController.requestInputFocus();
    }

    @Override
    protected boolean shouldShowBreadcrumbBar() {
        return false;
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return uiController;
    }

    @Override
    public void initUIController() {
        uiController = new ConnectIDPhoneVerificationActivityUIController(this);
    }

    private final Handler taskHandler = new android.os.Handler();

    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if (smsTime != null) {
                double elapsedMinutes = ((new DateTime()).getMillis() - smsTime.getMillis()) / 60000.0;
                int resendLimitMinutes = 2;
                boolean allowResend = elapsedMinutes > resendLimitMinutes;
                uiController.setResendEnabled(allowResend);

                String text = getString(R.string.connect_verify_phone_resend);
                if (!allowResend) {
                    text = getString(R.string.connect_verify_phone_resend_wait, (int)Math.ceil((resendLimitMinutes - elapsedMinutes) * 60));
                } else {
                    smsTime = null;
                }

                uiController.setResendText(text);
            }

            taskHandler.postDelayed(this, 100);
        }
    };

    void startHandler() {
        taskHandler.postDelayed(runnable, 100);
    }

    void stopHandler() {
        taskHandler.removeCallbacks(runnable);
    }

    public void updateMessage() {
        boolean alternate = method == MethodRecoveryAlternate;
        String phone = alternate ? recoveryPhone : primaryPhone;
        if (phone == null) {
            phone = "-";
        }

        uiController.setLabelText(getString(R.string.connect_verify_phone_label, phone));
    }

    public void requestSMSCode() {
        smsTime = new DateTime();

        uiController.setErrorMessage(null);
        String command;
        HashMap<String, String> params = new HashMap<>();
        AuthInfo authInfo = new AuthInfo.NoAuth();
        switch (method) {
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

        boolean isBusy = !ConnectIDNetworkHelper.post(this, url, authInfo, params, false, new ConnectIDNetworkHelper.INetworkResultHandler() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        JSONObject json = new JSONObject(responseAsString);
                        String key = ConnectIDConstants.CONNECT_KEY_SECRET;
                        if (json.has(key)) {
                            password = json.getString(key);
                        }

                        key = ConnectIDConstants.CONNECT_KEY_SECONDARY_PHONE;
                        if (json.has(key)) {
                            recoveryPhone = json.getString(key);
                            updateMessage();
                        }
                    }
                } catch (IOException | JSONException e) {
                    Logger.exception("Parsing return from OTP request", e);
                }
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                String message = "";
                if (responseCode > 0) {
                    message = String.format(Locale.getDefault(), "(%d)", responseCode);
                } else if (e != null) {
                    message = e.toString();
                }

                uiController.setErrorMessage(String.format("Error requesting SMS code. %s", message));
            }
        });

        if (isBusy) {
            Toast.makeText(this, R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    public void verifySMSCode() {
        uiController.setErrorMessage(null);
        String command;
        HashMap<String, String> params = new HashMap<>();
        AuthInfo authInfo = new AuthInfo.NoAuth();
        switch (method) {
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

        params.put("token", uiController.getCode());

        boolean isBusy = !ConnectIDNetworkHelper.post(this, url, authInfo, params, false, new ConnectIDNetworkHelper.INetworkResultHandler() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                String username = "";
                String displayName = "";
                if (method == MethodRecoveryAlternate) {
                    try {
                        String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                        JSONObject json = new JSONObject(responseAsString);
                        String key = ConnectIDConstants.CONNECT_KEY_USERNAME;
                        if (json.has(key)) {
                            username = json.getString(key);
                        }

                        key = ConnectIDConstants.CONNECT_KEY_NAME;
                        if (json.has(key)) {
                            displayName = json.getString(key);
                        }
                    } catch (IOException | JSONException e) {
                        Logger.exception("Parsing return from confirm_secondary_otp", e);
                    }
                }
                logRecoveryResult(true);
                finish(true, false, username, displayName, recoveryPhone);
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                String message = "";
                if (responseCode > 0) {
                    message = String.format(Locale.getDefault(), "(%d)", responseCode);
                } else if (e != null) {
                    message = e.toString();
                }
                logRecoveryResult(false);
                uiController.setErrorMessage(String.format("Error verifying SMS code. %s", message));
            }
        });

        if (isBusy) {
            Toast.makeText(this, R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    private void logRecoveryResult(boolean success) {
        if (method != MethodRegistrationPrimary) {
            String methodParam = AnalyticsParamValue.CCC_RECOVERY_METHOD_PRIMARY_OTP;
            if (method == MethodRecoveryAlternate) {
                methodParam = AnalyticsParamValue.CCC_RECOVERY_METHOD_ALTERNATE_OTP;
            }
            FirebaseAnalyticsUtil.reportCccRecovery(success, methodParam);
        }
    }

    public void changeNumber() {
        finish(true, true, null, null, null);
    }

    public void finish(boolean success, boolean changeNumber, String username, String name, String altPhone) {
        stopHandler();

        Intent intent = new Intent(getIntent());
        if (method == MethodRecoveryPrimary) {
            intent.putExtra(ConnectIDConstants.SECRET, password);
            intent.putExtra(ConnectIDConstants.CHANGE, changeNumber);
        } else if (method == MethodRecoveryAlternate) {
            intent.putExtra(ConnectIDConstants.USERNAME, username);
            intent.putExtra(ConnectIDConstants.NAME, name);
            intent.putExtra(ConnectIDConstants.ALT_PHONE, altPhone);
        } else {
            intent.putExtra(ConnectIDConstants.CHANGE, changeNumber);
        }

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }
}
