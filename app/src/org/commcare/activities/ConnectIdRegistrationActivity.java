package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;

import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.utils.CredentialUtil;

import java.util.Random;

public class ConnectIdRegistrationActivity extends CommCareActivity<ConnectIdRegistrationActivity>
implements WithUIController {
    private static final String TAG = ConnectIdRegistrationActivity.class.getSimpleName();

    private ConnectIdRegistrationActivityUiController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiController.setupUI();

        uiController.setUserId(generateUserId());
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    public void initUIController() {
        uiController = new ConnectIdRegistrationActivityUiController(this);
    }

    private String generateUserId() {
        int idLength = 7;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String userId = "";
        for (int i = 0; i < idLength; i++) {
            userId += charSet.charAt(new Random().nextInt(charSet.length()));
        }

        return userId;
    }

    public void createAccount() {
        Intent intent = new Intent(getIntent());
        setResult(RESULT_OK, intent);
        finish();
    }
}
