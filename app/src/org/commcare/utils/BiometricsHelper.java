package org.commcare.utils;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import static com.google.zxing.integration.android.IntentIntegrator.REQUEST_CODE;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import org.commcare.activities.connect.ConnectIDConstants;
import org.commcare.activities.connect.ConnectIDManager;
import org.commcare.dalvik.R;

public class BiometricsHelper {
    public enum ConfigurationStatus {
        NotAvailable,
        NotConfigured,
        Configured
    }

    private static final int StrongBiometric = BIOMETRIC_STRONG;
    private static final int PinBiometric = DEVICE_CREDENTIAL;

    public static ConfigurationStatus checkFingerprintStatus(Context context, BiometricManager biometricManager) {
        return checkStatus(context, biometricManager, StrongBiometric);
    }

    public static boolean isFingerprintConfigured(Context context, BiometricManager biometricManager) {
        return checkStatus(context, biometricManager, StrongBiometric) == ConfigurationStatus.Configured;
    }

    public static void configureFingerprint(Activity activity) {
        configureBiometric(activity, StrongBiometric);
    }

    public static void authenticateFingerprint(FragmentActivity activity, BiometricManager biometricManager, BiometricPrompt.AuthenticationCallback biometricPromptCallback) {
        if(BiometricsHelper.isFingerprintConfigured(activity, biometricManager)) {
            BiometricPrompt prompt = new BiometricPrompt(activity,
                    ContextCompat.getMainExecutor(activity),
                    biometricPromptCallback);

            prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(activity.getString(R.string.connect_unlock_fingerprint_title))
                    .setSubtitle(activity.getString(R.string.connect_unlock_fingerprint_message))
                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .setNegativeButtonText(activity.getString(R.string.connect_unlock_other_options))
                    .build());
        }
    }


    public static ConfigurationStatus checkPinStatus(Context context, BiometricManager biometricManager) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            return checkStatus(context, biometricManager, PinBiometric);
        }
        else {
            KeyguardManager manager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            boolean isSecure = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? manager.isDeviceSecure() : manager.isKeyguardSecure();

            return isSecure ? ConfigurationStatus.Configured : ConfigurationStatus.NotConfigured;
        }
    }

    public static boolean isPinConfigured(Context context, BiometricManager biometricManager) {
        return checkStatus(context, biometricManager, PinBiometric) == ConfigurationStatus.Configured;
    }

    public static void configurePin(Activity activity) {
        configureBiometric(activity, PinBiometric);
    }

    private static BiometricPrompt.AuthenticationCallback biometricPromptCallbackHolder;
    public static void authenticatePin(FragmentActivity activity, BiometricManager biometricManager, BiometricPrompt.AuthenticationCallback biometricPromptCallback) {
        if (BiometricsHelper.isPinConfigured(activity, biometricManager)) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                BiometricPrompt prompt = new BiometricPrompt(activity,
                        ContextCompat.getMainExecutor(activity),
                        biometricPromptCallback);

                prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(activity.getString(R.string.connect_unlock_pin_title))
                        .setSubtitle(activity.getString(R.string.connect_unlock_pin_message))
                        .setAllowedAuthenticators(DEVICE_CREDENTIAL)
                        .build());
            }
            //else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                //manager.isDeviceSecure()
            //}
            else {
                //manager.isKeyguardSecure()
                biometricPromptCallbackHolder = biometricPromptCallback;
                KeyguardManager manager = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
                activity.startActivityForResult(manager.createConfirmDeviceCredentialIntent(activity.getString(R.string.connect_unlock_pin_title),
                    activity.getString(R.string.connect_unlock_pin_message)), ConnectIDConstants.CONNECT_UNLOCK_PIN);
            }
        }
    }

    public static void handlePinUnlockActivityResult(int requestCode, int resultCode, Intent intent) {
        if(requestCode == ConnectIDConstants.CONNECT_UNLOCK_PIN) {
            if(resultCode == Activity.RESULT_OK) {
                biometricPromptCallbackHolder.onAuthenticationSucceeded(null);
            }
            else {
                biometricPromptCallbackHolder.onAuthenticationFailed();
            }
        }
    }

    public static ConfigurationStatus checkStatus(Context context, BiometricManager biometricManager, int authenticator) {
        int val = canAuthenticate(context, biometricManager, authenticator);
        switch(val) {
            case BiometricManager.BIOMETRIC_SUCCESS -> { return ConfigurationStatus.Configured; }
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> { return ConfigurationStatus.NotConfigured; }
        }

        return ConfigurationStatus.NotAvailable;
    }

    private static int canAuthenticate(Context context, BiometricManager biometricManager, int authenticator) {
        if(authenticator == PinBiometric && Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            KeyguardManager manager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);

            boolean isSecure = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? manager.isDeviceSecure() : manager.isKeyguardSecure();

            return isSecure ? BiometricManager.BIOMETRIC_SUCCESS : BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED;
        }

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
