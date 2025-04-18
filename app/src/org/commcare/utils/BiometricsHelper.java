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
import org.javarosa.core.services.Logger;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.Locale;

/**
 * Helper class for biometric configuration and verification
 *
 * @author dviggiano
 */
public class BiometricsHelper {
    /**
     * Enum simplifying the availability of a biometric method
     */
    public enum ConfigurationStatus {
        NotAvailable,
        NotConfigured,
        Configured
    }

    private static final int StrongBiometric = BiometricManager.Authenticators.BIOMETRIC_STRONG;
    private static final int PinBiometric = BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    public static ConfigurationStatus checkFingerprintStatus(Context context, BiometricManager biometricManager) {
        return checkStatus(context, biometricManager, StrongBiometric);
    }

    public static boolean isFingerprintConfigured(Context context, BiometricManager biometricManager) {
        return checkFingerprintStatus(context, biometricManager) == ConfigurationStatus.Configured;
    }

    public static boolean configureFingerprint(Activity activity) {
        return configureBiometric(activity, StrongBiometric);
    }

    public static void authenticateFingerprint(FragmentActivity activity,
                                               BiometricManager biometricManager,
                                               BiometricPrompt.AuthenticationCallback biometricPromptCallback) {
        authenticatePinOrBiometric(activity,biometricManager, biometricPromptCallback);
    }


    public static ConfigurationStatus checkPinStatus(Context context, BiometricManager biometricManager) {
        return checkStatus(context, biometricManager, PinBiometric);
    }

    public static boolean isPinConfigured(Context context, BiometricManager biometricManager) {
        return checkPinStatus(context, biometricManager) == ConfigurationStatus.Configured;
    }

    public static boolean configurePin(Activity activity) {
        return configureBiometric(activity, PinBiometric);
    }

    private static BiometricPrompt.AuthenticationCallback biometricPromptCallbackHolder;

    public static void authenticatePin(FragmentActivity activity, BiometricManager biometricManager,
                                       BiometricPrompt.AuthenticationCallback biometricPromptCallback) {

        authenticatePinOrBiometric(activity,biometricManager, biometricPromptCallback);
    }

    public static void authenticatePinOrBiometric(FragmentActivity activity, BiometricManager biometricManager,
                                                  BiometricPrompt.AuthenticationCallback biometricPromptCallback){
        if (BiometricsHelper.isPinConfigured(activity, biometricManager)|| BiometricsHelper.isFingerprintConfigured(activity, biometricManager)) {
            BiometricPrompt prompt = new BiometricPrompt(activity,
                    ContextCompat.getMainExecutor(activity),
                    biometricPromptCallback);

            prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(activity.getString(R.string.connect_unlock_title))
                    .setSubtitle(activity.getString(R.string.connect_unlock_message))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL |
                            BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .build());
        }
    }

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
            default -> {
                Logger.exception("Unhandled biometric status", new Exception(
                        String.format(Locale.getDefault(), "Mode %d encountered unexpected status %d",
                                authenticator, val)
                ));

                return ConfigurationStatus.NotAvailable;
            }
        }
    }

    private static int canAuthenticate(Context context, BiometricManager biometricManager, int authenticator) {
        if (authenticator == PinBiometric && Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            KeyguardManager manager = (KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);

            boolean isSecure = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                    manager.isDeviceSecure() :
                    manager.isKeyguardSecure();

            return isSecure ? BiometricManager.BIOMETRIC_SUCCESS : BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED;
        }

        return biometricManager.canAuthenticate(authenticator);
    }

    private static boolean configureBiometric(Activity activity, int authenticator) {
        // Prompts the user to create credentials that your app accepts.
        Intent enrollIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //Best case, handles both fingerprint and PIN
            enrollIntent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
            enrollIntent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    authenticator);
        } else if (authenticator == StrongBiometric && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            //An alternative for fingerprint enroll that might be available
            enrollIntent = new Intent(Settings.ACTION_FINGERPRINT_ENROLL);
        } else {
            //No way to enroll, have to fail
            Logger.exception("Biometric config failed", new Exception(String.format(Locale.getDefault(),
                    "No available enroll activity for authenticator %d", authenticator)));
            return false;
        }

        activity.startActivityForResult(enrollIntent, IntentIntegrator.REQUEST_CODE);
        return true;
    }
}
