package org.commcare.fragments.connectId;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectIDManager;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenConnectVerifyBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.BiometricsHelper;
import org.commcare.utils.CommCareNavController;
import org.javarosa.core.services.Logger;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import static android.app.Activity.RESULT_OK;
import static org.commcare.fragments.connectId.ConnectIdPasswordVerificationFragment.PASSWORD_LOCK;

/**
 * {@link Fragment} subclass for helping the user choose or configure their biometric.
 */
public class ConnectIdBiometricConfigFragment extends Fragment {
    private BiometricManager biometricManager;
    private int callingActivity;
    private boolean allowPassword = false;
    private boolean attemptingFingerprint = false;
    private BiometricPrompt.AuthenticationCallback biometricPromptCallbacks;
    private static final String KEY_CALLING_ACTIVITY = "calling_activity";
    private static final String KEY_ALLOW_PASSWORD = "allow_password";
    private static final String KEY_ATTEMPTING_FINGERPRINT = "attempting_fingerprint";
    private ScreenConnectVerifyBinding binding;


    public ConnectIdBiometricConfigFragment() {
        // Required empty public constructor
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CALLING_ACTIVITY, callingActivity);
        outState.putBoolean(KEY_ALLOW_PASSWORD, allowPassword);
        outState.putBoolean(KEY_ATTEMPTING_FINGERPRINT, attemptingFingerprint);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadSavedState(savedInstanceState);

    }

    @Override
    public void onResume() {
        super.onResume();
        updateState();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = ScreenConnectVerifyBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        biometricManager = BiometricManager.from(requireActivity());
        biometricPromptCallbacks = preparePromptCallbacks();
        if (getArguments() != null) {
            callingActivity = ConnectIdBiometricConfigFragmentArgs.fromBundle(getArguments()).getCallingClass();
        }
        binding.connectVerifyFingerprintButton.setOnClickListener(v -> handleFingerprintButton());
        binding.connectVerifyPinButton.setOnClickListener(v -> handlePinButton());
        requireActivity().setTitle(R.string.connect_appbar_title_app_lock);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void loadSavedState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            callingActivity = savedInstanceState.getInt(KEY_CALLING_ACTIVITY);
            allowPassword = savedInstanceState.getBoolean(KEY_ALLOW_PASSWORD);
            attemptingFingerprint = savedInstanceState.getBoolean(KEY_ATTEMPTING_FINGERPRINT);
        }
    }

    private BiometricPrompt.AuthenticationCallback preparePromptCallbacks() {
        final Context context = requireActivity();
        return new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode,
                                              @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (attemptingFingerprint) {
                    attemptingFingerprint = false;
                    if (BiometricsHelper.isPinConfigured(context, biometricManager) &&
                            allowPassword) {
                        //Automatically try PIN
                        performPinUnlock();
                        return;
                    }
                }

                String whyNoPin = allowPassword ? "configured" : "allowed";
                Logger.exception("Exhausted biometrics", new Exception(String.format(Locale.getDefault(),
                        "Fingerprint error and PIN isn't %s: %s (%d)", whyNoPin, errString, errorCode)));

                String message = context.getString(R.string.connect_verify_configuration_failed, errString);
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(
                    @NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                logSuccess();
                finish(true, false);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(requireActivity().getApplicationContext(), "Authentication failed",
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

    public void updateState() {
        BiometricsHelper.ConfigurationStatus fingerprint = BiometricsHelper.checkFingerprintStatus(getActivity(),
                biometricManager);
        BiometricsHelper.ConfigurationStatus pin = BiometricsHelper.checkPinStatus(getActivity(), biometricManager);

        if (fingerprint == BiometricsHelper.ConfigurationStatus.NotAvailable &&
                pin == BiometricsHelper.ConfigurationStatus.NotAvailable) {
            Logger.exception("No biometrics", new Exception(
                    "No biometric options available during biometric config"));

            finish(true, true);
            return;
        }

        String titleText = getString(R.string.connect_verify_title);
        String messageText = getString(R.string.connect_verify_message);
        String fingerprintButtonText = null;
        String pinButtonText = null;
        if (fingerprint == BiometricsHelper.ConfigurationStatus.Configured) {
            //Show fingerprint but not PIN
            titleText = getString(R.string.connect_verify_use_fingerprint_long);
            messageText = getString(R.string.connect_verify_fingerprint_configured);
            fingerprintButtonText = getString(R.string.connect_verify_agree);
        } else if (pin == BiometricsHelper.ConfigurationStatus.Configured) {
            //Show PIN, and fingerprint if configurable
            titleText = getString(R.string.connect_verify_use_pin_long);
            messageText = getString(R.string.connect_verify_pin_configured);
            pinButtonText = getString(R.string.connect_verify_agree);
            fingerprintButtonText = fingerprint == BiometricsHelper.ConfigurationStatus.NotConfigured ?
                    getString(R.string.connect_verify_configure_fingerprint) : null;
        } else {
            //Show anything configurable
            if (fingerprint == BiometricsHelper.ConfigurationStatus.NotConfigured) {
                fingerprintButtonText = getString(R.string.connect_verify_configure_fingerprint);
            }
            if (pin == BiometricsHelper.ConfigurationStatus.NotConfigured) {
                pinButtonText = getString(R.string.connect_verify_configure_pin);
            }
        }

        binding.connectVerifyTitle.setText(titleText);
        binding.connectVerifyMessage.setText(messageText);

        boolean showFingerprint = fingerprintButtonText != null;
        boolean showPin = pinButtonText != null;

        updateFingerprint(fingerprintButtonText);
        updatePin(pinButtonText);

        binding.connectVerifyOr.setVisibility(showFingerprint && showPin ? View.VISIBLE : View.INVISIBLE);
    }

    private void updateFingerprint(String fingerprintButtonText) {
        boolean showFingerprint = fingerprintButtonText != null;
        binding.connectVerifyFingerprintContainer.setVisibility(showFingerprint ? View.VISIBLE : View.GONE);
        if (showFingerprint) {
            binding.connectVerifyFingerprintButton.setText(fingerprintButtonText);
        }
    }

    private void updatePin(String pinButtonText) {
        boolean showPin = pinButtonText != null;
        binding.connectVerifyPinContainer.setVisibility(showPin ? View.VISIBLE : View.GONE);
        if (showPin) {
            binding.connectVerifyPinButton.setText(pinButtonText);
        }
    }

    public void handleFinishedPinActivity(int requestCode, int resultCode, Intent intent) {
        if (requestCode == PASSWORD_LOCK) {
            //This route was for when the user failed to enter the correct password several times
            //Obsolete now, should be removed
            Logger.exception("Biometric enrollment failed", new Exception(
                    "Hit the PASSWORD_LOCK path, should never happen"));
            finish(true, true);
        }
        if (requestCode == ConnectConstants.CONNECT_UNLOCK_PIN) {
            finish(true, false);
        }
    }

    private void performFingerprintUnlock() {
        attemptingFingerprint = true;
        BiometricsHelper.authenticateFingerprint(requireActivity(), biometricManager, biometricPromptCallbacks);
    }

    private void performPinUnlock() {
        BiometricsHelper.authenticatePin(requireActivity(), biometricManager, biometricPromptCallbacks);
    }


    private void handleFingerprintButton() {
        BiometricsHelper.ConfigurationStatus fingerprint = BiometricsHelper.checkFingerprintStatus(getActivity(),
                biometricManager);
        if (fingerprint == BiometricsHelper.ConfigurationStatus.Configured) {
            performFingerprintUnlock();
        } else if (!BiometricsHelper.configureFingerprint(getActivity())) {
            finish(true, true);
        }
    }

    private void handlePinButton() {
        BiometricsHelper.ConfigurationStatus pin = BiometricsHelper.checkPinStatus(getActivity(), biometricManager);
        if (pin == BiometricsHelper.ConfigurationStatus.Configured) {
            performPinUnlock();
        } else if (!BiometricsHelper.configurePin(getActivity())) {
            finish(true, true);
        }
    }

    private void finish(boolean success, boolean failedEnrollment) {
        NavDirections directions = null;
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getActivity());
        BiometricsHelper.ConfigurationStatus fingerprint = BiometricsHelper.checkFingerprintStatus(getActivity(),
                biometricManager);
        BiometricsHelper.ConfigurationStatus pin = BiometricsHelper.checkPinStatus(getActivity(), biometricManager);
        boolean configured = fingerprint == BiometricsHelper.ConfigurationStatus.Configured ||
                pin == BiometricsHelper.ConfigurationStatus.Configured;

        switch (callingActivity) {
            case ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS -> {
                if (success) {
                    directions = failedEnrollment || !configured ? navigateToBiometricEnrollFail() :
                            navigateToConnectidPhoneVerify(ConnectConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE, ConnectIDManager.MethodRegistrationPrimary, user.getPrimaryPhone(), user.getUserId(), user.getPassword(), user.getAlternatePhone());
                }
            }
            case ConnectConstants.CONNECT_RECOVERY_CONFIGURE_BIOMETRICS -> {
                if (success) {
                    directions = navigateToConnectidPhoneVerify(ConnectConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE, ConnectIDManager.MethodRecoveryPrimary, ((ConnectIdActivity)requireActivity()).recoverPhone, ((ConnectIdActivity)requireActivity()).recoverPhone, "", null);
                }
            }
            case ConnectConstants.CONNECT_UNLOCK_BIOMETRIC -> {
                if (success) {
                    ConnectIDManager.getInstance().setStatus(ConnectIDManager.ConnectIdStatus.LoggedIn);
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_NO_ACTIVITY);
                    requireActivity().setResult(RESULT_OK);
                    requireActivity().finish();

                }
            }
        }

        CommCareNavController.navigateSafely(Navigation.findNavController(binding.connectVerifyMessage),directions);

    }

    private NavDirections navigateToBiometricEnrollFail() {
        return ConnectIdBiometricConfigFragmentDirections.actionConnectidBiometricConfigToConnectidMessage(getResources().getString(R.string.connect_biometric_enroll_fail_title), getResources().getString(R.string.connect_biometric_enroll_fail_message), ConnectConstants.CONNECT_BIOMETRIC_ENROLL_FAIL, getResources().getString(R.string.connect_biometric_enroll_fail_button), null, null, null);
    }

    private NavDirections navigateToConnectidPhoneVerify(int verifyType, int method, String primaryPhone, String userId, String password, String alternatePhone) {
        return ConnectIdBiometricConfigFragmentDirections.actionConnectidBiometricConfigToConnectidPhoneVerify(verifyType, String.format(Locale.getDefault(), "%d", method), primaryPhone, userId, password, alternatePhone, false);
    }
}