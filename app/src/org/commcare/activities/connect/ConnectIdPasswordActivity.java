package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.nulabinc.zxcvbn.Strength;
import com.nulabinc.zxcvbn.Zxcvbn;

import org.commcare.activities.CommCareActivity;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.views.dialogs.CustomProgressDialog;

import java.io.IOException;
import java.io.InputStream;

/**
 * Shows the page that prompts the user to choose (and repeat) their password
 *
 * @author dviggiano
 */
public class ConnectIdPasswordActivity extends CommCareActivity<ConnectIdPasswordActivity>
        implements WithUIController {
    private ConnectIdPasswordActivityUiController uiController;

    private String username = null;
    private String oldPassword = null;

    private String phone = null;
    private String secret = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.connect_password_title));

        uiController.setupUI();

        username = getIntent().getStringExtra(ConnectConstants.USERNAME);
        oldPassword = getIntent().getStringExtra(ConnectConstants.PASSWORD);
        phone = getIntent().getStringExtra(ConnectConstants.PHONE);
        secret = getIntent().getStringExtra(ConnectConstants.SECRET);

        String method = getIntent().getStringExtra(ConnectConstants.METHOD);
        boolean passwordOnlyWorkflow = method != null && method.equals("true");

        uiController.setMessageText(passwordOnlyWorkflow ?
                getString(R.string.connect_password_message) :
                getString(R.string.connect_password_message_recovery));
    }

    @Override
    public void onResume() {
        super.onResume();

        uiController.requestInputFocus();

        checkPasswords();
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
        uiController = new ConnectIdPasswordActivityUiController(this);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        return CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId);
    }

    public void finish(boolean success, String password) {
        Intent intent = new Intent(getIntent());

        intent.putExtra(ConnectConstants.PASSWORD, password);

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    public void checkPasswords() {
        String pass1 = uiController.getPasswordText();
        String pass2 = uiController.getPasswordRepeatText();

        if (pass1.length() == 0 || pass2.length() == 0) {
            uiController.setErrorText("");
            uiController.setButtonEnabled(false);
        } else if (!pass1.equals(pass2)) {
            uiController.setErrorText(getString(R.string.connect_password_mismatch));
            uiController.setButtonEnabled(false);
        } else {
            Zxcvbn checker = new Zxcvbn();
            Strength strength = checker.measure(pass1);
            if (strength.getScore() < 2) {
                uiController.setErrorText(getString(R.string.connect_password_weak));
                uiController.setButtonEnabled(false);
            } else {
                uiController.setErrorText("");
                uiController.setButtonEnabled(true);
            }
        }
    }

    public void handleButtonPress() {
        final String password = uiController.getPasswordText();

        IApiCallback callback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                finish(true, password);
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Toast.makeText(getApplicationContext(), getString(R.string.connect_password_error),
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
        };

        boolean isBusy;
        if (username != null && username.length() > 0 && oldPassword != null && oldPassword.length() > 0) {
            isBusy = !ApiConnectId.changePassword(this, username, oldPassword, password, callback);
        } else {
            isBusy = !ApiConnectId.resetPassword(this, phone, secret, password, callback);
        }

        if (isBusy) {
            Toast.makeText(this, R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }
}
