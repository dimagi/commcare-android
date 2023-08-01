package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;

public class ConnectIDRegistrationActivity extends CommCareActivity<ConnectIDRegistrationActivity>
implements WithUIController {
    private ConnectIDRegistrationActivityUIController uiController;

    private ConnectUserRecord user;
    private String phone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.connect_register_title));

        phone = getIntent().getStringExtra(ConnectIDConstants.PHONE);

        uiController.setupUI();

        ConnectUserRecord user = ConnectIDManager.getUser(this);
        if(user != null) {
            uiController.setNameText(user.getName());
        }

        updateStatus();
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
        uiController = new ConnectIDRegistrationActivityUIController(this);
    }

    private String generateUserId() {
        int idLength = 20;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder userId = new StringBuilder();
        for (int i = 0; i < idLength; i++) {
            userId.append(charSet.charAt(new Random().nextInt(charSet.length())));
        }

        return userId.toString();
    }

    public static String generatePassword() {
        int passwordLength = 15;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_!.?";
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < passwordLength; i++) {
            password.append(charSet.charAt(new Random().nextInt(charSet.length())));
        }

        return password.toString();
    }

    public void updateStatus() {
        String error = uiController.getNameText().length() == 0 ?
                getString(R.string.connect_register_error_name) : null;

        uiController.setErrorText(error);
        uiController.setButtonEnabled(error == null);
    }

    public void finish(boolean success) {
        Intent intent = new Intent(getIntent());
        user.putUserInIntent(intent);
        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    public void continuePressed() {
        user = ConnectIDManager.getUser(this);
        if(user == null) {
            createAccount();
        }
        else {
            updateAccount();
        }
    }

    public void createAccount() {
        uiController.setErrorText(null);
        
        String url = getString(R.string.ConnectURL) + "/users/register";

        user = new ConnectUserRecord(phone, generateUserId(), generatePassword(), uiController.getNameText(), "");

        HashMap<String, String> params = new HashMap<>();
        params.put("username", user.getUserID());
        params.put("password", user.getPassword());
        params.put("name", user.getName());
        params.put("phone_number", phone);

        ConnectIDNetworkHelper.post(this, url, new AuthInfo.NoAuth(), params, false, new ConnectIDNetworkHelper.INetworkResultHandler() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                finish(true);
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                uiController.setErrorText(String.format(Locale.getDefault(), "Registration error: %d", responseCode));
            }
        });
    }

    public void updateAccount() {
        uiController.setErrorText(null);

        String url = getString(R.string.ConnectURL) + "/users/update_profile";

        String newName = uiController.getNameText();

        if(newName.equals(user.getName())) {
            finish(true);
        }
        else {
            user.setName(newName);

            HashMap<String, String> params = new HashMap<>();
            params.put("name", user.getName());

            ConnectIDNetworkHelper.post(this, url, new AuthInfo.ProvidedAuth(user.getUserID(), user.getPassword(), false), params, false, new ConnectIDNetworkHelper.INetworkResultHandler() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    finish(true);
                }

                @Override
                public void processFailure(int responseCode, IOException e) {
                    uiController.setErrorText(String.format(Locale.getDefault(), "Error: %d", responseCode));
                }
            });
        }
    }
}
