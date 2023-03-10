package org.commcare.activities;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import static com.google.zxing.integration.android.IntentIntegrator.REQUEST_CODE;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;

public class ConnectIdLoginActivity extends CommCareActivity<ConnectIdLoginActivity>
implements WithUIController {
    public enum RegistrationPhase {
        Initial, //Collect primary info: name, DOB, etc.
        Secrets, //Configure fingerprint, PIN, password, etc.
        Verify //Verify phone number via SMS
    }
    private static final String TAG = ConnectIdLoginActivity.class.getSimpleName();

    private static final int CONNECT_REGISTER_ACTIVITY = 0;
    private static final int CONNECT_VERIFY_ACTIVITY = 1;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo fingerprintPromptInfo;
    private BiometricPrompt.PromptInfo pinPromptInfo;

    private ConnectIdLoginActivityUIController uiController;
    private RegistrationPhase phase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        phase = RegistrationPhase.Initial;

        uiController.setupUI();

        biometricPrompt = preparePrompt();

        BiometricManager biometricManager = BiometricManager.from(this);
        configureFingerprintUnlock(biometricManager);
        configurePinUnlock(biometricManager);
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
        uiController = new ConnectIdLoginActivityUIController(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if(requestCode == CONNECT_REGISTER_ACTIVITY && resultCode == RESULT_OK) {
            phase = RegistrationPhase.Secrets;

            Intent i = new Intent(this, ConnectIdVerificationActivity.class);
            startActivityForResult(i, CONNECT_VERIFY_ACTIVITY);
        }
        else if(requestCode == CONNECT_VERIFY_ACTIVITY && resultCode == RESULT_OK) {
            //TODO: where does phone verification live? New activity?
            //Jumping to end of workflow for now, user logged in
            finish(true);
        }

        super.onActivityResult(requestCode, resultCode, intent);
    }
    public void startNewAccountWorkflow() {
        Intent i = new Intent(this, ConnectIdRegistrationActivity.class);
        startActivityForResult(i, CONNECT_REGISTER_ACTIVITY);
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
