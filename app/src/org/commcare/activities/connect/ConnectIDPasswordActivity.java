package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.nulabinc.zxcvbn.Strength;
import com.nulabinc.zxcvbn.Zxcvbn;

import org.commcare.activities.CommCareActivity;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.views.dialogs.CustomProgressDialog;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/**
 * Shows the page that prompts the user to choose (and repeat) their password
 *
 * @author dviggiano
 */
public class ConnectIDPasswordActivity extends CommCareActivity<ConnectIDPasswordActivity>
        implements WithUIController {
    private ConnectIDPasswordActivityUIController uiController;

    private String username = null;
    private String oldPassword = null;

    private String phone = null;
    private String secret = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.connect_password_title));

        username = getIntent().getStringExtra(ConnectIDConstants.USERNAME);
        oldPassword = getIntent().getStringExtra(ConnectIDConstants.PASSWORD);
        String method = getIntent().getStringExtra(ConnectIDConstants.METHOD);
        boolean passwordOnlyWorkflow = method != null && method.equals("true");

        phone = getIntent().getStringExtra(ConnectIDConstants.PHONE);
        secret = getIntent().getStringExtra(ConnectIDConstants.SECRET);

        uiController.setupUI();

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
        uiController = new ConnectIDPasswordActivityUIController(this);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        return CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId);
    }

    public void finish(boolean success, String password) {
        Intent intent = new Intent(getIntent());

        intent.putExtra(ConnectIDConstants.PASSWORD, password);

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
        String password = uiController.getPasswordText();

        HashMap<String, String> params = new HashMap<>();
        AuthInfo authInfo;
        int urlId;
        if (username != null && username.length() > 0 && oldPassword != null && oldPassword.length() > 0) {
            authInfo = new AuthInfo.ProvidedAuth(username, oldPassword, false);
            urlId = R.string.ConnectChangePasswordURL;
        } else {
            authInfo = new AuthInfo.NoAuth();
            urlId = R.string.ConnectResetPasswordURL;

            params.put("phone", phone);
            params.put("secret_key", secret);
        }

        params.put("password", password);

        boolean isBusy = !ConnectIDNetworkHelper.post(this, getString(urlId), authInfo, params, false, new ConnectIDNetworkHelper.INetworkResultHandler() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                finish(true, password);
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Toast.makeText(getApplicationContext(), "Password change error", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void processNetworkFailure() {
                Toast.makeText(getApplicationContext(), getString(R.string.recovery_network_unavailable), Toast.LENGTH_SHORT).show();
            }
        });

        if (isBusy) {
            Toast.makeText(this, R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }
}
