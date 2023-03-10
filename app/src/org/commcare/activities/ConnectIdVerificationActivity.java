package org.commcare.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import androidx.biometric.BiometricManager;

import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;

public class ConnectIdVerificationActivity extends CommCareActivity<ConnectIdVerificationActivity>
implements WithUIController {

    private ConnectIdVerificationActivityUiController uiController;
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
        uiController = new ConnectIdVerificationActivityUiController(this);
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
        //Only able to press the button if a biometric was configured
//        boolean biometricConfigured = BiometricsHelper.checkFingerprintStatus(biometricManager) == BiometricsHelper.ConfigurationStatus.Configured ||
//                BiometricsHelper.checkPinStatus(biometricManager) == BiometricsHelper.ConfigurationStatus.Configured;
//
//        if (!biometricConfigured) {
//            AlertDialog.Builder builder = new AlertDialog.Builder(this);
//            builder.setTitle("");
//            builder.setMessage("");
//            builder.setCancelable(true);
//            builder.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//                @Override
//                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//
//                }
//
//                @Override
//                public void onNothingSelected(AdapterView<?> parent) {
//
//                }
//            });
//
//            builder.setOnCancelListener(v -> {
//                finish(false);
//            });
//        }
//        else {
            finish(true);
//        }
    }

    public void finish(boolean success) {
        Intent intent = new Intent(getIntent());
        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }
}
