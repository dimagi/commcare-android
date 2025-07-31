package org.commcare.fragments.personalId;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.PersonalIdManager;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenPersonalidVerifyBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.util.LogTypes;
import org.commcare.utils.BiometricsHelper;
import org.commcare.utils.EncryptionKeyProvider;
import org.javarosa.core.services.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import androidx.navigation.fragment.NavHostFragment;

import static android.app.Activity.RESULT_OK;

import static org.commcare.android.database.connect.models.PersonalIdSessionData.BIOMETRIC_TYPE;
import static org.commcare.android.database.connect.models.PersonalIdSessionData.PIN;
import static org.commcare.connect.PersonalIdManager.BIOMETRIC_INVALIDATION_KEY;
import static org.commcare.utils.ViewUtils.showSnackBarWithOk;

/**
 * Fragment that handles biometric or PIN verification for Connect ID authentication.
 */
public class PersonalIdBiometricConfigFragment extends BasePersonalIdFragment {
    private BiometricManager biometricManager;
    private BiometricPrompt.AuthenticationCallback biometricCallback;
    private ScreenPersonalidVerifyBinding binding;
    private PersonalIdSessionDataViewModel personalIdSessionDataViewModel;

    public PersonalIdBiometricConfigFragment() {
        // Required empty public constructor
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        personalIdSessionDataViewModel = new ViewModelProvider(requireActivity()).get(
                PersonalIdSessionDataViewModel.class);
        BiometricsHelper.checkForValidSecurityType(personalIdSessionDataViewModel
                .getPersonalIdSessionData().getRequiredLock());
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
        updateUiBasedOnMinSecurityRequired();
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
                Logger.exception("Biometric error", new Exception(String.format(Locale.getDefault(),
                        "Biometric error without PIN fallback: %s (%d)", errString, errorCode)));
                Toast.makeText(context, getString(R.string.connect_verify_configuration_failed, errString),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                navigateForward(false);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(requireActivity(), "Authentication failed", Toast.LENGTH_SHORT).show();
            }
        };
    }

    private void updateUiBasedOnMinSecurityRequired() {

        BiometricsHelper.ConfigurationStatus fingerprintStatus = BiometricsHelper.checkFingerprintStatus(
                requireContext(), biometricManager);
        BiometricsHelper.ConfigurationStatus pinStatus = BiometricsHelper.checkPinStatus(requireContext(),
                biometricManager);

        boolean hasFingerprintHardware = fingerprintStatus != BiometricsHelper.ConfigurationStatus.NoHardware;

        String title;
        String message;
        String fingerprintButton;
        String pinButton = null;

        if (fingerprintStatus != BiometricsHelper.ConfigurationStatus.Configured
                && pinStatus != BiometricsHelper.ConfigurationStatus.Configured) {  // nothing is configured
            title = getString(R.string.connect_verify_title);
            message = getString(R.string.connect_verify_message);
            fingerprintButton = getString(R.string.connect_verify_configure_fingerprint);
            if (PIN.equals(personalIdSessionDataViewModel.getPersonalIdSessionData().getRequiredLock())) {
                pinButton = getString(R.string.connect_verify_configure_pin);
            }
        } else if (fingerprintStatus == BiometricsHelper.ConfigurationStatus.Configured) {    // Fingerprint is configured so works for both PIN and BIOMETRIC_TYPE
            title = getString(R.string.connect_verify_use_fingerprint_long);
            message = getString(R.string.connect_verify_fingerprint_configured);
            fingerprintButton = getString(R.string.connect_verify_agree);
        } else if (BIOMETRIC_TYPE.equals(personalIdSessionDataViewModel.getPersonalIdSessionData().getRequiredLock())) {   //Fingerprint not configured but required for BIOMETRIC_TYPE
            // Need at least Fingerprint configuration for BIOMETRIC_TYPE
            if(hasFingerprintHardware) {
                title = getString(R.string.connect_verify_title);
                message = getString(R.string.connect_verify_message);
                fingerprintButton = getString(R.string.connect_verify_configure_fingerprint);
            } else {
                showNoBiometricHardwareError();
                return;
            }
        } else {   // Only PIN is configure
            title = getString(R.string.connect_verify_use_pin_long);
            message = getString(R.string.connect_verify_pin_configured);
            pinButton = getString(R.string.connect_verify_agree);
            fingerprintButton = hasFingerprintHardware ?
                    getString(R.string.connect_verify_configure_fingerprint) : null;
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

        switch(status) {
            case NotAvailable:
                showBiometricNotAvailableError();
                break;
            case NeedsUpdate:
                showBiometricNeedsUpdateError();
                break;
            case Configured:
                initiateBiometricAuthentication();
                return;
            case NotConfigured:
                if (!BiometricsHelper.configureFingerprint(getActivity())) {
                    navigateForward(true);
                }
                break;
        }
    }

    private void showNoBiometricHardwareError() {
        String message = BiometricsHelper.getNoBiometricHardwareError(requireActivity());
        FirebaseAnalyticsUtil.reportPersonalIdConfigurationFailure(AnalyticsParamValue.MIN_BIOMETRIC_HARDWARE_ABSENT);
        Logger.log(LogTypes.TYPE_MAINTENANCE, "No biometric hardware during biometric configuration");
        navigateToMessageDisplayForSecurityConfigurationFailure(message, false);
    }

    private void showBiometricNotAvailableError() {
        String message = BiometricsHelper.getBiometricHardwareUnavailableError(requireActivity());
        FirebaseAnalyticsUtil.reportPersonalIdConfigurationFailure(AnalyticsParamValue.MIN_BIOMETRIC_HARDWARE_UNAVAILABLE);
        Logger.log(LogTypes.TYPE_MAINTENANCE, "Biometric not available during biometric configuration");
        navigateToMessageDisplayForSecurityConfigurationFailure(message, true);
    }

    private void showBiometricNeedsUpdateError() {
        String message = BiometricsHelper.getBiometricNeedsUpdateError(requireActivity());
        FirebaseAnalyticsUtil.reportPersonalIdConfigurationFailure(AnalyticsParamValue.MIN_BIOMETRIC_NEEDS_UPDATE);
        Logger.log(LogTypes.TYPE_MAINTENANCE, "Biometric requires update during biometric configuration");
        navigateToMessageDisplayForSecurityConfigurationFailure(message, true);
    }

    private void initiateBiometricAuthentication() {
        BiometricsHelper.authenticateFingerprint(requireActivity(), biometricManager, biometricCallback,
                !BIOMETRIC_TYPE.equals(personalIdSessionDataViewModel.getPersonalIdSessionData().getRequiredLock()));
    }

    private void onPinButtonClicked() {
        BiometricsHelper.ConfigurationStatus status = BiometricsHelper.checkPinStatus(getActivity(),
                biometricManager);

        switch(status) {
            case NotAvailable:
                showPinNotAvailableError();
                break;
            case NeedsUpdate:
                showPinNeedsUpdateError();
                break;
            case Configured:
                initiatePinAuthentication();
                return;
            case NotConfigured:
                if (!BiometricsHelper.configurePin(getActivity())) {
                    navigateForward(true);
                }
                break;
        }
    }

    private void showPinNotAvailableError() {
        String message = BiometricsHelper.getPinHardwareUnavailableError(requireActivity());
        FirebaseAnalyticsUtil.reportPersonalIdConfigurationFailure(AnalyticsParamValue.MIN_BIOMETRIC_HARDWARE_ABSENT);
        Logger.log(LogTypes.TYPE_MAINTENANCE, "PIN not available during biometric configuration");
        navigateToMessageDisplayForSecurityConfigurationFailure(message, true);
    }

    private void showPinNeedsUpdateError() {
        String message = BiometricsHelper.getPinNeedsUpdateError(requireActivity());
        FirebaseAnalyticsUtil.reportPersonalIdConfigurationFailure(AnalyticsParamValue.MIN_BIOMETRIC_PIN_NEEDS_UPDATE);
        Logger.log(LogTypes.TYPE_MAINTENANCE, "PIN requires update during biometric configuration");
        navigateToMessageDisplayForSecurityConfigurationFailure(message, true);
    }

    private void initiatePinAuthentication() {
        BiometricsHelper.authenticatePin(requireActivity(), biometricManager, biometricCallback);
    }

    public void handleFinishedPinActivity(int requestCode, int resultCode) {
        if (requestCode == ConnectConstants.CONFIGURE_BIOMETRIC_REQUEST_CODE) {
            navigateForward(resultCode != RESULT_OK);
        }
    }

    private void navigateForward(boolean enrollmentFailed) {
        if (enrollmentFailed) {
            FirebaseAnalyticsUtil.reportPersonalIdConfigurationFailure(AnalyticsParamValue.BIOMETRIC_ENROLLMENT_FAILED);
            navigateToBiometricEnrollmentFailed();
        } else {
            BiometricsHelper.ConfigurationStatus fingerprint = BiometricsHelper.checkFingerprintStatus(
                    getActivity(), biometricManager);
            BiometricsHelper.ConfigurationStatus pin = BiometricsHelper.checkPinStatus(getActivity(),
                    biometricManager);
            boolean isConfigured = fingerprint == BiometricsHelper.ConfigurationStatus.Configured ||
                    pin == BiometricsHelper.ConfigurationStatus.Configured;
            if (isConfigured) {
                storeBiometricInvalidationKey();
                if (Boolean.FALSE.equals(personalIdSessionDataViewModel.getPersonalIdSessionData().getDemoUser())) {
                    NavHostFragment.findNavController(this).navigate(navigateToOtpScreen());
                } else {
                    View view = getView();
                    if (view != null) {
                        showSnackBarWithOk(view, getString(R.string.connect_verify_skip_phone_number),
                                v -> NavHostFragment.findNavController(this).navigate(navigateToNameScreen()));
                    }
                }
            }
        }
    }

    /**
     * Generates a biometric linked key in Android Key Store if not already there
     */
    private void storeBiometricInvalidationKey() {
        CommCareActivity<?> activity = (CommCareActivity<?>) requireActivity();
        if(BiometricsHelper.isFingerprintConfigured(activity,
                PersonalIdManager.getInstance().getBiometricManager(activity))) {
            new EncryptionKeyProvider(requireContext(), true, BIOMETRIC_INVALIDATION_KEY).getKeyForEncryption();
        }
    }

    private void navigateToBiometricEnrollmentFailed() {
        navigateToMessageDisplay(
                getString(R.string.connect_biometric_enroll_fail_title),
                getString(R.string.connect_biometric_enroll_fail_message),
                true,
                ConnectConstants.PERSONALID_BIOMETRIC_ENROLL_FAIL,
                R.string.connect_biometric_enroll_fail_button);
    }

    private void navigateToMessageDisplayForSecurityConfigurationFailure(String errorMessage, boolean allowRetry) {
        int specifier = allowRetry ? ConnectConstants.PERSONALID_DEVICE_CONFIGURATION_ISSUE_WARNING
                : ConnectConstants.PERSONALID_DEVICE_CONFIGURATION_FAILED;

        navigateToMessageDisplay(
                getString(R.string.personalid_configuration_process_failed_title),
                errorMessage,
                false,
                specifier,
                R.string.ok);
    }

    private NavDirections navigateToOtpScreen() {
        return PersonalIdBiometricConfigFragmentDirections.actionPersonalidBiometricConfigToPersonalidOtpPage();
    }

    private NavDirections navigateToNameScreen() {
        return PersonalIdBiometricConfigFragmentDirections.actionPersonalidBiometricConfigToPersonalidName();
    }

    @Override
    protected void navigateToMessageDisplay(@NotNull String title, @Nullable String message, boolean isCancellable,
            int phase, int buttonText) {
        NavDirections navDirections = PersonalIdBiometricConfigFragmentDirections.actionPersonalidBiometricConfigToPersonalidMessage(
                title, message, phase, getString(buttonText), null).setIsCancellable(isCancellable);
        Navigation.findNavController(binding.getRoot()).navigate(navDirections);
    }
}
