package org.commcare.activities.connect;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;

import org.commcare.activities.CommCareActivity;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.utils.BiometricsHelper;

/**
 * Gets the user to unlock ConnectID (via fingerprint, PIN, or password)
 *
 * @author dviggiano
 */
public class ConnectIdLoginActivity extends CommCareActivity<ConnectIdLoginActivity>
        implements WithUIController {
    private BiometricPrompt.AuthenticationCallback biometricPromptCallbacks;
    private boolean attemptingFingerprint = false;
    private boolean allowPassword = false;
    private BiometricManager biometricManager;

    private ConnectIdLoginActivityUiController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.connect_unlock_title));

        uiController.setupUI();

        allowPassword = getIntent().getStringExtra(ConnectIdConstants.ALLOW_PASSWORD).equals("true");

        biometricPromptCallbacks = preparePromptCallbacks();

        biometricManager = BiometricManager.from(this);

        if (BiometricsHelper.isFingerprintConfigured(this, biometricManager)) {
            //Automatically try fingerprint first
            performFingerprintUnlock();
        } else if (!BiometricsHelper.isPinConfigured(this, biometricManager)) {
            //Automatically try password, it's the only option
            performPasswordUnlock();
        } else {
            if (allowPassword) {
                //Show options for PIN or password
                uiController.showAdditionalOptions();
            } else {
                //PIN is the only option
                performPinUnlock();
            }
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
        uiController = new ConnectIdLoginActivityUiController(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        BiometricsHelper.handlePinUnlockActivityResult(requestCode, resultCode, intent);
        ConnectIdManager.handleFinishedActivity(requestCode, resultCode, intent);

        super.onActivityResult(requestCode, resultCode, intent);
    }

    public void startAccountRecoveryWorkflow() {
        finish(false, false, true);
    }

    public void performFingerprintUnlock() {
        attemptingFingerprint = true;
        BiometricsHelper.authenticateFingerprint(this, biometricManager, biometricPromptCallbacks);
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
                    if (!BiometricsHelper.isPinConfigured(context, biometricManager)) {
                        //Automatically try password, it's the only option
                        performPasswordUnlock();
                    } else {
                        //Show options for PIN or password
                        uiController.showAdditionalOptions();
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
                //TODO: Change path before Android takes action
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

        intent.putExtra(ConnectIdConstants.PASSWORD, password);
        intent.putExtra(ConnectIdConstants.RECOVER, recover);

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }
}
