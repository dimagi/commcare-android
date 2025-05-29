package org.commcare.fragments.personalId;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import org.commcare.util.LogTypes;
import org.commcare.utils.AppMetaDataUtil;
import org.commcare.utils.PhoneNumberHelper;
import org.javarosa.core.services.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import androidx.navigation.fragment.NavHostFragment;

public class PersonalIdPhoneFragment extends Fragment {

    private ScreenPersonalidPhonenoBinding binding;
    private boolean shouldShowPhoneHintDialog = true;
    private PhoneNumberHelper phoneNumberHelper;
    private Activity activity;
    private PersonalIdSessionDataViewModel personalIdSessionDataViewModel;
    private IntegrityTokenApiRequestHelper integrityTokenApiRequestHelper;

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
        String phone = PhoneNumberHelper.buildPhoneNumber(
                binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString()
        );

        boolean isValidPhone = phoneNumberHelper.isValidPhoneNumber(phone);
        boolean isConsentChecked = binding.connectConsentCheck.isChecked();

        binding.personalidPhoneContinueButton.setEnabled(isValidPhone && isConsentChecked);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String phone = PhoneNumberHelper.handlePhoneNumberPickerResult(requestCode, resultCode, data,
                getActivity());
        displayPhoneNumber(phone);
    }

    private void displayPhoneNumber(String fullNumber) {
        int defaultCode = phoneNumberHelper.getCountryCodeFromLocale(activity);
        String formattedCode = PhoneNumberHelper.getInstance(activity).formatCountryCode(defaultCode);

        if (fullNumber != null && fullNumber.startsWith(formattedCode)) {
            fullNumber = fullNumber.substring(formattedCode.length());
        }

        int countryCode = phoneNumberHelper.getCountryCode(
                fullNumber != null && !fullNumber.isEmpty() ? fullNumber : "");
        String countryCodeText = phoneNumberHelper.formatCountryCode(countryCode);

        binding.connectPrimaryPhoneInput.setText(fullNumber);
        binding.countryCode.setText(countryCodeText);
    }

    private void onContinueClicked() {
        binding.personalidPhoneContinueButton.setEnabled(false);
        startConfigurationRequest();
    }

    private void startConfigurationRequest() {
        String phone = PhoneNumberHelper.buildPhoneNumber(
                binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString()
        );

        HashMap<String, String> body = AppMetaDataUtil.INSTANCE.provideAppMetaData(requireActivity());
        body.put("phone_number", phone);

        integrityTokenApiRequestHelper.withIntegrityToken(body, (integrityToken, requestHash) -> {
            if (integrityToken != null) {
                makeStartConfigurationCall(integrityToken, requestHash, body);
            } else {
                onConfigurationFailure();
            }
            return null;
        });
    }

    private void makeStartConfigurationCall(@Nullable String integrityToken, String requestHash,
            HashMap<String, String> body) {
        new PersonalIdApiHandler() {
            @Override
            protected void onSuccess(PersonalIdSessionData sessionData) {
                personalIdSessionDataViewModel.setPersonalIdSessionData(sessionData);
                if (personalIdSessionDataViewModel.getPersonalIdSessionData().getToken() != null) {
                    onConfigurationSucesss();
                } else {
                    // This is called when api returns success but with a a failure code
                    Logger.log(LogTypes.TYPE_USER,
                            personalIdSessionDataViewModel.getPersonalIdSessionData().getSessionFailureCode());
                    onConfigurationFailure();
                }
            }

            @Override
            protected void onFailure(PersonalIdApiErrorCodes failureCode) {
                navigateFailure(failureCode);
            }
        }.makeStartConfigurationCall(requireActivity(), body, integrityToken,requestHash);
    }


    private void onConfigurationSucesss() {
        Navigation.findNavController(binding.personalidPhoneContinueButton).navigate(navigateToBiometricSetup());
    }

    private void onConfigurationFailure() {
        String failureMessage = getString(R.string.configuration_process_failed_subtitle);
        Navigation.findNavController(binding.personalidPhoneContinueButton).navigate(
                navigateToMessageDisplay(failureMessage, false));
    }

    private void navigateFailure(PersonalIdApiHandler.PersonalIdApiErrorCodes failureCode) {
        if (failureCode.shouldAllowRetry()) {
            binding.personalidPhoneContinueButton.setEnabled(true);
        }
        PersonalIdApiErrorHandler.handle(requireActivity(), failureCode);
    }

    private NavDirections navigateToBiometricSetup() {
        return PersonalIdPhoneFragmentDirections.actionPersonalidPhoneFragmentToPersonalidBiometricConfig();
    }

    private NavDirections navigateToMessageDisplay(String errorMessage,boolean isCancellable) {
        return PersonalIdPhoneFragmentDirections.actionPersonalidPhoneFragmentToPersonalidMessageDisplay(
                getString(R.string.configuration_process_failed_title),
                errorMessage,
                ConnectConstants.PERSONALID_DEVICE_CONFIGURATION_FAILED, getString(R.string.ok), null, "",
                "").setIsCancellable(isCancellable);
    }
}
