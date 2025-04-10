package org.commcare.utils;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import com.google.zxing.integration.android.IntentIntegrator;

import org.commcare.connect.ConnectConstants;
import org.commcare.dalvik.R;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

/**
 * Helper class for biometric configuration and verification.
 * Provides methods to check biometric availability, configure biometrics,
 * and perform authentication using fingerprint or PIN or Password.
 * Supports both biometric strong authentication and device credentials.
 */
public class BiometricsHelper {

    /**
     * Enum representing the availability and configuration status of a biometric method.
     */
    public enum ConfigurationStatus {
        NotAvailable,  // Biometrics not available on the device
        NotConfigured, // Biometrics available but not set up
        Configured     // Biometrics set up and ready for authentication
    }

    /**
     * Checks the fingerprint authentication status.
     *
     * @param context          The application context.
     * @param biometricManager The BiometricManager instance.
     * @return The fingerprint configuration status.
     */
    public static ConfigurationStatus checkFingerprintStatus(Context context, BiometricManager biometricManager) {
        return checkStatus(context, biometricManager, BiometricManager.Authenticators.BIOMETRIC_STRONG);
    }

    /**
     * Determines if fingerprint authentication is configured on the device.
     *
     * @param context          The application context.
     * @param biometricManager The BiometricManager instance.
     * @return True if fingerprint authentication is configured, false otherwise.
     */
    public static boolean isFingerprintConfigured(Context context, BiometricManager biometricManager) {
        return checkStatus(context, biometricManager, BiometricManager.Authenticators.BIOMETRIC_STRONG) == ConfigurationStatus.Configured;
    }

    /**
     * Prompts the user to configure fingerprint authentication.
     *
     * @param activity The current activity.
     * @return True if the configuration process starts successfully, false otherwise.
     */
    public static boolean configureFingerprint(Activity activity) {
        return configureBiometric(activity, BiometricManager.Authenticators.BIOMETRIC_STRONG);
    }

    /**
     * Initiates fingerprint authentication.
     *
     * @param activity          The fragment activity.
     * @param biometricManager  The BiometricManager instance.
     * @param allowExtraOptions Whether to allow alternative authentication options (e.g., PIN).
     * @param callback          The callback for authentication results.
     */
    public static void authenticateFingerprint(FragmentActivity activity,
                                               BiometricManager biometricManager,
                                               boolean allowExtraOptions,
                                               BiometricPrompt.AuthenticationCallback callback) {
        if (BiometricsHelper.isFingerprintConfigured(activity, biometricManager)) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                // For Android 11+ (R), use PIN as an alternative unlock method
                authenticatePin(activity, biometricManager, callback);
            } else {
                BiometricPrompt prompt = new BiometricPrompt(activity,
                        ContextCompat.getMainExecutor(activity),
                        callback);

                BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(activity.getString(R.string.connect_unlock_fingerprint_title))
                        .setSubtitle(activity.getString(R.string.connect_unlock_fingerprint_message))
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG);

                if (allowExtraOptions) {
                    builder.setNegativeButtonText(activity.getString(R.string.connect_unlock_other_options));
                }

                prompt.authenticate(builder.build());
            }
        }
    }

    /**
     * Checks the status of PIN-based authentication.
     *
     * @param context          The application context.
     * @param biometricManager The BiometricManager instance.
     * @return The PIN configuration status.
     */
    public static ConfigurationStatus checkPinStatus(Context context, BiometricManager biometricManager) {
        int authStatus = canAuthenticate(context, biometricManager, BiometricManager.Authenticators.DEVICE_CREDENTIAL);

        if (authStatus == BiometricManager.BIOMETRIC_SUCCESS) {
            return ConfigurationStatus.Configured;
        } else {
            return ConfigurationStatus.NotConfigured;
        }
    }

    /**
     * Determines if PIN authentication is configured on the device.
     *
     * @param context          The application context.
     * @param biometricManager The BiometricManager instance.
     * @return True if PIN authentication is configured, false otherwise.
     */
    public static boolean isPinConfigured(Context context, BiometricManager biometricManager) {
        return checkStatus(context, biometricManager, BiometricManager.Authenticators.DEVICE_CREDENTIAL) == ConfigurationStatus.Configured;
    }

    /**
     * Prompts the user to configure PIN-based authentication.
     *
     * @param activity The current activity.
     * @return True if the configuration process starts successfully, false otherwise.
     */
    public static boolean configurePin(Activity activity) {
        return configureBiometric(activity, BiometricManager.Authenticators.DEVICE_CREDENTIAL);
    }

    private static BiometricPrompt.AuthenticationCallback biometricPromptCallbackHolder;

    /**
     * Initiates PIN-based authentication.
     *
     * @param activity                The fragment activity.
     * @param biometricManager        The BiometricManager instance.
     * @param biometricPromptCallback The callback for authentication results.
     */
    public static void authenticatePin(FragmentActivity activity, BiometricManager biometricManager,
                                       BiometricPrompt.AuthenticationCallback biometricPromptCallback) {
        if (BiometricsHelper.isPinConfigured(activity, biometricManager)) {
            biometricPromptCallbackHolder = biometricPromptCallback;
            KeyguardManager manager = (KeyguardManager)activity.getSystemService(Context.KEYGUARD_SERVICE);
            activity.startActivityForResult(
                    manager.createConfirmDeviceCredentialIntent(
                            activity.getString(R.string.connect_unlock_title),
                            activity.getString(R.string.connect_unlock_message)),
                    ConnectConstants.CONNECT_UNLOCK_PIN);
        }
    }

    /**
     * Handles the result of the PIN authentication activity.
     *
     * @param requestCode The request code for the authentication intent.
     * @param resultCode  The result code from the authentication activity.
     * @return True if the request was handled, false otherwise.
     */
    public static boolean handlePinUnlockActivityResult(int requestCode, int resultCode) {
        if (requestCode == ConnectConstants.CONNECT_UNLOCK_PIN) {
            if (resultCode == Activity.RESULT_OK) {
                biometricPromptCallbackHolder.onAuthenticationSucceeded(null);
            } else {
                biometricPromptCallbackHolder.onAuthenticationFailed();
            }
            return true;
        }
        return false;
    }

    /**
     * Checks the biometric authentication status for a specific authentication method.
     *
     * @param context          The application context.
     * @param biometricManager The BiometricManager instance.
     * @param authenticator    The authenticator type (e.g., fingerprint, PIN).
     * @return The biometric configuration status.
     */
    public static ConfigurationStatus checkStatus(Context context, BiometricManager biometricManager,
                                                  int authenticator) {
        int val = canAuthenticate(context, biometricManager, authenticator);
        switch (val) {
            case BiometricManager.BIOMETRIC_SUCCESS -> {
                return ConfigurationStatus.Configured;
            }
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                return ConfigurationStatus.NotConfigured;
            }
        }
        return ConfigurationStatus.NotAvailable;
    }

    private static int canAuthenticate(Context context, BiometricManager biometricManager, int authenticator) {
        if (authenticator == BiometricManager.Authenticators.DEVICE_CREDENTIAL && Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            KeyguardManager manager = (KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
            boolean isSecure = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                    manager.isDeviceSecure() : manager.isKeyguardSecure();

            return isSecure ? BiometricManager.BIOMETRIC_SUCCESS : BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED;
        }
        return biometricManager.canAuthenticate(authenticator);
    }

    private static boolean configureBiometric(Activity activity, int authenticator) {
        Intent enrollIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            enrollIntent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
            enrollIntent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, authenticator);
        } else if (authenticator == BiometricManager.Authenticators.BIOMETRIC_STRONG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            enrollIntent = new Intent(Settings.ACTION_FINGERPRINT_ENROLL);
        } else {
            return false;
        }

        activity.startActivityForResult(enrollIntent, IntentIntegrator.REQUEST_CODE);
        return true;
    }

    /**
     * Initiates password-based authentication.
     *
     * @param activity                The fragment activity.
     * @param biometricManager        The BiometricManager instance.
     * @param biometricPromptCallback The callback for authentication results.
     */
    public static void authenticatePassword(Activity activity, BiometricManager biometricManager,
                                            BiometricPrompt.AuthenticationCallback biometricPromptCallback) {
        if (isPasswordConfigured(activity, biometricManager)) {
            biometricPromptCallbackHolder = biometricPromptCallback;
            KeyguardManager manager = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
            activity.startActivityForResult(
                    manager.createConfirmDeviceCredentialIntent(
                            activity.getString(R.string.connect_unlock_title),
                            activity.getString(R.string.connect_unlock_message)),
                    ConnectConstants.CONNECT_UNLOCK_PASSWORD);
        }
    }

    /**
     * Determines if password authentication is configured on the device.
     *
     * @param context          The application context.
     * @param biometricManager The BiometricManager instance.
     * @return True if password authentication is configured, false otherwise.
     */
    public static boolean isPasswordConfigured(Context context, BiometricManager biometricManager) {
        return checkStatus(context, biometricManager, BiometricManager.Authenticators.DEVICE_CREDENTIAL) == ConfigurationStatus.Configured;
    }

    /**
     * Handles the result of the password authentication activity.
     *
     * @param requestCode The request code for the authentication intent.
     * @param resultCode  The result code from the authentication activity.
     * @return True if the request was handled, false otherwise.
     */
    public static boolean handlePasswordUnlockActivityResult(int requestCode, int resultCode) {
        if (requestCode == ConnectConstants.CONNECT_UNLOCK_PASSWORD) {
            if (resultCode == Activity.RESULT_OK) {
                biometricPromptCallbackHolder.onAuthenticationSucceeded(null);
            } else {
                biometricPromptCallbackHolder.onAuthenticationFailed();
            }
            return true;
        }
        return false;
    }
}
