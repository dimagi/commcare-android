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
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentSecondaryPhoneNumberBinding;
import org.commcare.utils.PhoneNumberHelper;

import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

public class ConnectIDSecondaryPhoneNumber extends Fragment {
    private String method;
    private int callingClass;
    private PhoneNumberHelper phoneNumberHelper;
    private static final String KEY_METHOD = "method";
    private static final String KEY_CALLING_CLASS = "calling_class";

    FragmentSecondaryPhoneNumberBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentSecondaryPhoneNumberBinding.inflate(inflater, container, false);
        getLoadState(savedInstanceState);
        setListener();
        method = ConnectIDSecondaryPhoneNumberArgs.fromBundle(getArguments()).getMethod();
        callingClass = ConnectIDSecondaryPhoneNumberArgs.fromBundle(getArguments()).getCallingClass();
        PhoneNumberHelper.getInstance(requireActivity());
        String code = "+" + phoneNumberHelper.getCountryCodeFromLocale(requireActivity());
        binding.countryCode.setText(code);
        updateButtonEnabled();
        requireActivity().setTitle(R.string.connect_phone_title_alternate);
        return binding.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CALLING_CLASS, callingClass);
        outState.putString(KEY_METHOD, method);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void getLoadState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            method = savedInstanceState.getString(KEY_METHOD);
            callingClass = savedInstanceState.getInt(KEY_CALLING_CLASS);
        }
    }

    private void setListener() {
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
        String existing = user.getPrimaryPhone();
        if (method.equals(ConnectConstants.METHOD_CHANGE_ALTERNATE)) {
            existing = user.getAlternatePhone();
        }
        if (existing != null && !existing.equals(phone)) {
            IApiCallback callback = new IApiCallback() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    binding.continueButton.setEnabled(true);
                    finish(true, phone);
                }

                @Override
                public void processFailure(int responseCode, IOException e) {
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
                public void processOldApiError() {
                    binding.continueButton.setEnabled(true);
                    ConnectNetworkHelper.showOutdatedApiError(getContext());
                }
            };

            //Update the phone number with the server
            if (method.equals(ConnectConstants.METHOD_CHANGE_ALTERNATE)) {
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

    private void finish(boolean success, String phone) {
        NavDirections directions = null;
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getActivity());
        switch (callingClass) {

            case ConnectConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE -> {
                if (success) {
                    user.setAlternatePhone(phone);
                    ConnectUserDatabaseUtil.storeUser(getActivity(), user);
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN);
                    directions = navigateToConnectidPin(ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN, phone, false, false);
                } else {
                    directions = navigateToConnectidPin(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_PIN, phone, false, true);
                }
            }
            case ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_CHANGE -> {
                directions = navigateToConnectidPhoneVerify(ConnectConstants.CONNECT_UNLOCK_VERIFY_ALT_PHONE, ConnectIdPhoneVerificationFragment.MethodVerifyAlternate, null, user.getUserId(), user.getPassword(), null, false);

            }
            case ConnectConstants.CONNECT_VERIFY_ALT_PHONE_CHANGE -> {
                if (success) {
                    directions = navigateToConnectidPhoneVerify(ConnectConstants.CONNECT_VERIFY_ALT_PHONE, ConnectIdPhoneVerificationFragment.MethodVerifyAlternate, null, user.getUserId(), user.getPassword(), null, false);
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