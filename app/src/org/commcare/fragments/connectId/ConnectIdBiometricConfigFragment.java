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

    public static ConnectIdBiometricConfigFragment newInstance(String param1, String param2) {
        ConnectIdBiometricConfigFragment fragment = new ConnectIdBiometricConfigFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadSavedState(savedInstanceState);

    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = ScreenConnectVerifyBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        biometricManager = BiometricManager.from(requireActivity());
        biometricPromptCallbacks = preparePromptCallbacks();
        if (getArguments() != null) {
            callingActivity = ConnectIdBiometricConfigFragmentArgs.fromBundle(getArguments()).getCallingClass();
            allowPassword = ConnectIdBiometricConfigFragmentArgs.fromBundle(getArguments()).getAllowPassword();
        }
        binding.connectVerifyFingerprintButton.setOnClickListener(v -> handleFingerprintButton());
        binding.connectVerifyPinButton.setOnClickListener(v -> handlePinButton());
        requireActivity().setTitle(R.string.connect_appbar_title_app_lock);
        updateState();
        return view;
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
                finish(true, false);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(requireActivity().getApplicationContext(), R.string.connect_biometric_error,
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

    private void updateState() {
        BiometricsHelper.ConfigurationStatus fingerprint = BiometricsHelper.checkFingerprintStatus(getActivity(),
                biometricManager);
        BiometricsHelper.ConfigurationStatus pin = BiometricsHelper.checkPinStatus(getActivity(), biometricManager);
        if (fingerprint == BiometricsHelper.ConfigurationStatus.NotAvailable &&
                pin == BiometricsHelper.ConfigurationStatus.NotAvailable) {
            //Skip to password-only workflow
            finish(true, true);
        } else {
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == PASSWORD_LOCK) {
            finish(true, true);
        }
        if (requestCode == ConnectConstants.CONNECT_UNLOCK_PIN) {
            finish(true, false);
        }
    }

    private void performFingerprintUnlock() {
        attemptingFingerprint = true;
        boolean allowOtherOptions = BiometricsHelper.isPinConfigured(requireActivity(), biometricManager) ||
                allowPassword;
        BiometricsHelper.authenticateFingerprint(requireActivity(), biometricManager, allowOtherOptions, biometricPromptCallbacks);
    }

    private void performPasswordUnlock() {
        BiometricsHelper.authenticatePassword(requireActivity(), biometricManager, biometricPromptCallbacks);
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
        if (directions != null) {
            Navigation.findNavController(binding.connectVerifyMessage).navigate(directions);
        }
    }

    private NavDirections navigateToBiometricEnrollFail() {
        return ConnectIdBiometricConfigFragmentDirections.actionConnectidBiometricConfigToConnectidMessage(getResources().getString(R.string.connect_biometric_enroll_fail_title), getResources().getString(R.string.connect_biometric_enroll_fail_message), ConnectConstants.CONNECT_BIOMETRIC_ENROLL_FAIL, getResources().getString(R.string.connect_biometric_enroll_fail_button), null, null, null);
    }

    private NavDirections navigateToConnectidPhoneVerify(int verifyType, int method, String primaryPhone, String userId, String password, String alternatePhone) {
        return ConnectIdBiometricConfigFragmentDirections.actionConnectidBiometricConfigToConnectidPhoneVerify(verifyType, String.format(Locale.getDefault(), "%d", method), primaryPhone, userId, password, alternatePhone, false);
    }
}