package org.commcare.activities.connect;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import org.commcare.activities.CommCareActivity;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.utils.PhoneNumberHelper;
import org.commcare.views.dialogs.CustomProgressDialog;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;


/**
 * Shows the page asking the user whether to register a new account or recover their existing account
 *
 * @author dviggiano
 */
public class ConnectIdRecoveryDecisionActivity extends CommCareActivity<ConnectIdRecoveryDecisionActivity>
        implements WithUIController {
    private enum ConnectRecoveryState {
        NewOrRecover,
        PhoneOrExtended
    }

    private ConnectIdRecoveryDecisionActivityUiController uiController;
    private ConnectRecoveryState state;
    protected boolean skipPhoneNumberCheck = false;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.connect_recovery_title));

        state = ConnectRecoveryState.NewOrRecover;

        uiController.setupUI();

        uiController.setMessage(getString(R.string.connect_recovery_decision_new));
        uiController.setButton1Text(getString(R.string.connect_recovery_button_new));
        uiController.setButton2Text(getString(R.string.connect_recovery_button_recover));
    }

    @Override
    protected boolean shouldShowBreadcrumbBar() {
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (state == ConnectRecoveryState.PhoneOrExtended) {
            uiController.requestInputFocus();
            checkPhoneNumber();
        }
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    public void initUIController() {
        uiController = new ConnectIdRecoveryDecisionActivityUiController(this);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        return CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String phone=PhoneNumberHelper.handlePhoneNumberPickerResult(requestCode,resultCode,data,this);
        displayNumber(phone);
    }

    void displayNumber(String fullNumber) {
        int code = PhoneNumberHelper.getCountryCode(this);
        if (fullNumber != null && fullNumber.length() > 0) {
            code = PhoneNumberHelper.getCountryCode(this, fullNumber);
        }

        String codeText = "";
        if (code > 0) {
            codeText = String.format(Locale.getDefault(), "%d", code);
        }

        if (fullNumber != null && fullNumber.startsWith("+" + codeText)) {
            fullNumber = fullNumber.substring(codeText.length() + 1);
        }
        skipPhoneNumberCheck = false;
        uiController.setPhoneNumber(fullNumber);
        skipPhoneNumberCheck = true;
        uiController.setCountryCode(codeText);
    }

    public void finish(boolean createNew, String phone) {
        Intent intent = new Intent(getIntent());

        intent.putExtra(ConnectConstants.CREATE, createNew);
        intent.putExtra(ConnectConstants.PHONE, phone);

        setResult(RESULT_OK, intent);
        finish();
    }

    public void handleButton1Press() {
        switch (state) {
            case NewOrRecover -> finish(true, null);
            case PhoneOrExtended ->
                    finish(false, PhoneNumberHelper.buildPhoneNumber(uiController.getCountryCode(),
                            uiController.getPhoneNumber()));
        }
    }

    public void handleButton2Press() {
        switch (state) {
            case NewOrRecover -> {
                state = ConnectRecoveryState.PhoneOrExtended;
                uiController.setPhoneInputVisible(true);

                setTitle(getString(R.string.connect_recovery_title2));
                PhoneNumberHelper.requestPhoneNumberHint(this);
                int code = PhoneNumberHelper.getCountryCode(this);
                uiController.setCountryCode(String.format(Locale.getDefault(), "+%d", code));

                uiController.requestInputFocus();
                uiController.setMessage(getString(R.string.connect_recovery_decision_phone));
                uiController.setButton1Text(getString(R.string.connect_recovery_button_phone));
                uiController.setButton2Visible(false);
            }
            case PhoneOrExtended -> {
                Toast.makeText(this, "Not ready yet!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void checkPhoneNumber() {
        if (!skipPhoneNumberCheck) {
            String phone = PhoneNumberHelper.buildPhoneNumber(uiController.getCountryCode(),
                    uiController.getPhoneNumber());

            boolean valid = PhoneNumberHelper.isValidPhoneNumber(this, phone);

            if (valid) {
                phone = phone.replaceAll("\\+", "%2b");
                uiController.setPhoneMessage(getString(R.string.connect_phone_checking));
                uiController.setButton1Enabled(false);

                boolean isBusy = !ApiConnectId.checkPhoneAvailable(this, phone,
                        new IApiCallback() {
                            @Override
                            public void processSuccess(int responseCode, InputStream responseData) {
                                uiController.setPhoneMessage(getString(R.string.connect_phone_not_found));
                                uiController.setButton1Enabled(false);
                                skipPhoneNumberCheck=false;
                            }

                            @Override
                            public void processFailure(int responseCode, IOException e) {
                                uiController.setPhoneMessage("");
                                uiController.setButton1Enabled(true);
                            }

                            @Override
                            public void processNetworkFailure() {
                                uiController.setPhoneMessage(getString(R.string.recovery_network_unavailable));
                                uiController.setButton1Enabled(false);
                            }

                            @Override
                            public void processOldApiError() {
                                uiController.setPhoneMessage(getString(R.string.recovery_network_outdated));
                                uiController.setButton1Enabled(false);
                            }
                        });

                if (isBusy) {
                    Toast.makeText(this, R.string.busy_message, Toast.LENGTH_SHORT).show();
                }
            } else {
                uiController.setPhoneMessage(getString(R.string.connect_phone_invalid));
                uiController.setButton1Enabled(false);
            }
        }
    }
}
