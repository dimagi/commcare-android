package org.commcare.activities.connect;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.utils.PhoneNumberHelper;

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

        phone = getIntent().getStringExtra(ConnectIDConstants.PHONE);

        uiController.setupUI();

        int code = PhoneNumberHelper.getCountryCode(this);
        uiController.setAltCountryCode(String.format(Locale.getDefault(), "+%d", code));

        uiController.setUserId(generateUserId());
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
        int idLength = 7;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder userId = new StringBuilder();
        for (int i = 0; i < idLength; i++) {
            userId.append(charSet.charAt(new Random().nextInt(charSet.length())));
        }

        return userId.toString();
    }

    public static String generatePassword() {
        int passwordLength = 10;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+,<.>?;[{]}|~";
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < passwordLength; i++) {
            password.append(charSet.charAt(new Random().nextInt(charSet.length())));
        }

        return password.toString();
    }

    public void finish(boolean success) {
        Intent intent = new Intent(getIntent());
        user.putUserInIntent(intent);
        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    public void createAccount() {
        String url = getString(R.string.ConnectURL) + "/users/register";

        user = new ConnectUserRecord(phone, uiController.getUserIdText(), generatePassword(), uiController.getNameText());

        String altPhone = PhoneNumberHelper.buildPhoneNumber(uiController.getAltCountryCode(), uiController.getAltPhoneNumber());

        //TODO: Disable button until a valid alt phone is entered
        if(!PhoneNumberHelper.isValidPhoneNumber(this, phone)) {
            Toast.makeText(this, "Error: Check Alt Phone!", Toast.LENGTH_SHORT).show();
            return;
        }

        HashMap<String, String> params = new HashMap<>();
        params.put("username", user.getUserID());
        params.put("password", user.getPassword());
        params.put("name", user.getName());
        params.put("phone_number", phone);
        params.put("recovery_phone", altPhone);

        final Context self = this;
        ConnectIDNetworkHelper.post(this, url, new AuthInfo.NoAuth(), params, new ConnectIDNetworkHelper.INetworkResultHandler() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                finish(true);
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Toast.makeText(self, "Registration error", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
