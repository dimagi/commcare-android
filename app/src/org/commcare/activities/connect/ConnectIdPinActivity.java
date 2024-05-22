package org.commcare.activities.connect;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
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
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Date;

/**
 * Shows the page that prompts the user to choose (and repeat) their recovery PIN
 *
 * @author dviggiano
 */
public class ConnectIdPinActivity extends CommCareActivity<ConnectIdPinActivity>
        implements WithUIController {
    private static final int pinLength = 6;
    public static final int PIN_FAIL = 1;
    public static final int PIN_LOCK = 2;
    private ConnectIdPinActivityUiController uiController;

    private String phone = null;
    private String secret = null;

    private boolean isRecovery; //Else registration
    private boolean isChanging; //Else verifying

    private static final int MaxFailures = 3;
    private int failureCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiController.setupUI();

        phone = getIntent().getStringExtra(ConnectConstants.PHONE);
        secret = getIntent().getStringExtra(ConnectConstants.SECRET);

        isRecovery = getIntent().getBooleanExtra(ConnectConstants.RECOVER, false);
        isChanging = getIntent().getBooleanExtra(ConnectConstants.CHANGE, false);

        int titleId = isChanging ? R.string.connect_pin_title_set :
                R.string.connect_pin_title_confirm;
        setTitle(getString(titleId));
        uiController.setTitleText(getString(titleId));

        int messageId;
        if(isChanging) {
            messageId = R.string.connect_pin_message_set;
        } else {
            messageId = isRecovery ? R.string.connect_pin_message_repeat :
                    R.string.connect_pin_message_confirm;
        }
        uiController.setMessageText(getString(messageId));

        uiController.setPinRepeatTextVisible(isChanging);
        uiController.setPinLength(pinLength);
        uiController.setPinForgotTextVisible(!isChanging);
    }

    @Override
    public void onResume() {
        super.onResume();

        uiController.requestInputFocus();

        checkPin();
    }

    @Override
    protected boolean shouldShowBreadcrumbBar() {
        return false;
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    public void initUIController() {
        uiController = new ConnectIdPinActivityUiController(this);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        return CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId);
    }

    public void finish(boolean success, boolean forgot, String pin) {
        Intent intent = new Intent(getIntent());

        intent.putExtra(ConnectConstants.PIN, pin);
        intent.putExtra(ConnectConstants.FORGOT, forgot);

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    public void checkPin() {
        String pin1 = uiController.getPinText();
        String pin2 = uiController.getPinRepeatText();

        String errorText = "";
        boolean buttonEnabled = false;
        if (pin1.length() > 0) {
            if (pin1.length() != pinLength) {
                errorText = getString(R.string.connect_pin_length, pinLength);
            } else if (isChanging && !pin1.equals(pin2)) {
                errorText = getString(R.string.connect_pin_mismatch);
            } else {
                buttonEnabled = true;
            }
        }

        uiController.setErrorText(errorText);
        uiController.setButtonEnabled(buttonEnabled);
    }

    public void handleButtonPress() {
        String pin = uiController.getPinText();
        ConnectUserRecord user = ConnectDatabaseHelper.getUser(this);

        boolean isBusy = false;
        final Context context = this;
        if(isChanging) {
            //Change PIN
            isBusy = !ApiConnectId.changePin(this, user.getUserId(), user.getPassword(), pin,
                    new IApiCallback() {
                        @Override
                        public void processSuccess(int responseCode, InputStream responseData) {
                            user.setPin(pin);
                            ConnectDatabaseHelper.storeUser(context, user);

                            finish(true, false, pin);
                        }

                        @Override
                        public void processFailure(int responseCode, IOException e) {
                            handleWrongPin();
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
        } else if(isRecovery) {
            //Confirm PIN
            isBusy = !ApiConnectId.checkPin(this, phone, secret, pin,
                    new IApiCallback() {
                        @Override
                        public void processSuccess(int responseCode, InputStream responseData) {
                            String username = null;
                            String name = null;
                            try {
                                String responseAsString = new String(
                                        StreamsUtil.inputStreamToByteArray(responseData));
                                if (responseAsString.length() > 0) {
                                    JSONObject json = new JSONObject(responseAsString);

                                    String key = ConnectConstants.CONNECT_KEY_USERNAME;
                                    if (json.has(key)) {
                                        username = json.getString(key);
                                    }

                                    key = ConnectConstants.CONNECT_KEY_NAME;
                                    if (json.has(key)) {
                                        name = json.getString(key);
                                    }

                                    key = ConnectConstants.CONNECT_KEY_DB_KEY;
                                    if (json.has(key)) {
                                        //TODO: Use the passphrase from the DB
                                        //json.getString(key);
                                    }

                                    ConnectUserRecord user = new ConnectUserRecord(phone, username,
                                            "", name, "");
                                    user.setPin(pin);
                                    user.setLastPinDate(new Date());

                                    key = ConnectConstants.CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY;
                                    user.setSecondaryPhoneVerified(!json.has(key) || json.isNull(key));
                                    if (!user.getSecondaryPhoneVerified()) {
                                        user.setSecondaryPhoneVerifyByDate(ConnectNetworkHelper.parseDate(json.getString(key)));
                                    }

                                    resetPassword(context, phone, secret, user);
                                }
                                else {
                                    //TODO: Show toast about error
                                }
                            } catch (IOException | JSONException | ParseException e) {
                                Logger.exception("Parsing return from OTP request", e);
                                //TODO: Show toast about error
                            }
                        }

                        @Override
                        public void processFailure(int responseCode, IOException e) {
                            handleWrongPin();
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
        } else if (pin.equals(user.getPin())) {
            //Local confirmation
            logRecoveryResult(true);
            finish(true, false, pin);
        } else {
            //Local failure
            handleWrongPin();
        }

        if (isBusy) {
            Toast.makeText(this, R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    private void resetPassword(Context context, String phone, String secret, ConnectUserRecord user) {
        //Auto-generate and send a new password
        String password = ConnectManager.generatePassword();
        ApiConnectId.resetPassword(context, phone, secret, password, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                //TODO: Need to get secondary phone from server
                user.setPassword(password);

                ConnectDatabaseHelper.storeUser(context, user);

                finish(true, false, user.getPin());
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

    public void handleWrongPin() {
        failureCount++;
        logRecoveryResult(false);
        uiController.clearPin();

        int requestCode = PIN_FAIL;
        int message = R.string.connect_pin_fail_message;

        if (failureCount >= MaxFailures) {
            requestCode = PIN_LOCK;
            message = R.string.connect_pin_recovery_message;
        }

        Intent messageIntent = new Intent(this, ConnectIdMessageActivity.class);
        messageIntent.putExtra(ConnectConstants.TITLE, R.string.connect_pin_fail_title);
        messageIntent.putExtra(ConnectConstants.MESSAGE, message);
        messageIntent.putExtra(ConnectConstants.BUTTON, R.string.connect_pin_fail_button);

        startActivityForResult(messageIntent, requestCode);
    }

    public void handleForgotPress() {
        finish(true, true, null);
    }

    private void logRecoveryResult(boolean success) {
        FirebaseAnalyticsUtil.reportCccRecovery(success, AnalyticsParamValue.CCC_RECOVERY_METHOD_PIN);
    }
}

