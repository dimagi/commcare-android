package org.commcare.activities;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;

public class ConnectIDLoginActivity extends CommCareActivity<ConnectIDLoginActivity>
implements WithUIController {

    private static final String TAG = ConnectIDLoginActivity.class.getSimpleName();

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo fingerprintPromptInfo;
    private BiometricPrompt.PromptInfo pinPromptInfo;

    private ConnectIDLoginActivityUIController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiController.setupUI();

        biometricPrompt = preparePrompt();

        BiometricManager biometricManager = BiometricManager.from(this);
        configureFingerprintUnlock(biometricManager);
        configurePinUnlock(biometricManager);

        if(fingerprintPromptInfo != null) {
            if(pinPromptInfo == null) {
                performFingerprintUnlock();
            }
        }
        else if(pinPromptInfo != null) {
            performPinUnlock();
        }
    }

    @Override
    protected  void onResume() {
        super.onResume();

        uiController.refreshView();
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    public void initUIController() {
        uiController = new ConnectIDLoginActivityUIController(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if(ConnectIDManager.isConnectIDActivity(requestCode)) {
            ConnectIDManager.handleFinishedActivity(requestCode, resultCode, intent);
        }

        super.onActivityResult(requestCode, resultCode, intent);
    }
    public void startAccountRecoveryWorkflow() {
        Toast.makeText(getApplicationContext(),
                "Not ready yet", Toast.LENGTH_SHORT).show();
    }

    public void performFingerprintUnlock() {
        biometricPrompt.authenticate(fingerprintPromptInfo);
    }

    public void performPinUnlock() {
        biometricPrompt.authenticate(pinPromptInfo);
    }

    private BiometricPrompt preparePrompt() {
        return new BiometricPrompt(this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode,
                                              @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(),
                                "Authentication error: " + errString, Toast.LENGTH_SHORT)
                        .show();
                finish(false);
            }

            @Override
            public void onAuthenticationSucceeded(
                    @NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Toast.makeText(getApplicationContext(),
                        "Authentication succeeded!", Toast.LENGTH_SHORT).show();
                finish(true);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Authentication failed",
                                Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void finish(boolean success) {
        Intent intent = new Intent(getIntent());
        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    private void configureFingerprintUnlock(BiometricManager biometricManager) {
        fingerprintPromptInfo = null;
        boolean enableFingerprint = BiometricsHelper.isFingerprintConfigured(biometricManager);
        uiController.setFingerprintEnabled(enableFingerprint);

        if(enableFingerprint) {
            fingerprintPromptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Biometric unlock for my app")
                    .setSubtitle("Unlock using your biometric credential")
                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .build();
        }
    }

    private void configurePinUnlock(BiometricManager biometricManager) {
        pinPromptInfo = null;
        boolean enablePin = BiometricsHelper.isPinConfigured(biometricManager);

        uiController.setPinEnabled(enablePin);

        if(enablePin) {
            pinPromptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("PIN unlock for my app")
                    .setSubtitle("Unlock using your PIN")
                    .setAllowedAuthenticators(DEVICE_CREDENTIAL)
                    .build();
        }
    }
}
