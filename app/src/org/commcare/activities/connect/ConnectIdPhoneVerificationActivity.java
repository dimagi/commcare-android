package org.commcare.activities.connect;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Shows the page that prompts the user to enter the OTP they received via SMS
 *
 * @author dviggiano
 */
public class ConnectIdPhoneVerificationActivity extends CommCareActivity<ConnectIdPhoneVerificationActivity>
        implements WithUIController {
    public static final int MethodRegistrationPrimary = 1;
    public static final int MethodRecoveryPrimary = 2;
    public static final int MethodRecoveryAlternate = 3;
    public static final int MethodVerifyAlternate = 4;

    private int method;
    private String primaryPhone;
    private String username;
    private String password;
    private String recoveryPhone;
    private boolean allowChange;
    private ConnectIdPhoneVerificationActivityUiController uiController;

    private DateTime smsTime = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.connect_verify_phone_title));

        method = Integer.parseInt(getIntent().getStringExtra(ConnectConstants.METHOD));
        primaryPhone = getIntent().getStringExtra(ConnectConstants.PHONE);
        allowChange = getIntent().getStringExtra(ConnectConstants.CHANGE).equals("true");
        username = getIntent().getStringExtra(ConnectConstants.USERNAME);
        password = getIntent().getStringExtra(ConnectConstants.PASSWORD);
        recoveryPhone = getIntent().getStringExtra(ConnectConstants.CONNECT_KEY_SECONDARY_PHONE);

        uiController.setupUI();

        updateMessage();

        requestSmsCode();

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
        uiController = new ConnectIdPhoneVerificationActivityUiController(this);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        return CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId);
    }

    private final Handler taskHandler = new android.os.Handler();

    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            int secondsToReset = -1;
            if (smsTime != null) {
                double elapsedMinutes = ((new DateTime()).getMillis() - smsTime.getMillis()) / 60000.0;
                int resendLimitMinutes = 2;
                double minutesRemaining = resendLimitMinutes - elapsedMinutes;
                if (minutesRemaining > 0) {
                    secondsToReset = (int)Math.ceil(minutesRemaining * 60);
                }
            }

            boolean allowResend = secondsToReset < 0;

            uiController.setResendEnabled(allowResend);

            String text = allowResend ?
                    getString(R.string.connect_verify_phone_resend) :
                    getString(R.string.connect_verify_phone_resend_wait, secondsToReset);

            uiController.setResendText(text);

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
        boolean alternate = method == MethodRecoveryAlternate || method == MethodVerifyAlternate;
        String text;
        String phone = alternate ? recoveryPhone : primaryPhone;
        if (phone != null) {
            //Crop to last 4 digits
            phone = phone.substring(phone.length() - 4);
            text = getString(R.string.connect_verify_phone_label, phone);
        } else {
            //The primary phone is never missing
            text = getString(R.string.connect_verify_phone_label_secondary);
        }

        uiController.setLabelText(text);
    }

    public void requestSmsCode() {
        smsTime = new DateTime();

        uiController.setErrorMessage(null);

        IApiCallback callback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        JSONObject json = new JSONObject(responseAsString);
                        String key = ConnectConstants.CONNECT_KEY_SECRET;
                        if (json.has(key)) {
                            password = json.getString(key);
                        }

                        key = ConnectConstants.CONNECT_KEY_SECONDARY_PHONE;
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

                //Null out the last-requested time so user can request again immediately
                smsTime = null;
            }

            @Override
            public void processNetworkFailure() {
                uiController.setErrorMessage(getString(R.string.recovery_network_unavailable));

                //Null out the last-requested time so user can request again immediately
                smsTime = null;
            }

            @Override
            public void processOldApiError() {
                uiController.setErrorMessage(getString(R.string.recovery_network_outdated));
            }
        };

        boolean isBusy;
        switch (method) {
            case MethodRecoveryPrimary -> {
                isBusy = !ApiConnectId.requestRecoveryOtpPrimary(this, username, callback);
            }
            case MethodRecoveryAlternate -> {
                isBusy = !ApiConnectId.requestRecoveryOtpSecondary(this, username, password, callback);
            }
            case MethodVerifyAlternate -> {
                isBusy = !ApiConnectId.requestVerificationOtpSecondary(this, username, password, callback);
            }
            default -> {
                isBusy = !ApiConnectId.requestRegistrationOtpPrimary(this, username, password, callback);
            }
        }

        if (isBusy) {
            Toast.makeText(this, R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    public void verifySmsCode() {
        uiController.setErrorMessage(null);

        String token = uiController.getCode();
        String phone = username;
        final Context context = this;

        IApiCallback callback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                logRecoveryResult(true);

                try {
                    switch(method) {
                        case MethodRegistrationPrimary -> {
                            finish(true, false, null);
                        }
                        case MethodVerifyAlternate -> {
                            ConnectUserRecord user = ConnectManager.getUser(getApplicationContext());
                            user.setSecondaryPhoneVerified(true);
                            ConnectDatabaseHelper.storeUser(context, user);

                            finish(true, false, null);
                        }
                        case MethodRecoveryPrimary -> {
                            String secondaryPhone = null;
                            String responseAsString = new String(
                                    StreamsUtil.inputStreamToByteArray(responseData));
                            if(responseAsString.length() > 0) {
                                JSONObject json = new JSONObject(responseAsString);
                                String key = ConnectConstants.CONNECT_KEY_SECONDARY_PHONE;
                                secondaryPhone = json.has(key) ? json.getString(key) : null;
                            }

                            finish(true, false, secondaryPhone);
                        }
                        case MethodRecoveryAlternate -> {
                            String responseAsString = new String(
                                    StreamsUtil.inputStreamToByteArray(responseData));
                            JSONObject json = new JSONObject(responseAsString);

                            String key = ConnectConstants.CONNECT_KEY_USERNAME;
                            String username = json.has(key) ? json.getString(key) : "";

                            key = ConnectConstants.CONNECT_KEY_NAME;
                            String displayName = json.has(key) ? json.getString(key) : "";

                            key = ConnectConstants.CONNECT_KEY_DB_KEY;
                            if (json.has(key)) {
                                //TODO: Use the passphrase from the DB
                                //json.getString(key);
                            }

                            resetPassword(context, phone, password, username, displayName);
                        }
                    }
                } catch(Exception e) {
                    Logger.exception("Parsing return from OTP verification", e);
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
                logRecoveryResult(false);
                uiController.setErrorMessage(String.format("Error verifying SMS code. %s", message));
            }

            @Override
            public void processNetworkFailure() {
                uiController.setErrorMessage(getString(R.string.recovery_network_unavailable));
            }

            @Override
            public void processOldApiError() {
                uiController.setErrorMessage(getString(R.string.recovery_network_outdated));
            }
        };

        boolean isBusy;
        switch (method) {
            case MethodRecoveryPrimary -> {
                isBusy = !ApiConnectId.confirmRecoveryOtpPrimary(this, username, password, token, callback);
            }
            case MethodRecoveryAlternate -> {
                isBusy = !ApiConnectId.confirmRecoveryOtpSecondary(this, username, password, token, callback);
            }
            case MethodVerifyAlternate -> {
                isBusy = !ApiConnectId.confirmVerificationOtpSecondary(this, username, password, token, callback);
            }
            default -> {
                isBusy = !ApiConnectId.confirmRegistrationOtpPrimary(this, username, password, token, callback);
            }
        }

        if (isBusy) {
            Toast.makeText(this, R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    private void resetPassword(Context context, String phone, String secret, String username, String name) {
        //Auto-generate and send a new password
        String password = ConnectManager.generatePassword();
        ApiConnectId.resetPassword(context, phone, secret, password, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                ConnectUserRecord user = new ConnectUserRecord(phone, username,
                        password, name, recoveryPhone);
                user.setSecondaryPhoneVerified(true);
                ConnectDatabaseHelper.storeUser(context, user);

                finish(true, false, null);
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Toast.makeText(context, getString(R.string.connect_recovery_failure),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void processNetworkFailure() {
                ConnectNetworkHelper.showNetworkError(getApplicationContext());
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(getApplicationContext());
            }
        });
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
        finish(true, true, null);
    }

    public void finish(boolean success, boolean changeNumber, String secondaryPhone) {
        stopHandler();

        Intent intent = new Intent(getIntent());
        if (method == MethodRecoveryPrimary) {
            intent.putExtra(ConnectConstants.SECRET, password);
            intent.putExtra(ConnectConstants.CHANGE, changeNumber);
            if(secondaryPhone != null) {
                intent.putExtra(ConnectConstants.CONNECT_KEY_SECONDARY_PHONE, secondaryPhone);
            }
        } else if (method != MethodRecoveryAlternate) {
            intent.putExtra(ConnectConstants.CHANGE, changeNumber);
        }

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }
}
