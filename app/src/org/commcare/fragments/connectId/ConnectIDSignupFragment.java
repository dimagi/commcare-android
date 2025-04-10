package org.commcare.fragments.connectId;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;

import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectIDManager;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentSignupBinding;
import org.commcare.utils.PhoneNumberHelper;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

public class ConnectIDSignupFragment extends Fragment {
    private String existingPhone = "";
    private int callingClass = ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE;
    protected boolean skipPhoneNumberCheck = false;
    private FragmentSignupBinding binding;
    private boolean showhPhoneDialog = true;
    PhoneNumberHelper phoneNumberHelper;
    private Activity activity;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentSignupBinding.inflate(inflater, container, false);
        activity = requireActivity();
        View view = binding.getRoot();
        activity.setTitle(getString(R.string.connect_registration_title));
        phoneNumberHelper =  PhoneNumberHelper.getInstance(activity);
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        setListeners();
        setArguments();

        binding.countryCode.setText(phoneNumberHelper.setDefaultCountryCode(getContext()));

        updateButtonEnabled();
        setupUi();
        if (!existingPhone.isEmpty()) {
            displayNumber(existingPhone);
        }
        return view;
    }

    private void setArguments() {
        if (getArguments() != null) {
            callingClass = ConnectIDSignupFragmentArgs.fromBundle(getArguments()).getCallingClass();
            existingPhone = ConnectIDSignupFragmentArgs.fromBundle(getArguments()).getPhone();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setListeners() {
        binding.connectConsentCheck.setOnClickListener(v -> updateButtonEnabled());
        ActivityResultLauncher<IntentSenderRequest> phoneNumberHintLauncher = getPhoneNumberHintLauncher();

        View.OnFocusChangeListener listener = (v, hasFocus) -> {
            if (hasFocus && showhPhoneDialog) {
                PhoneNumberHelper.requestPhoneNumberHint(phoneNumberHintLauncher, activity);
                showhPhoneDialog = false;
            }
        };

        TextWatcher buttonUpdateWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateButtonEnabled();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        };


        binding.nameTextValue.addTextChangedListener(buttonUpdateWatcher);
        binding.connectPrimaryPhoneInput.addTextChangedListener(buttonUpdateWatcher);

        binding.countryCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!s.toString().contains("+")) {
                    binding.countryCode.setText("+" + binding.countryCode.getText());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        binding.connectPrimaryPhoneInput.setOnFocusChangeListener(listener);
        binding.countryCode.setOnFocusChangeListener(listener);
    }

    private ActivityResultLauncher<IntentSenderRequest> getPhoneNumberHintLauncher() {
        return registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        String phoneNumber;
                        try {
                            phoneNumber = Identity.getSignInClient(activity).getPhoneNumberFromIntent(data);
                            displayNumber(phoneNumber);
                        } catch (ApiException e) {
                            Toast.makeText(getContext(), R.string.error_occured, Toast.LENGTH_SHORT).show();
                            throw new RuntimeException(e);
                        }

                    }
                }
        );
    }

    void setupUi() {
        if (callingClass == ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE) {
            binding.nameLayout.setVisibility(View.GONE);
            binding.phoneTitle.setText(R.string.connect_recovery_title);
            binding.buttonTitle.setText(R.string.connect_recover_no_account);
            binding.recoverButton.setText(R.string.connect_signup);
            binding.connectConsentCheck.setVisibility(View.GONE);
            binding.checkText.setVisibility(View.GONE);
            binding.recoverButton.setOnClickListener(v -> handleSignupButtonPress());
            binding.phoneSubText.setVisibility(View.GONE);
            binding.continueButton.setOnClickListener(v -> handleContinueButtonPress());

        } else {
            binding.nameLayout.setVisibility(View.VISIBLE);
            binding.phoneTitle.setText(R.string.connect_registration_title);
            binding.checkText.setMovementMethod(LinkMovementMethod.getInstance());
            binding.buttonTitle.setText(R.string.connect_registration_have_account);
            binding.recoverButton.setText(R.string.connect_recover);
            binding.phoneSubText.setVisibility(View.GONE);
            binding.connectConsentCheck.setVisibility(View.VISIBLE);
            binding.recoverButton.setOnClickListener(v -> handleRecoverButtonPress());
            binding.continueButton.setOnClickListener(v -> handleContinueButtonPress());
        }
    }

    public void updateButtonEnabled() {
        String phone = PhoneNumberHelper.buildPhoneNumber(binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString());

        boolean valid = phoneNumberHelper.isValidPhoneNumber(phone);

        boolean isEnabled = valid && (callingClass == ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE ||
                (binding.nameTextValue.getText().toString().length() > 0 &&
                        binding.connectConsentCheck.isChecked()));
        binding.continueButton.setEnabled(isEnabled);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String phone = PhoneNumberHelper.handlePhoneNumberPickerResult(requestCode, resultCode, data, getActivity());
        skipPhoneNumberCheck = false;
        displayNumber(phone);
    }

    void displayNumber(String fullNumber) {
        int code = phoneNumberHelper.getCountryCodeFromLocale(activity);
        if (fullNumber != null && fullNumber.length() > 0) {
            code = phoneNumberHelper.getCountryCode(fullNumber);
        }

        String codeText = "";
        if (code > 0) {
            codeText = String.valueOf(code);
            if (!codeText.startsWith("+")) {
                codeText = "+" + codeText;
            }
        }

        if (fullNumber != null && fullNumber.startsWith(codeText)) {
            fullNumber = fullNumber.substring(codeText.length());
        }
        skipPhoneNumberCheck = false;
        binding.connectPrimaryPhoneInput.setText(fullNumber);
        skipPhoneNumberCheck = true;
        binding.countryCode.setText(codeText);
        skipPhoneNumberCheck = false;
    }

    void handleContinueButtonPress() {
        checkPhoneNumber();
    }

    void handleRecoverButtonPress() {
        ConnectUserDatabaseUtil.forgetUser(requireContext());
        NavDirections directions = navigateToSelf(ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE);
        Navigation.findNavController(binding.continueButton).navigate(directions);
    }

    void handleSignupButtonPress() {
        NavDirections directions = navigateToSelf(ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE);
        Navigation.findNavController(binding.continueButton).navigate(directions);
    }

    private void checkPhoneNumber() {
        if (!skipPhoneNumberCheck) {
            String phone = PhoneNumberHelper.buildPhoneNumber(binding.countryCode.getText().toString(),
                    binding.connectPrimaryPhoneInput.getText().toString());

            boolean valid = phoneNumberHelper.isValidPhoneNumber(phone);
            ConnectUserRecord user = ConnectIDManager.getInstance().getUser(getContext());

            if (valid) {
                String existingPrimary = user != null ? user.getPrimaryPhone() : existingPhone;
                String existingAlternate = user != null ? user.getAlternatePhone() : null;
                switch (callingClass) {
                    case ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE,
                            ConnectConstants.CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE,
                            ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE -> {
                        if (existingAlternate != null && existingAlternate.equals(phone)) {
                            updateUi(getString(R.string.connect_phone_not_alt));
                        } else {
                            updateUi(getString(R.string.connect_phone_checking));
                            callPhoneAvailableApi(phone);
                        }
                    }
                    case ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_CHANGE -> {
                        if (existingPrimary != null && existingPrimary.equals(phone)) {
                            updateUi(getString(R.string.connect_phone_not_primary));
                        } else {
                            updateUi("");
                        }
                    }
                }
            } else {
                updateUi(getString(R.string.connect_phone_invalid));
            }
        }
    }

    private void callPhoneAvailableApi(String phone) {
        ApiConnectId.checkPhoneAvailable(getContext(), phone,
                new IApiCallback() {
                    @Override
                    public void processSuccess(int responseCode, InputStream responseData) {
                        skipPhoneNumberCheck = false;
                        if (callingClass == ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE) {
                            updateUi(null);
                            createAccount();
                        } else if (callingClass == ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE) {
                            updateUi(getString(R.string.connect_phone_not_found));
                        }
                    }

                    @Override
                    public void processFailure(int responseCode) {
                        skipPhoneNumberCheck = false;
                        if (callingClass == ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE) {
                            updateUi(getString(R.string.connect_phone_unavailable));
                            NavDirections directions = navigateToPhonenNotAvailable(phone, ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE);
                            Navigation.findNavController(binding.continueButton).navigate(directions);
                        } else if (callingClass == ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE) {
                            ((ConnectIdActivity)activity).recoverPhone = phone;
                            NavDirections directions = navigateToBiometricConfig(ConnectConstants.CONNECT_RECOVERY_CONFIGURE_BIOMETRICS);
                            Navigation.findNavController(binding.continueButton).navigate(directions);
                        }
                    }

                    @Override
                    public void processNetworkFailure() {
                        skipPhoneNumberCheck = false;
                        updateUi(getString(R.string.recovery_network_unavailable));
                    }

                    @Override
                    public void processOldApiError() {
                        skipPhoneNumberCheck = false;
                        updateUi(getString(R.string.recovery_network_outdated));
                    }

                    @Override
                    public void processTokenUnavailableError() {
                        updateUi(getResources().getString(R.string.recovery_network_token_unavailable));
                    }

                    @Override
                    public void processTokenRequestDeniedError() {
                        updateUi(getResources().getString(R.string.recovery_network_token_request_rejected));
                    }
                });
    }

    private void createAccount() {
        clearError();
        String phoneNo = binding.countryCode.getText().toString() + binding.connectPrimaryPhoneInput.getText().toString();
        ConnectUserRecord tempUser = new ConnectUserRecord(phoneNo, generateUserId(), ConnectIDManager.getInstance().generatePassword(),
                binding.nameTextValue.getText().toString(), "");

        final Context context = getActivity();
        ApiConnectId.registerUser(activity, tempUser.getUserId(), tempUser.getPassword(),
                tempUser.getName(), phoneNo, new IApiCallback() {
                    @Override
                    public void processSuccess(int responseCode, InputStream responseData) {

                        ConnectUserRecord user = tempUser;
                        try {
                            String responseAsString = new String(
                                    StreamsUtil.inputStreamToByteArray(responseData));
                            JSONObject json = new JSONObject(responseAsString);
                            ConnectDatabaseHelper.handleReceivedDbPassphrase(context, json.getString(ConnectConstants.CONNECT_KEY_DB_KEY));
                            user.setSecondaryPhoneVerified(!json.has(ConnectConstants.CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY) || json.isNull(ConnectConstants.CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY));
                            if (!user.getSecondaryPhoneVerified()) {
                                user.setSecondaryPhoneVerifyByDate(DateUtils.parseDate(json.getString(ConnectConstants.CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY)));
                            }

                            ConnectUserDatabaseUtil.storeUser(context, user);
                            ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS);
                            NavDirections directions = navigateToBiometricConfig(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS);
                            Navigation.findNavController(binding.continueButton).navigate(directions);
                        } catch (IOException e) {
                            Logger.exception("Parsing return from confirm_secondary_otp", e);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }

                    }

                    @Override
                    public void processFailure(int responseCode) {
                        updateUi("Registration error: " + responseCode);
                    }

                    @Override
                    public void processNetworkFailure() {
                        updateUi(getResources().getString(R.string.recovery_network_unavailable));
                    }

                    @Override
                    public void processTokenUnavailableError() {
                        updateUi(getResources().getString(R.string.recovery_network_token_unavailable));
                    }

                    @Override
                    public void processTokenRequestDeniedError() {
                        updateUi(getResources().getString(R.string.recovery_network_token_request_rejected));
                    }

                    @Override
                    public void processOldApiError() {
                        updateUi(getResources().getString(R.string.recovery_network_outdated));
                    }
                });
    }

    private String generateUserId() {
        int idLength = 20;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder userId = new StringBuilder();
        SecureRandom secureRandom = new SecureRandom();
        for (int i = 0; i < idLength; i++) {
            userId.append(charSet.charAt(secureRandom.nextInt(charSet.length())));
        }

        return userId.toString();
    }

    void updateUi(String errorMessage) {
        updateButtonEnabled();
        if (errorMessage == null || errorMessage.isEmpty()) {
            clearError();
        } else {
            showError(errorMessage);
        }
    }

    private void showError(String errorMessage){
        binding.errorTextView.setVisibility(View.VISIBLE);
        binding.errorTextView.setText(errorMessage);
    }

    private void clearError(){
        binding.errorTextView.setVisibility(View.GONE);
    }


    private NavDirections navigateToBiometricConfig(int phase) {
        return ConnectIDSignupFragmentDirections.actionConnectidPhoneFragmentToConnectidBiometricConfig(phase);
    }

    private NavDirections navigateToPhonenNotAvailable(String phone, int phase) {
        return ConnectIDSignupFragmentDirections.actionConnectidPhoneFragmentToConnectidPhoneNotAvailable(phone,phase);
    }
    private NavDirections navigateToSelf(int phase) {
        return  ConnectIDSignupFragmentDirections.actionConnectidSignupFragmentSelf().setCallingClass(phase);
    }
}