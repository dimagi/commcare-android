package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;

import androidx.biometric.BiometricManager;

import org.commcare.activities.BiometricsHelper;
import org.commcare.activities.CommCareActivity;
import org.commcare.dalvik.R;
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
    protected void onResume() {
        super.onResume();

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
        BiometricsHelper.ConfigurationStatus fingerprint = BiometricsHelper.checkFingerprintStatus(biometricManager);
        uiController.setFingerprintStatus(fingerprint);

        BiometricsHelper.ConfigurationStatus pin = BiometricsHelper.checkPinStatus(biometricManager);
        uiController.setPinStatus(pin);

        boolean configured = fingerprint == BiometricsHelper.ConfigurationStatus.Configured || pin == BiometricsHelper.ConfigurationStatus.Configured;
        String text = configured ? getString(R.string.connect_verify_button_configured) : getString(R.string.connect_verify_button_password);
        uiController.setButtonText(text);
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

        BiometricsHelper.ConfigurationStatus fingerprint = BiometricsHelper.checkFingerprintStatus(biometricManager);
        BiometricsHelper.ConfigurationStatus pin = BiometricsHelper.checkPinStatus(biometricManager);
        boolean configured = fingerprint == BiometricsHelper.ConfigurationStatus.Configured || pin == BiometricsHelper.ConfigurationStatus.Configured;
        intent.putExtra(ConnectIDConstants.CONFIGURED, configured);

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }
}
