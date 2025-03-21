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

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;

import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenConnectPrimaryPhoneBinding;
import org.commcare.utils.ConnectIdAppBarUtils;
import org.commcare.utils.KeyboardHelper;
import org.commcare.utils.PhoneNumberHelper;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

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

    public ConnectIdPhoneFragment() {
        // Required empty public constructor
    }

    public static ConnectIdPhoneFragment newInstance() {
        ConnectIdPhoneFragment fragment = new ConnectIdPhoneFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for getContext() fragment
        binding = ScreenConnectPrimaryPhoneBinding.inflate(inflater, container, false);

        requireActivity().setTitle(getString(R.string.connect_phone_page_title));
        if (getArguments() != null) {
            method = ConnectIdPhoneFragmentArgs.fromBundle(getArguments()).getMethod();
            existingPhone = ConnectIdPhoneFragmentArgs.fromBundle(getArguments()).getPhone();
            callingClass = ConnectIdPhoneFragmentArgs.fromBundle(getArguments()).getCallingClass();
        }

        View.OnFocusChangeListener listener = (v, hasFocus) -> {
            if (hasFocus && callingClass == ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE) {
                PhoneNumberHelper.requestPhoneNumberHint(getActivity());
            }
        };

        PhoneNumberHelper.phoneNumberHintLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        String phoneNumber;
                        try {
                            phoneNumber = Identity.getSignInClient(requireActivity()).getPhoneNumberFromIntent(data);
                            displayNumber(phoneNumber);
                        } catch (ApiException e) {
                            Toast.makeText(getContext(), R.string.error_occured, Toast.LENGTH_SHORT).show();
                            throw new RuntimeException(e);
                        }

                    }
                }
        );

        binding.countryCode.setOnFocusChangeListener(listener);
        binding.connectPrimaryPhoneInput.setOnFocusChangeListener(listener);
        binding.connectPrimaryPhoneInput.addTextChangedListener(watcher);

        binding.connectPrimaryPhoneButton.setOnClickListener(v -> handleButtonPress());
        //Special case for initial reg. screen. Remembering phone number before account has been created

        ConnectUserRecord user = ConnectManager.getUser(getActivity());
        String title = getString(R.string.connect_phone_title_primary);
        String message = getString(R.string.connect_phone_message_primary);
        String existing = user != null ? user.getPrimaryPhone() : existingPhone;
        binding.connectPrimaryPhoneTitle.setText(title);
        binding.connectPrimaryPhoneMessage.setText(message);
        displayNumber(existing);
        handleAppBar(binding.getRoot());
        return binding.getRoot();
    }

    private void handleAppBar(View view) {
        View appBarView = view.findViewById(R.id.commonAppBar);
        ConnectIdAppBarUtils.setTitle(appBarView, getString(R.string.connect_phone_title_primary));
        ConnectIdAppBarUtils.setBackButtonWithCallBack(appBarView, R.drawable.ic_connect_arrow_back, true, click -> {
            Navigation.findNavController(appBarView).popBackStack();
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        checkPhoneNumber();

        KeyboardHelper.showKeyboardOnInput(requireActivity(), binding.connectPrimaryPhoneInput);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String phone = PhoneNumberHelper.handlePhoneNumberPickerResult(requestCode, resultCode, data, getActivity());
        skipPhoneNumberCheck = false;
        displayNumber(phone);
    }

    public void finish(boolean success, String phone) {
        NavDirections directions = null;
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getActivity());
        switch (callingClass) {

            case ConnectConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE -> {
                if (success) {
                    user.setAlternatePhone(phone);
                    ConnectUserDatabaseUtil.storeUser(getActivity(), user);
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN);
                    directions = ConnectIdPhoneFragmentDirections.actionConnectidPhoneNoToConnectidPin(ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN, phone, "").setRecover(false).setChange(false);
                } else {
                    directions = ConnectIdPhoneFragmentDirections.actionConnectidPhoneNoToConnectidPin(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_PIN, phone, "").setRecover(false).setChange(true);
                }
            }
            case ConnectConstants.CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE -> {
                if (success) {
                    user.setPrimaryPhone(phone);
                    ConnectUserDatabaseUtil.storeUser(getActivity(), user);
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE);
                }
                directions = ConnectIdPhoneFragmentDirections.actionConnectidPhoneNoToConnectidPhoneVerify(ConnectConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE, String.format(Locale.getDefault(), "%d",
                        ConnectIdPhoneVerificationFragmnet.MethodRegistrationPrimary), phone, user.getUserId(), user.getPassword(), null, false).setAllowChange(true);
            }
            case ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE -> {
                if (success) {
                    ConnectIdActivity.recoverPhone = phone;
                    directions = ConnectIdPhoneFragmentDirections.actionConnectidPhoneNoToConnectidBiometricConfig(ConnectConstants.CONNECT_RECOVERY_CONFIGURE_BIOMETRICS);
                }
            }
            case ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_CHANGE -> {
                directions = ConnectIdPhoneFragmentDirections.actionConnectidPhoneNoToConnectidPhoneVerify(ConnectConstants.CONNECT_UNLOCK_VERIFY_ALT_PHONE, String.format(Locale.getDefault(), "%d",
                        ConnectIdPhoneVerificationFragmnet.MethodVerifyAlternate), null, user.getUserId(), user.getPassword(), null, false).setAllowChange(false);

            }
            case ConnectConstants.CONNECT_VERIFY_ALT_PHONE_CHANGE -> {
                if (success) {
                    directions = ConnectIdPhoneFragmentDirections.actionConnectidPhoneNoToConnectidPhoneVerify(ConnectConstants.CONNECT_VERIFY_ALT_PHONE, String.format(Locale.getDefault(), "%d",
                            ConnectIdPhoneVerificationFragmnet.MethodVerifyAlternate), null, user.getUserId(), user.getPassword(), null, false).setAllowChange(false);
                } else {
                    directions = ConnectIdPhoneFragmentDirections.actionConnectidPhoneNoToConnectidMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_VERIFY_ALT_PHONE_MESSAGE, getString(R.string.connect_password_fail_button), getString(R.string.connect_recovery_alt_change_button), null, null);
                }

            }
            default -> {
            }
        }
        assert directions != null;
        Navigation.findNavController(binding.connectPrimaryPhoneButton).navigate(directions);
    }

    //8556
    void displayNumber(String fullNumber) {
        int code = PhoneNumberHelper.getCountryCode(getContext());
        if (fullNumber != null && fullNumber.length() > 0) {
            code = PhoneNumberHelper.getCountryCode(getContext(), fullNumber);
        }

        String codeText = "";
        if (code > 0) {
            codeText = String.format(Locale.getDefault(), "%d", code);
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

    public void handleButtonPress() {
        String phone = PhoneNumberHelper.buildPhoneNumber(binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString());
        ConnectUserRecord user = ConnectManager.getUser(getContext());
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
                    ConnectNetworkHelper.showNetworkError(requireContext());
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
                    ConnectNetworkHelper.showOutdatedApiError(requireContext());
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
            finish(true, phone);
        }
    }

    public void checkPhoneNumber() {
        if (!skipPhoneNumberCheck) {
            String phone = binding.countryCode.getText().toString() + binding.connectPrimaryPhoneInput.getText().toString();

            boolean valid = PhoneNumberHelper.isValidPhoneNumber(getContext(), phone);
            ConnectUserRecord user = ConnectManager.getUser(getContext());

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
//                            phone = phone.replaceAll("\\+", "%2b");
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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }
}