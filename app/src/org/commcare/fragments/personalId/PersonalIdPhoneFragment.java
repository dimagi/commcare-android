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
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;

import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectConstants.PersonalIdApiErrorCodes;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.PersonalIdApiHandler;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenPersonalidPhonenoBinding;
import org.commcare.util.LogTypes;
import org.commcare.utils.PhoneNumberHelper;
import org.javarosa.core.services.Logger;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

public class PersonalIdPhoneFragment extends Fragment {

    private ScreenPersonalidPhonenoBinding binding;
    private boolean shouldShowPhoneHintDialog = true;
    private PhoneNumberHelper phoneNumberHelper;
    private Activity activity;
    private PersonalIdSessionDataViewModel personalIdSessionDataViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = ScreenPersonalidPhonenoBinding.inflate(inflater, container, false);
        activity = requireActivity();
        phoneNumberHelper = PhoneNumberHelper.getInstance(activity);

        activity.setTitle(R.string.connect_registration_title);
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        personalIdSessionDataViewModel = new ViewModelProvider(requireActivity()).get(PersonalIdSessionDataViewModel.class);
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
        String phone = PhoneNumberHelper.buildPhoneNumber(
                binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString()
        );

        new PersonalIdApiHandler() {
            @Override
            protected void onSuccess(PersonalIdSessionData sessionData) {
                personalIdSessionDataViewModel.setPersonalIdSessionData(sessionData);
                if (personalIdSessionDataViewModel.getPersonalIdSessionData().getToken() != null) {
                    onConfigurationSucesss();
                } else { // This is called when api returns success but with a a failure code
                    onConfigurationFailure();
                }
            }

            @Override
            protected void onFailure(PersonalIdApiErrorCodes failureCode) {
                navigateFailure(failureCode);
            }
        }.makeStartConfigurationCall(requireActivity(), phone);
    }


    private void onConfigurationSucesss() {
        Navigation.findNavController(binding.personalidPhoneContinueButton).navigate(navigateToBiometricSetup());
    }

    private void onConfigurationFailure() {
        Logger.log(LogTypes.TYPE_USER,
                personalIdSessionDataViewModel.getPersonalIdSessionData().getSessionFailureCode());
        Navigation.findNavController(binding.personalidPhoneContinueButton).navigate(
                navigateToMessageDisplay(getString(R.string.configuration_process_failed_subtitle), false));
    }

    private void navigateFailure(PersonalIdApiErrorCodes failureCode) {
        switch (failureCode) {
            case API_ERROR:
                Navigation.findNavController(binding.personalidPhoneContinueButton).navigate(
                        navigateToMessageDisplay(getString(R.string.configuration_process_api_failed), true));
                break;
            case NETWORK_ERROR:
                ConnectNetworkHelper.showNetworkError(getActivity());
                break;
            case TOKEN_UNAVAILABLE_ERROR:
                ConnectNetworkHelper.handleTokenUnavailableException(requireActivity());
                break;
            case TOKEN_DENIED_ERROR:
                ConnectNetworkHelper.handleTokenDeniedException(requireActivity());
                break;
            case OLD_API_ERROR:
                ConnectNetworkHelper.showOutdatedApiError(getActivity());
                break;
        }
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
