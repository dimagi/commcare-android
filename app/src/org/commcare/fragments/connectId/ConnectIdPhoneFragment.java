package org.commcare.fragments.connectId;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenConnectPrimaryPhoneBinding;
import org.commcare.utils.KeyboardHelper;
import org.commcare.utils.PhoneNumberHelper;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectIdPhoneFragment#newInstance} factory method to
 * create an instance of getContext() fragment.
 */
public class ConnectIdPhoneFragment extends Fragment {

    private String method;
    private String existingPhone;
    private int callingClass;
    private ScreenConnectPrimaryPhoneBinding binding;
    protected boolean skipPhoneNumberCheck = false;
    private static final String KEY_PHONE = "phone";
    private static final String KEY_METHOD = "method";
    private static final String KEY_CALLING_CLASS = "calling_class";
    private PhoneNumberHelper phoneNumberHelper;
    private Activity activity;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for getContext() fragment
        binding = ScreenConnectPrimaryPhoneBinding.inflate(inflater, container, false);
        View view = binding.getRoot();


        activity = requireActivity();

        activity.setTitle(getString(R.string.connect_phone_page_title));

        phoneNumberHelper = PhoneNumberHelper.getInstance(activity);

        setLisetner();
        setArguments();
        loadSavedState(savedInstanceState);

        //Special case for initial reg. screen. Remembering phone number before account has been created

        ConnectUserRecord user = ConnectIDManager.getInstance().getUser(getActivity());
        String title = getString(R.string.connect_phone_title_primary);
        String message = getString(R.string.connect_phone_message_primary);
        if (user == null && existingPhone == null) {
            Logger.log("Null Exception", "User and existing phone cannot be null together");
        }
        String existing = user != null ? user.getPrimaryPhone() : existingPhone;
        binding.connectPrimaryPhoneTitle.setText(title);
        binding.connectPrimaryPhoneMessage.setText(message);
        displayNumber(existing);
        activity.setTitle(R.string.connect_phone_title_primary);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        checkPhoneNumber();

        KeyboardHelper.showKeyboardOnInput(getActivity(), binding.connectPrimaryPhoneInput);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String phone = PhoneNumberHelper.handlePhoneNumberPickerResult(requestCode, resultCode, data, getActivity());
        skipPhoneNumberCheck = false;
        displayNumber(phone);
    }

    private void setLisetner() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkPhoneNumber();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        View.OnFocusChangeListener listener = (v, hasFocus) -> {
            if (hasFocus && callingClass == ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE) {
                PhoneNumberHelper.requestPhoneNumberHint(null, getActivity());
            }
        };
        binding.countryCode.setOnFocusChangeListener(listener);
        binding.connectPrimaryPhoneInput.setOnFocusChangeListener(listener);
        binding.connectPrimaryPhoneInput.addTextChangedListener(watcher);

        binding.connectPrimaryPhoneButton.setOnClickListener(v -> verifyPhone());
    }

    private void setArguments() {
        if (getArguments() != null) {
            method = ConnectIdPhoneFragmentArgs.fromBundle(getArguments()).getMethod();
            existingPhone = ConnectIdPhoneFragmentArgs.fromBundle(getArguments()).getPhone();
            callingClass = ConnectIdPhoneFragmentArgs.fromBundle(getArguments()).getCallingClass();
        }
    }

    private void loadSavedState(Bundle outState) {
        existingPhone = outState.getString(KEY_PHONE);
        method = outState.getString(KEY_METHOD);
        callingClass = outState.getInt(KEY_CALLING_CLASS);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_PHONE, existingPhone);
        outState.putString(KEY_METHOD, method);
        outState.putInt(KEY_CALLING_CLASS, callingClass);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void finish(boolean success, String phone) {
        NavDirections directions = null;
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getActivity());
        switch (callingClass) {

            case ConnectConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE -> {
                if (success) {
                    user.setAlternatePhone(phone);
                    ConnectUserDatabaseUtil.storeUser(getActivity(), user);
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN);
                    directions = navigateToPin(ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN, phone, "");
                } else {
                    directions = navigateToPin(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_PIN, phone, "");
                }
            }
            case ConnectConstants.CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE -> {
                if (success) {
                    user.setPrimaryPhone(phone);
                    ConnectUserDatabaseUtil.storeUser(getActivity(), user);
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE);
                }
                directions = navigateToPhoneVerify(ConnectConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE, String.valueOf(
                        ConnectIdPhoneVerificationFragment.MethodRegistrationPrimary), phone, user.getUserId(), user.getPassword(), null, false);
            }
            case ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE -> {
                if (success) {
                    ((ConnectIdActivity)activity).recoverPhone = phone;
                    directions = navigateToBiometricConfig();
                }
            }
            case ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_CHANGE -> {
                directions = navigateToPhoneVerify(ConnectConstants.CONNECT_UNLOCK_VERIFY_ALT_PHONE, String.valueOf(
                        ConnectIdPhoneVerificationFragment.MethodVerifyAlternate), null, user.getUserId(), user.getPassword(), null, false);

            }
            case ConnectConstants.CONNECT_VERIFY_ALT_PHONE_CHANGE -> {
                if (success) {
                    directions = navigateToPhoneVerify(ConnectConstants.CONNECT_VERIFY_ALT_PHONE, String.valueOf(
                            ConnectIdPhoneVerificationFragment.MethodVerifyAlternate), null, user.getUserId(), user.getPassword(), null, false);
                } else {
                    directions = navigateToMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_VERIFY_ALT_PHONE_MESSAGE, getString(R.string.connect_password_fail_button), getString(R.string.connect_recovery_alt_change_button), null, null);
                }

            }
            default -> {
            }
        }
        if (directions == null) {
            throw new IllegalStateException("Navigation directions is null. Unable to navigate.");
        }
        Navigation.findNavController(binding.connectPrimaryPhoneButton).navigate(directions);
    }

    private NavDirections navigateToPin(int phase, String phone, String message) {
        return ConnectIdPhoneFragmentDirections.actionConnectidPhoneNoToConnectidPin(phase, phone, message).setRecover(false).setChange(false);
    }

    private NavDirections navigateToPhoneVerify(int phase, String method, String phone, String userId, String password, String secretKey, boolean isRecovery) {
        return ConnectIdPhoneFragmentDirections.actionConnectidPhoneNoToConnectidPhoneVerify(phase, method, phone, userId, password, secretKey, isRecovery);
    }

    private NavDirections navigateToBiometricConfig() {
        return ConnectIdPhoneFragmentDirections.actionConnectidPhoneNoToConnectidBiometricConfig(ConnectConstants.CONNECT_RECOVERY_CONFIGURE_BIOMETRICS);
    }

    private NavDirections navigateToMessage(String title, String message, int phase, String button1Text, String button2Text, String userName, String password) {
        return ConnectIdPhoneFragmentDirections.actionConnectidPhoneNoToConnectidMessage(title, message, phase, button1Text, button2Text, userName, password);
    }

    private void displayNumber(String fullNumber) {
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

    private void verifyPhone() {
        String phone = PhoneNumberHelper.buildPhoneNumber(binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString());
        ConnectUserRecord user = ConnectIDManager.getInstance().getUser(getContext());
        String existing = user != null ? user.getPrimaryPhone() : existingPhone;
        if (callingClass == ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_CHANGE || callingClass == ConnectConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE) {
            existing = user != null ? user.getAlternatePhone() : null;
        }
        if (user != null && existing != null && !existing.equals(phone)) {
            IApiCallback callback = new IApiCallback() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    skipPhoneNumberCheck = false;
                    finish(true, phone);
                }

                @Override
                public void processFailure(int responseCode) {
                    skipPhoneNumberCheck = false;
                    Toast.makeText(getContext(), getString(R.string.connect_phone_change_error),
                            Toast.LENGTH_SHORT).show();
                }

                @Override
                public void processNetworkFailure() {
                    skipPhoneNumberCheck = false;
                    ConnectNetworkHelper.showNetworkError(getContext());
                }

                @Override
                public void processTokenUnavailableError() {
                    skipPhoneNumberCheck = false;
                    ConnectNetworkHelper.handleTokenUnavailableException(requireContext());
                }

                @Override
                public void processTokenRequestDeniedError() {
                    skipPhoneNumberCheck = false;
                    ConnectNetworkHelper.handleTokenRequestDeniedException(requireContext());
                }

                @Override
                public void processOldApiError() {
                    skipPhoneNumberCheck = false;
                    ConnectNetworkHelper.showOutdatedApiError(getContext());
                }
            };

            //Update the phone number with the server
            if (callingClass == ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_CHANGE || callingClass == ConnectConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE) {
                ApiConnectId.updateUserProfile(getContext(), user.getUserId(), user.getPassword(),
                        null, phone, callback);
            } else {
                ApiConnectId.changePhone(getContext(), user.getUserId(), user.getPassword(),
                        existing, phone, callback);
            }
        } else {
            if (phone.isEmpty()) {
                Toast.makeText(getContext(), "Phone number is empty or invalid", Toast.LENGTH_SHORT).show();
                return;
            }
            finish(true, phone);
        }
    }

    private void checkPhoneNumber() {
        if (!skipPhoneNumberCheck) {
            String phone = binding.countryCode.getText().toString() + binding.connectPrimaryPhoneInput.getText().toString();

            boolean valid = phoneNumberHelper.isValidPhoneNumber(phone);
            ConnectUserRecord user = ConnectIDManager.getInstance().getUser(getContext());

            if (valid) {
                String existingPrimary = user != null ? user.getPrimaryPhone() : existingPhone;
                String existingAlternate = user != null ? user.getAlternatePhone() : null;
                switch (method) {
                    case ConnectConstants.METHOD_REGISTER_PRIMARY,
                            ConnectConstants.METHOD_CHANGE_PRIMARY -> {
                        if (existingPrimary != null && existingPrimary.equals(phone)) {
                            binding.errorTextView.setText("");
                            binding.connectPrimaryPhoneButton.setEnabled(true);
                        } else if (existingAlternate != null && existingAlternate.equals(phone)) {
                            binding.errorTextView.setText(getString(R.string.connect_phone_not_alt));
                            binding.connectPrimaryPhoneButton.setEnabled(false);
                        } else {
                            //Make sure the number isn't already in use
                            binding.connectPrimaryPhoneAvailability.setText(getString(R.string.connect_phone_checking));
                            binding.connectPrimaryPhoneButton.setEnabled(false);
                            ApiConnectId.checkPhoneAvailable(getContext(), phone,
                                    new IApiCallback() {
                                        private void completeCall() {
                                            skipPhoneNumberCheck = false;
                                            binding.connectPrimaryPhoneAvailability.setText("");
                                        }

                                        @Override
                                        public void processSuccess(int responseCode, InputStream responseData) {
                                            completeCall();
                                            binding.errorTextView.setText(getString(R.string.connect_phone_available));
                                            binding.connectPrimaryPhoneButton.setEnabled(true);

                                        }

                                        @Override
                                        public void processFailure(int responseCode) {
                                            completeCall();
                                            binding.errorTextView.setText(getString(R.string.connect_phone_unavailable));
                                            binding.connectPrimaryPhoneButton.setEnabled(false);
                                        }

                                        @Override
                                        public void processNetworkFailure() {
                                            completeCall();
                                            binding.errorTextView.setText(getString(R.string.recovery_network_unavailable));
                                        }

                                        @Override
                                        public void processTokenUnavailableError() {
                                            completeCall();
                                            binding.errorTextView.setText(getString(R.string.recovery_network_token_unavailable));
                                        }

                                        @Override
                                        public void processTokenRequestDeniedError() {
                                            completeCall();
                                            binding.errorTextView.setText(getString(R.string.recovery_network_token_request_rejected));
                                        }

                                        @Override
                                        public void processOldApiError() {
                                            completeCall();
                                            binding.errorTextView.setText(getString(R.string.recovery_network_outdated));
                                        }
                                    });
                        }
                    }
                    case ConnectConstants.METHOD_CHANGE_ALTERNATE -> {
                        if (existingPrimary != null && existingPrimary.equals(phone)) {
                            binding.errorTextView.setText(getString(R.string.connect_phone_not_primary));
                            binding.connectPrimaryPhoneButton.setEnabled(false);
                        } else {
                            binding.errorTextView.setText("");
                            binding.connectPrimaryPhoneButton.setEnabled(true);
                        }
                    }
                    case ConnectConstants.METHOD_RECOVER_PRIMARY -> {
                        binding.errorTextView.setText("");
                        binding.connectPrimaryPhoneButton.setEnabled(true);
                    }
                }
            } else {
                binding.errorTextView.setText(getString(R.string.connect_phone_invalid));
                binding.connectPrimaryPhoneButton.setEnabled(false);
            }
        }
    }
}