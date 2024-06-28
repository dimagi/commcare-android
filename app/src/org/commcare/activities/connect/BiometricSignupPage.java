package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;

import androidx.biometric.BiometricManager;

import org.commcare.activities.CommCareActivity;
import org.commcare.connect.ConnectConstants;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.utils.BiometricsHelper;

public class BiometricSignupPage extends CommCareActivity<BiometricSignupPage>
        implements WithUIController {
    private BiometricSignupPageUiController uiController;

    private BiometricManager biometricManager;

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    public void initUIController() {
        uiController = new BiometricSignupPageUiController(this);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.app_lock));
        biometricManager = BiometricManager.from(this);

        uiController.setupUI();

        BiometricsHelper.ConfigurationStatus fingerprint = BiometricsHelper.checkFingerprintStatus(this,
                biometricManager);
        BiometricsHelper.ConfigurationStatus pin = BiometricsHelper.checkPinStatus(this, biometricManager);
        if (fingerprint == BiometricsHelper.ConfigurationStatus.NotAvailable &&
                pin == BiometricsHelper.ConfigurationStatus.NotAvailable) {
//            Skip to password-only workflow
            finish(true, true);
        } else {
//            updateState(fingerprint, pin);
        }
    }

    public void handleFingerprintButton() {
        BiometricsHelper.ConfigurationStatus fingerprint = BiometricsHelper.checkFingerprintStatus(this,
                biometricManager);
        if (fingerprint == BiometricsHelper.ConfigurationStatus.Configured) {
            finish(true, false);
        } else if (!BiometricsHelper.configureFingerprint(this)) {
            finish(true, true);
        }
    }

    public void finish(boolean success, boolean failedEnrollment) {
        Intent intent = new Intent(getIntent());

        BiometricsHelper.ConfigurationStatus fingerprint = BiometricsHelper.checkFingerprintStatus(this,
                biometricManager);
        BiometricsHelper.ConfigurationStatus pin = BiometricsHelper.checkPinStatus(this, biometricManager);
        boolean configured = fingerprint == BiometricsHelper.ConfigurationStatus.Configured ||
                pin == BiometricsHelper.ConfigurationStatus.Configured;

        intent.putExtra(ConnectConstants.ENROLL_FAIL, failedEnrollment || !configured);

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }
}
