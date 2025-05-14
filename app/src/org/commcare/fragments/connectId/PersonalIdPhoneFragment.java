package org.commcare.fragments.connectId;

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

import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenPersonalidPhonenoBinding;
import org.commcare.utils.PhoneNumberHelper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;

public class PersonalIdPhoneFragment extends Fragment {

    private ScreenPersonalidPhonenoBinding binding;
    private boolean shouldShowPhoneHintDialog = true;
    private PhoneNumberHelper phoneNumberHelper;
    private Activity activity;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = ScreenPersonalidPhonenoBinding.inflate(inflater, container, false);
        activity = requireActivity();
        phoneNumberHelper = PhoneNumberHelper.getInstance(activity);

        activity.setTitle(R.string.connect_registration_title);
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

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
        binding.continueButton.setOnClickListener(v -> onContinueClicked());

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

        binding.continueButton.setEnabled(isValidPhone && isConsentChecked);
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
        // TODO: Trigger API call for phone number validation and registration api later
        navigateToBiometricSetup();
    }

    private NavDirections navigateToBiometricSetup() {
        return PersonalIdPhoneFragmentDirections.actionPersonalidPhoneFragmentToPersonalidBiometricConfig();
    }
}
