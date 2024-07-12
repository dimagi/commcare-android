package org.commcare.activities.connect;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.commcare.activities.CommCareActivity;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectManager;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.utils.BiometricsHelper;
import org.javarosa.core.services.Logger;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;

/**
 * Gets the user to unlock ConnectID via screen unlock (fingerprint, PIN, pattern...)
 *
 * @author dviggiano
 */
public class ConnectIdBiometricUnlockActivity extends CommCareActivity<ConnectIdBiometricUnlockActivity>
        implements WithUIController {
    private BiometricPrompt.AuthenticationCallback biometricPromptCallbacks;
    private boolean attemptingFingerprint = false;
    private boolean allowPassword = false;
    private BiometricManager biometricManager;

    private ConnectIdBiometricUnlockActivityUiController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.connect_unlock_title));

        uiController.setupUI();

        allowPassword = getIntent().getStringExtra(ConnectConstants.ALLOW_PASSWORD).equals("true");

        biometricPromptCallbacks = preparePromptCallbacks();

        biometricManager = BiometricManager.from(this);

        if (BiometricsHelper.isFingerprintConfigured(this, biometricManager)) {
            //Automatically try fingerprint first
            performFingerprintUnlock();
        } else if (BiometricsHelper.isPinConfigured(this, biometricManager)) {
            performPinUnlock();
        } else if (allowPassword) {
            performPasswordUnlock();
        } else {
            Logger.exception("No unlock method available when trying to unlock ConnectID", new Exception("No unlock option"));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        uiController.refreshView();
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
        uiController = new ConnectIdBiometricUnlockActivityUiController(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if(!BiometricsHelper.handlePinUnlockActivityResult(requestCode, resultCode, intent)) {
            ConnectManager.handleFinishedActivity(requestCode, resultCode, intent);
        }

        super.onActivityResult(requestCode, resultCode, intent);
    }

    public void startAccountRecoveryWorkflow() {
        finish(false, false, true);
    }

    public void performFingerprintUnlock() {
        attemptingFingerprint = true;
        boolean allowOtherOptions = BiometricsHelper.isPinConfigured(this, biometricManager) ||
                allowPassword;
        BiometricsHelper.authenticateFingerprint(this, biometricManager, allowOtherOptions, biometricPromptCallbacks);
    }

    public void performPasswordUnlock() {
        finish(false, true, false);
    }

    public void performPinUnlock() {
        BiometricsHelper.authenticatePin(this, biometricManager, biometricPromptCallbacks);
    }

    private BiometricPrompt.AuthenticationCallback preparePromptCallbacks() {
        final Context context = this;
        return new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode,
                                              @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (attemptingFingerprint) {
                    attemptingFingerprint = false;
                    if (BiometricsHelper.isPinConfigured(context, biometricManager) &&
                            allowPassword) {
                        //Automatically try password, it's the only option
                        performPinUnlock();
                    } else {
                        //Automatically try password, it's the only option
                        performPasswordUnlock();
                    }
                }
            }

            @Override
            public void onAuthenticationSucceeded(
                    @NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                logSuccess();
                finish(true, false, false);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Authentication failed",
                                Toast.LENGTH_SHORT)
                        .show();
            }
        };
    }

    private void logSuccess() {
        String method = attemptingFingerprint ? AnalyticsParamValue.CCC_SIGN_IN_METHOD_FINGERPRINT
                : AnalyticsParamValue.CCC_SIGN_IN_METHOD_PIN;
        FirebaseAnalyticsUtil.reportCccSignIn(method);
    }

    private void finish(boolean success, boolean password, boolean recover) {
        Intent intent = new Intent(getIntent());

        intent.putExtra(ConnectConstants.PASSWORD, password);
        intent.putExtra(ConnectConstants.RECOVER, recover);

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }
}
