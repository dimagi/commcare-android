package org.commcare.fragments.personalId;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;

import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.android.integrity.IntegrityTokenApiRequestHelper;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.network.PersonalIdApiErrorHandler;
import org.commcare.connect.network.PersonalIdApiHandler;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenPersonalidPhonenoBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.util.LogTypes;
import org.commcare.utils.PhoneNumberHelper;
import org.javarosa.core.services.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class PersonalIdPhoneFragment extends Fragment {

    private ScreenPersonalidPhonenoBinding binding;
    private boolean shouldShowPhoneHintDialog = true;
    private PhoneNumberHelper phoneNumberHelper;
    private Activity activity;
    private PersonalIdSessionDataViewModel personalIdSessionDataViewModel;
    private IntegrityTokenApiRequestHelper integrityTokenApiRequestHelper;
    private String phone;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = ScreenPersonalidPhonenoBinding.inflate(inflater, container, false);
        activity = requireActivity();
        phoneNumberHelper = PhoneNumberHelper.getInstance(activity);
        activity.setTitle(R.string.connect_registration_title);
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        personalIdSessionDataViewModel = new ViewModelProvider(requireActivity()).get(PersonalIdSessionDataViewModel.class);
        integrityTokenApiRequestHelper = new IntegrityTokenApiRequestHelper(getViewLifecycleOwner());
        initializeUi();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void initializeUi() {
        binding.countryCode.setText(phoneNumberHelper.setDefaultCountryCode(getContext()));
        binding.checkText.setMovementMethod(LinkMovementMethod.getInstance());
        setupListeners();
        updateContinueButtonState();
    }

    private void setupListeners() {
        binding.connectConsentCheck.setOnClickListener(v -> updateContinueButtonState());
        binding.personalidPhoneContinueButton.setOnClickListener(v -> onContinueClicked());

        ActivityResultLauncher<IntentSenderRequest> phoneHintLauncher = setupPhoneHintLauncher();

        View.OnFocusChangeListener focusChangeListener = (v, hasFocus) -> {
            if (hasFocus && shouldShowPhoneHintDialog) {
                PhoneNumberHelper.requestPhoneNumberHint(phoneHintLauncher, activity);
                shouldShowPhoneHintDialog = false;
            }
        };

        binding.connectPrimaryPhoneInput.addTextChangedListener(createPhoneNumberWatcher());
        binding.countryCode.addTextChangedListener(phoneNumberHelper.getCountryCodeWatcher(binding.countryCode));

        binding.connectPrimaryPhoneInput.setOnFocusChangeListener(focusChangeListener);
        binding.countryCode.setOnFocusChangeListener(focusChangeListener);
    }

    private ActivityResultLauncher<IntentSenderRequest> setupPhoneHintLauncher() {
        return registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        try {
                            String phoneNumber = Identity.getSignInClient(activity).getPhoneNumberFromIntent(
                                    result.getData());
                            displayPhoneNumber(phoneNumber);
                        } catch (ApiException e) {
                            Toast.makeText(getContext(), R.string.error_occured, Toast.LENGTH_SHORT).show();
                        }
                    }else{
                        binding.connectPrimaryPhoneInput.post(() -> binding.connectPrimaryPhoneInput.requestFocus());
                    }
                }
        );
    }

    private TextWatcher createPhoneNumberWatcher() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateContinueButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
    }

    private void updateContinueButtonState() {
        phone = PhoneNumberHelper.buildPhoneNumber(
                binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString()
        );

        boolean isValidPhone = phoneNumberHelper.isValidPhoneNumber(phone);
        boolean isConsentChecked = binding.connectConsentCheck.isChecked();

        enableContinueButton(isValidPhone && isConsentChecked);
    }

    private void displayPhoneNumber(String fullPhoneNumber) {

        if(TextUtils.isEmpty(fullPhoneNumber))return;

        int countryCodeFromFullPhoneNumber = phoneNumberHelper.getCountryCode(fullPhoneNumber);
        long nationPhoneNumberFromFullPhoneNumber = phoneNumberHelper.getNationalNumber(fullPhoneNumber);

        if(countryCodeFromFullPhoneNumber!=-1 && nationPhoneNumberFromFullPhoneNumber!=-1){
            binding.connectPrimaryPhoneInput.setText(String.valueOf(nationPhoneNumberFromFullPhoneNumber));
            binding.countryCode.setText(phoneNumberHelper.formatCountryCode(countryCodeFromFullPhoneNumber));
        }

    }

    private void onContinueClicked() {
        enableContinueButton(false);
        startConfigurationRequest();
    }

    private void enableContinueButton(boolean isEnabled) {
        binding.personalidPhoneContinueButton.setEnabled(isEnabled);
    }

    private void startConfigurationRequest() {
        clearError();
        phone = PhoneNumberHelper.buildPhoneNumber(
                binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString()
        );

        HashMap<String, String> body = new HashMap<>();
        body.put("phone_number", phone);
        body.put("application_id",requireContext().getPackageName());

        integrityTokenApiRequestHelper.withIntegrityToken(body, (integrityToken, requestHash) -> {
            if (integrityToken != null) {
                makeStartConfigurationCall(integrityToken, requestHash, body);
            } else {
                onConfigurationFailure(AnalyticsParamValue.START_CONFIGURATION_INTEGRITY_DEVICE_FAILURE);
            }
            return null;
        });
    }

    private void makeStartConfigurationCall(@Nullable String integrityToken, String requestHash,
            HashMap<String, String> body) {
        Log.d("Integrity", "Token: " + integrityToken);
        Log.d("Integrity", "Hash: " + requestHash);
        new PersonalIdApiHandler() {
            @Override
            protected void onSuccess(PersonalIdSessionData sessionData) {
                personalIdSessionDataViewModel.setPersonalIdSessionData(sessionData);
                personalIdSessionDataViewModel.getPersonalIdSessionData().setPhoneNumber(phone);
                if (personalIdSessionDataViewModel.getPersonalIdSessionData().getToken() != null) {
                    onConfigurationSuccess();
                } else {
                    String failureCode =
                            personalIdSessionDataViewModel.getPersonalIdSessionData().getSessionFailureCode();
                    // This is called when api returns success but with a a failure code
                    Logger.log(LogTypes.TYPE_MAINTENANCE, "Start Config API failed with " + failureCode);
                    onConfigurationFailure(failureCode);
                }
            }

            @Override
            protected void onFailure(PersonalIdApiErrorCodes failureCode, Throwable t) {
                if(failureCode == PersonalIdApiErrorCodes.FORBIDDEN_ERROR) {
                    onConfigurationFailure(AnalyticsParamValue.START_CONFIGURATION_INTEGRITY_CHECK_FAILURE);
                } else {
                    navigateFailure(failureCode, t);
                }
            }
        }.makeStartConfigurationCall(requireActivity(), body, integrityToken,requestHash);
    }


    private void onConfigurationSuccess() {
        Navigation.findNavController(binding.personalidPhoneContinueButton).navigate(navigateToBiometricSetup());
    }

    private void onConfigurationFailure(String failureCause) {
        FirebaseAnalyticsUtil.reportPersonalIdConfigurationFailure(failureCause);
        String failureMessage = getString(R.string.personalid_configuration_process_failed_subtitle);
        Navigation.findNavController(binding.personalidPhoneContinueButton).navigate(
                navigateToMessageDisplay(failureMessage, false));
    }

    private void navigateFailure(PersonalIdApiHandler.PersonalIdApiErrorCodes failureCode, Throwable t) {
        showError(PersonalIdApiErrorHandler.handle(requireActivity(), failureCode, t));
        if (failureCode.shouldAllowRetry()) {
            enableContinueButton(true);
        }
    }

    private void clearError() {
        binding.personalidPhoneError.setVisibility(View.GONE);
        binding.personalidPhoneError.setText("");
    }

    private void showError(String error) {
        binding.personalidPhoneError.setVisibility(View.VISIBLE);
        binding.personalidPhoneError.setText(error);
    }

    private NavDirections navigateToBiometricSetup() {
        return PersonalIdPhoneFragmentDirections.actionPersonalidPhoneFragmentToPersonalidBiometricConfig();
    }

    private NavDirections navigateToMessageDisplay(String errorMessage,boolean isCancellable) {
        return PersonalIdPhoneFragmentDirections.actionPersonalidPhoneFragmentToPersonalidMessageDisplay(
                getString(R.string.personalid_configuration_process_failed_title),
                errorMessage,
                ConnectConstants.PERSONALID_DEVICE_CONFIGURATION_FAILED, getString(R.string.ok), null).setIsCancellable(isCancellable);
    }
}
