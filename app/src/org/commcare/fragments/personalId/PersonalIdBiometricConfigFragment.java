package org.commcare.fragments.personalId;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import org.commcare.activities.connect.PersonalIdActivity;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenPersonalidVerifyBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.BiometricsHelper;
import org.javarosa.core.services.Logger;

import java.util.Locale;

import static android.app.Activity.RESULT_OK;

/**
 * Fragment that handles biometric or PIN verification for Connect ID authentication.
 */
public class PersonalIdBiometricConfigFragment extends Fragment {

    private BiometricManager biometricManager;
    private boolean isAttemptingFingerprint = false;
    private BiometricPrompt.AuthenticationCallback biometricCallback;
    private static final String KEY_ATTEMPTING_FINGERPRINT = "attempting_fingerprint";
    private ScreenPersonalidVerifyBinding binding;

    public PersonalIdBiometricConfigFragment() {
        // Required empty public constructor
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_ATTEMPTING_FINGERPRINT, isAttemptingFingerprint);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            isAttemptingFingerprint = savedInstanceState.getBoolean(KEY_ATTEMPTING_FINGERPRINT);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = ScreenPersonalidVerifyBinding.inflate(inflater, container, false);
        biometricManager = BiometricManager.from(requireActivity());
        biometricCallback = setupBiometricCallback();

        binding.connectVerifyFingerprintButton.setOnClickListener(v -> onFingerprintButtonClicked());
        binding.connectVerifyPinButton.setOnClickListener(v -> onPinButtonClicked());

        requireActivity().setTitle(R.string.connect_appbar_title_app_lock);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAuthenticationOptions();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private BiometricPrompt.AuthenticationCallback setupBiometricCallback() {
        Context context = requireActivity();
        return new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (isAttemptingFingerprint) {
                    isAttemptingFingerprint = false;
                    if (BiometricsHelper.isPinConfigured(context, biometricManager)) {
                        initiatePinAuthentication();
                        return;
                    }
                }
                Logger.exception("Biometric error", new Exception(String.format(Locale.getDefault(),
                        "Biometric error without PIN fallback: %s (%d)", errString, errorCode)));
                Toast.makeText(context, getString(R.string.connect_verify_configuration_failed, errString),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                reportAuthSuccess();
                navigateForward(false);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(requireActivity(), "Authentication failed", Toast.LENGTH_SHORT).show();
            }
        };
    }

    private void reportAuthSuccess() {
        String method = isAttemptingFingerprint ? AnalyticsParamValue.CCC_SIGN_IN_METHOD_FINGERPRINT
                : AnalyticsParamValue.CCC_SIGN_IN_METHOD_PIN;
        FirebaseAnalyticsUtil.reportCccSignIn(method);
    }

    private void refreshAuthenticationOptions() {
        BiometricsHelper.ConfigurationStatus fingerprintStatus = BiometricsHelper.checkFingerprintStatus(
                getActivity(), biometricManager);
        BiometricsHelper.ConfigurationStatus pinStatus = BiometricsHelper.checkPinStatus(getActivity(),
                biometricManager);

        if (fingerprintStatus == BiometricsHelper.ConfigurationStatus.NotAvailable &&
                pinStatus == BiometricsHelper.ConfigurationStatus.NotAvailable) {
            Logger.exception("No biometrics available", new Exception("No biometric or PIN options available"));
            navigateForward(true);
            return;
        }

        updateUiBasedOnStatus(fingerprintStatus, pinStatus);
    }

    private void updateUiBasedOnStatus(BiometricsHelper.ConfigurationStatus fingerprint,
                                       BiometricsHelper.ConfigurationStatus pin) {
        String title;
        String message;
        String fingerprintButton = null;
        String pinButton = null;

        if (fingerprint == BiometricsHelper.ConfigurationStatus.Configured) {
            title = getString(R.string.connect_verify_use_fingerprint_long);
            message = getString(R.string.connect_verify_fingerprint_configured);
            fingerprintButton = getString(R.string.connect_verify_agree);
        } else if (pin == BiometricsHelper.ConfigurationStatus.Configured) {
            title = getString(R.string.connect_verify_use_pin_long);
            message = getString(R.string.connect_verify_pin_configured);
            pinButton = getString(R.string.connect_verify_agree);
            if (fingerprint == BiometricsHelper.ConfigurationStatus.NotConfigured) {
                fingerprintButton = getString(R.string.connect_verify_configure_fingerprint);
            }
        } else {
            title = getString(R.string.connect_verify_title);
            message = getString(R.string.connect_verify_message);
            if (fingerprint == BiometricsHelper.ConfigurationStatus.NotConfigured) {
                fingerprintButton = getString(R.string.connect_verify_configure_fingerprint);
            }
            if (pin == BiometricsHelper.ConfigurationStatus.NotConfigured) {
                pinButton = getString(R.string.connect_verify_configure_pin);
            }
        }

        binding.connectVerifyTitle.setText(title);
        binding.connectVerifyMessage.setText(message);

        updateFingerprintSection(fingerprintButton);
        updatePinSection(pinButton);

        binding.connectVerifyOr.setVisibility(
                (fingerprintButton != null && pinButton != null) ? View.VISIBLE : View.INVISIBLE);
    }

    private void updateFingerprintSection(String buttonText) {
        boolean visible = buttonText != null;
        binding.connectVerifyFingerprintContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            binding.connectVerifyFingerprintButton.setText(buttonText);
        }
    }

    private void updatePinSection(String buttonText) {
        boolean visible = buttonText != null;
        binding.connectVerifyPinContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            binding.connectVerifyPinButton.setText(buttonText);
        }
    }

    private void onFingerprintButtonClicked() {
        BiometricsHelper.ConfigurationStatus status = BiometricsHelper.checkFingerprintStatus(getActivity(),
                biometricManager);
        if (status == BiometricsHelper.ConfigurationStatus.Configured) {
            isAttemptingFingerprint = true;
            BiometricsHelper.authenticateFingerprint(requireActivity(), biometricManager, biometricCallback);
        } else if (!BiometricsHelper.configureFingerprint(getActivity())) {
            navigateForward(true);
        }
    }

    private void onPinButtonClicked() {
        BiometricsHelper.ConfigurationStatus status = BiometricsHelper.checkPinStatus(getActivity(),
                biometricManager);
        if (status == BiometricsHelper.ConfigurationStatus.Configured) {
            initiatePinAuthentication();
        } else if (!BiometricsHelper.configurePin(getActivity())) {
            navigateForward(true);
        }
    }

    private void initiatePinAuthentication() {
        BiometricsHelper.authenticatePin(requireActivity(), biometricManager, biometricCallback);
    }

    public void handleFinishedPinActivity(int requestCode, int resultCode, Intent intent) {
        if (requestCode == ConnectConstants.PERSONALID_UNLOCK_PIN) {
            PersonalIdManager.getInstance().setStatus(PersonalIdManager.PersonalIdStatus.LoggedIn);
            ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.PERSONALID_NO_ACTIVITY);
            requireActivity().setResult(RESULT_OK);
            requireActivity().finish();
        }
        if (requestCode == ConnectConstants.CONFIGURE_BIOMETRIC_REQUEST_CODE) {
            navigateForward(false);
        }
    }

    private void navigateForward(boolean enrollmentFailed) {
        if (enrollmentFailed) {
            Navigation.findNavController(binding.connectVerifyFingerprintButton).navigate(navigateToBiometricEnrollmentFailed());
        } else {
            BiometricsHelper.ConfigurationStatus fingerprint = BiometricsHelper.checkFingerprintStatus(
                    getActivity(), biometricManager);
            BiometricsHelper.ConfigurationStatus pin = BiometricsHelper.checkPinStatus(getActivity(),
                    biometricManager);
            boolean isConfigured = fingerprint == BiometricsHelper.ConfigurationStatus.Configured ||
                    pin == BiometricsHelper.ConfigurationStatus.Configured;
            if (isConfigured) {
                Navigation.findNavController(binding.connectVerifyFingerprintButton).navigate(navigateToOtpScreen());
            }
        }
    }

    private NavDirections navigateToBiometricEnrollmentFailed() {
        return PersonalIdBiometricConfigFragmentDirections.actionPersonalidBiometricConfigToPersonalidMessage(
                getString(R.string.connect_biometric_enroll_fail_title),
                getString(R.string.connect_biometric_enroll_fail_message),
                ConnectConstants.PERSONALID_BIOMETRIC_ENROLL_FAIL,
                getString(R.string.connect_biometric_enroll_fail_button),
                null, null, null);
    }

    private NavDirections navigateToOtpScreen() {
        return PersonalIdBiometricConfigFragmentDirections.actionPersonalidBiometricConfigToPersonalidOtpPage(
                ((PersonalIdActivity)requireActivity()).primaryPhone);
    }
}
