package org.commcare.fragments.connectId;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectIDManager;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentSecondaryPhoneNumberBinding;
import org.commcare.utils.PhoneNumberHelper;

import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

public class ConnectIDSecondaryPhoneNumber extends Fragment {
    private int callingClass;
    private PhoneNumberHelper phoneNumberHelper;
    private static final String KEY_CALLING_CLASS = "calling_class";

    FragmentSecondaryPhoneNumberBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentSecondaryPhoneNumberBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        loadSavedState(savedInstanceState);
        phoneNumberHelper=PhoneNumberHelper.getInstance(requireActivity());
        setListener();
        callingClass = ConnectIDSecondaryPhoneNumberArgs.fromBundle(getArguments()).getCallingClass();
        String code = phoneNumberHelper.formatCountryCode(phoneNumberHelper.getCountryCodeFromLocale(requireActivity()));
        binding.countryCode.setText(code);
        updateButtonEnabled();
        requireActivity().setTitle(R.string.connect_phone_title_alternate);
        return view;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CALLING_CLASS, callingClass);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void loadSavedState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            callingClass = savedInstanceState.getInt(KEY_CALLING_CLASS);
        }
    }

    private void setListener() {
        TextWatcher codeWatcher = phoneNumberHelper.getCountryCodeWatcher(binding.countryCode);
        binding.countryCode.addTextChangedListener(codeWatcher);
        binding.connectPrimaryPhoneInput.addTextChangedListener(new TextWatcher() {
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
        });

        binding.continueButton.setOnClickListener(v -> onContinuePress());
    }

    private void updateButtonEnabled() {
        String phone = PhoneNumberHelper.buildPhoneNumber(binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString());

        boolean valid = phoneNumberHelper.isValidPhoneNumber(phone);
        String userPrimaryNumber = ConnectIDManager.getInstance().getUser(requireActivity()).getPrimaryPhone();
        if (userPrimaryNumber.equals(phone)) {
            binding.errorTextView.setVisibility(View.VISIBLE);
            binding.errorTextView.setText(R.string.primary_and_alternate_phone_number);
            valid = false;
        } else {
            binding.errorTextView.setVisibility(View.GONE);
        }
        binding.continueButton.setEnabled(valid);
    }

    private void onContinuePress() {
        if (getContext() == null) {
            return;
        }
        binding.continueButton.setEnabled(false);
        String phone = PhoneNumberHelper.buildPhoneNumber(binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString());
        ConnectUserRecord user = ConnectIDManager.getInstance().getUser(getContext());
        String existing = user.getAlternatePhone();
        if (existing != null && !existing.equals(phone)) {
            IApiCallback callback = new IApiCallback() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    binding.continueButton.setEnabled(true);
                    finish(true, phone);
                }

                @Override
                public void processFailure(int responseCode) {
                    binding.continueButton.setEnabled(true);
                    Toast.makeText(getContext(), getString(R.string.connect_phone_change_error),
                            Toast.LENGTH_SHORT).show();
                }

                @Override
                public void processNetworkFailure() {
                    binding.continueButton.setEnabled(true);
                    ConnectNetworkHelper.showNetworkError(getContext());
                }

                @Override
                public void processTokenUnavailableError() {
                    binding.continueButton.setEnabled(true);
                    ConnectNetworkHelper.handleTokenUnavailableException(requireActivity());
                }

                @Override
                public void processTokenRequestDeniedError() {
                    binding.continueButton.setEnabled(true);
                    ConnectNetworkHelper.handleTokenDeniedException(requireActivity());
                }

                @Override
                public void processOldApiError() {
                    binding.continueButton.setEnabled(true);
                    ConnectNetworkHelper.showOutdatedApiError(getContext());
                }
            };

            //Update the phone number with the server
            ApiConnectId.updateUserProfile(getContext(), user.getUserId(), user.getPassword(),
                    null, phone, callback);

        } else {
            finish(true, phone);
        }
    }

    private void finish(boolean success, String phone) {
        NavDirections directions = null;
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getActivity());
        switch (callingClass) {

            case ConnectConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE -> {
                if (success) {
                    phoneNumberHelper.storeAlternatePhone(getActivity(), user, phone);
                    directions = navigateToConnectidPin(ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN, phone, false, false);
                } else {
                    directions = navigateToConnectidPin(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_PIN, phone, false, true);
                }
            }
            case ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_CHANGE -> {
                directions = navigateToConnectidPhoneVerify(ConnectConstants.CONNECT_UNLOCK_VERIFY_ALT_PHONE, PersonalIdPhoneVerificationFragment.MethodVerifyAlternate, null, user.getUserId(), user.getPassword(), null, false);

            }
            case ConnectConstants.CONNECT_VERIFY_ALT_PHONE_CHANGE -> {
                if (success) {
                    directions = navigateToConnectidPhoneVerify(ConnectConstants.CONNECT_VERIFY_ALT_PHONE, PersonalIdPhoneVerificationFragment.MethodVerifyAlternate, null, user.getUserId(), user.getPassword(), null, false);
                } else {
                    directions = navigateToConnectidMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_VERIFY_ALT_PHONE_MESSAGE, getString(R.string.connect_password_fail_button), getString(R.string.connect_recovery_alt_change_button), null, null);
                }

            }
            default -> {
            }
        }
        if (directions == null) {
            throw new IllegalStateException(String.valueOf(R.string.connect_navigation_error));
        }
        Navigation.findNavController(binding.continueButton).navigate(directions);
    }

    private NavDirections navigateToConnectidPin(int phase, String phone, boolean recover, boolean change) {
        return ConnectIDSecondaryPhoneNumberDirections.actionConnectidSecondaryPhoneFragmentToConnectidPin(phase, phone, "").setRecover(recover).setChange(change);
    }

    private NavDirections navigateToConnectidPhoneVerify(int phase, int method, String phone, String userId, String password, String secretKey, boolean isRecovery) {
        return ConnectIDSecondaryPhoneNumberDirections.actionConnectidSecondaryPhoneFragmentToConnectidPhoneVerify(phase, String.valueOf(method), phone, userId, password, secretKey, isRecovery);
    }

    private NavDirections navigateToConnectidMessage(String title, String message, int phase, String button1Text, String button2Text, String phone, String secret) {
        return ConnectIDSecondaryPhoneNumberDirections.actionConnectidSecondaryPhoneFragmentToConnectidMessage(title, message, phase, button1Text, button2Text, phone, secret);
    }
}
