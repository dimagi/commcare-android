package org.commcare.utils;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import static com.google.zxing.integration.android.IntentIntegrator.REQUEST_CODE;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;

import androidx.biometric.BiometricManager;

public class BiometricsHelper {
    public enum ConfigurationStatus {
        NotAvailable,
        NotConfigured,
        Configured
    }

    private static final int StrongBiometric = BIOMETRIC_STRONG;
    private static final int PinBiometric = DEVICE_CREDENTIAL;

    public static ConfigurationStatus checkFingerprintStatus(BiometricManager biometricManager) {
        return checkStatus(biometricManager, StrongBiometric);
    }

    public static boolean isFingerprintAvailable(BiometricManager biometricManager) {
        int status = canAuthenticate(biometricManager, StrongBiometric);
        return status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ||
                status == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public static boolean isFingerprintConfigured(BiometricManager biometricManager) {
        return canAuthenticate(biometricManager, StrongBiometric) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public static void configureFingerprint(Activity activity) {
        configureBiometric(activity, StrongBiometric);
    }

    public static ConfigurationStatus checkPinStatus(BiometricManager biometricManager) {
        return checkStatus(biometricManager, PinBiometric);
    }

    public static boolean isPinAvailable(BiometricManager biometricManager) {
        int status = canAuthenticate(biometricManager, PinBiometric);
        return status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ||
                status == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public static boolean isPinConfigured(BiometricManager biometricManager) {
        return canAuthenticate(biometricManager, PinBiometric) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public static void configurePin(Activity activity) {
        configureBiometric(activity, PinBiometric);
    }

    public static ConfigurationStatus checkStatus(BiometricManager biometricManager, int authenticator) {
        int val = canAuthenticate(biometricManager, authenticator);
        switch(val) {
            case BiometricManager.BIOMETRIC_SUCCESS -> { return ConfigurationStatus.Configured; }
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> { return ConfigurationStatus.NotConfigured; }
        }

        return ConfigurationStatus.NotAvailable;
    }

    private static int canAuthenticate(BiometricManager biometricManager, int authenticator) {
        return biometricManager.canAuthenticate(authenticator);
    }

    private static void configureBiometric(Activity activity, int authenticator) {
        // Prompts the user to create credentials that your app accepts.
        final Intent enrollIntent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
        enrollIntent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                authenticator);
        activity.startActivityForResult(enrollIntent, REQUEST_CODE);
    }
}
