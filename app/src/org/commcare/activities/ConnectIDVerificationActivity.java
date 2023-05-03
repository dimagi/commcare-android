package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.biometric.BiometricManager;

import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;

public class ConnectIDVerificationActivity extends CommCareActivity<ConnectIDVerificationActivity>
implements WithUIController {

    private ConnectIDVerificationActivityUIController uiController;
    private BiometricManager biometricManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        biometricManager = BiometricManager.from(this);

        uiController.setupUI();

        updateStatus();
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    public void initUIController() {
        uiController = new ConnectIDVerificationActivityUIController(this);
    }

    public void updateStatus() {
        uiController.setFingerprintStatus(BiometricsHelper.checkFingerprintStatus(biometricManager));
        uiController.setPinStatus(BiometricsHelper.checkPinStatus(biometricManager));
    }

    public void configureFingerprint() {
        BiometricsHelper.configureFingerprint(this);
    }

    public void configurePin() {
        BiometricsHelper.configurePin(this);
    }

    public void handleActionButton() {
        finish(true);
    }

    public void finish(boolean success) {
        Intent intent = new Intent(getIntent());
        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }
}
