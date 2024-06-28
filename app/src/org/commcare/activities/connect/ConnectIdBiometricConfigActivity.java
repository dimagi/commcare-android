package org.commcare.activities.connect;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;

import org.commcare.activities.CommCareActivity;
import org.commcare.connect.ConnectConstants;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.utils.BiometricsHelper;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.Logger;

/**
 * Shows the page for configuring biometrics (fingerprint and/or PIN)
 *
 * @author dviggiano
 */
public class ConnectIdBiometricConfigActivity extends CommCareActivity<ConnectIdBiometricConfigActivity>
        implements WithUIController {

    private ConnectIdBiometricConfigActivityUiController uiController;
    private BiometricManager biometricManager;

    private BiometricPrompt.AuthenticationCallback biometricPromptCallbacks;

    private boolean attemptingFingerprint = false;
    private boolean allowPassword = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.connect_verify_title));
        biometricManager = BiometricManager.from(this);
        biometricPromptCallbacks = preparePromptCallbacks();
        uiController.setupUI();
        allowPassword = getIntent().hasExtra(ConnectConstants.ALLOW_PASSWORD) && getIntent().getStringExtra(ConnectConstants.ALLOW_PASSWORD).equals("true");;
        BiometricsHelper.ConfigurationStatus fingerprint = BiometricsHelper.checkFingerprintStatus(this,
                biometricManager);
        BiometricsHelper.ConfigurationStatus pin = BiometricsHelper.checkPinStatus(this, biometricManager);
        if (fingerprint == BiometricsHelper.ConfigurationStatus.NotAvailable &&
                pin == BiometricsHelper.ConfigurationStatus.NotAvailable) {
            //Skip to password-only workflow
//            finish(true, true);
        } else {
            updateState(fingerprint, pin);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        BiometricsHelper.ConfigurationStatus fingerprint = BiometricsHelper.checkFingerprintStatus(this,
                biometricManager);
        BiometricsHelper.ConfigurationStatus pin = BiometricsHelper.checkPinStatus(this, biometricManager);
        updateState(fingerprint, pin);
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
        uiController = new ConnectIdBiometricConfigActivityUiController(this);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        return CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId);
    }

    public void updateState(BiometricsHelper.ConfigurationStatus fingerprintStatus,
                            BiometricsHelper.ConfigurationStatus pinStatus) {
        String titleText = getString(R.string.connect_verify_title);
        String messageText = getString(R.string.connect_verify_message);
        String fingerprintButtonText = null;
        String pinButtonText = null;
        if (fingerprintStatus == BiometricsHelper.ConfigurationStatus.Configured) {
            //Show fingerprint but not PIN
            titleText = getString(R.string.connect_verify_use_fingerprint_long);
            messageText = getString(R.string.connect_verify_fingerprint_configured);
            fingerprintButtonText = getString(R.string.connect_verify_agree);
        } else if (pinStatus == BiometricsHelper.ConfigurationStatus.Configured) {
            //Show PIN, and fingerprint if configurable
            titleText = getString(R.string.connect_verify_use_pin_long);
            messageText = getString(R.string.connect_verify_pin_configured);
            pinButtonText = getString(R.string.connect_verify_agree);
            fingerprintButtonText = fingerprintStatus == BiometricsHelper.ConfigurationStatus.NotConfigured ?
                    getString(R.string.connect_verify_configure_fingerprint) : null;
        } else {
            //Show anything configurable
            if (fingerprintStatus == BiometricsHelper.ConfigurationStatus.NotConfigured) {
                fingerprintButtonText = getString(R.string.connect_verify_configure_fingerprint);
            }
            if (pinStatus == BiometricsHelper.ConfigurationStatus.NotConfigured) {
                pinButtonText = getString(R.string.connect_verify_configure_pin);
            }
        }

        uiController.setTitleText(titleText);
        uiController.setMessageText(messageText);

        boolean showFingerprint = fingerprintButtonText != null;
        boolean showPin = pinButtonText != null;

        uiController.updateFingerprint(fingerprintButtonText);
        uiController.updatePin(pinButtonText);

        uiController.setOrVisibility(showFingerprint && showPin);
    }

    public void handleFingerprintButton() {

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

//    public void handlePinButton() {
//        BiometricsHelper.ConfigurationStatus pin = BiometricsHelper.checkPinStatus(this, biometricManager);
//        if (pin == BiometricsHelper.ConfigurationStatus.Configured) {
//            finish(true, false);
//        } else if (!BiometricsHelper.configurePin(this)) {
//            finish(true, true);
//        }
//    }

    private void finish(boolean success, boolean password, boolean recover) {
        Intent intent = new Intent(getIntent());

        intent.putExtra(ConnectConstants.PASSWORD, password);
        intent.putExtra(ConnectConstants.RECOVER, recover);

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }
}
