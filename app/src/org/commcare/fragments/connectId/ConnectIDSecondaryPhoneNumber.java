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
import java.util.Locale;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

public class ConnectIDSecondaryPhoneNumber extends Fragment {
    private String method;
    private String existingPhone;
    private int callingClass;
    protected boolean skipPhoneNumberCheck = false;
    private PhoneNumberHelper phoneNumberHelper;

    FragmentSecondaryPhoneNumberBinding binding;


    public ConnectIDSecondaryPhoneNumber() {
        // Required empty public constructor
    }

    public static ConnectIDSecondaryPhoneNumber newInstance() {
        return new ConnectIDSecondaryPhoneNumber();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentSecondaryPhoneNumberBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        if (getArguments() != null) {
            method = ConnectIDSecondaryPhoneNumberArgs.fromBundle(getArguments()).getMethod();
            existingPhone = ConnectIDSecondaryPhoneNumberArgs.fromBundle(getArguments()).getPhone();
            callingClass = ConnectIDSecondaryPhoneNumberArgs.fromBundle(getArguments()).getCallingClass();
        }
        phoneNumberHelper= new PhoneNumberHelper(requireActivity());
        String code = "+" + phoneNumberHelper.getCountryCode(requireActivity().getResources().getConfiguration().locale);
        binding.countryCode.setText(code);
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

        binding.continueButton.setOnClickListener(v -> handleButtonPress());
        binding.secondaryPhoneTitle.setText(getString(R.string.connect_phone_title_alternate));
        binding.secondaryPhoneSubTitle.setText(getString(R.string.connect_phone_message_alternate));
        updateButtonEnabled();
        requireActivity().setTitle(R.string.connect_phone_title_alternate);
        return view;
    }

    public void updateButtonEnabled() {
        String phone = phoneNumberHelper.buildPhoneNumber(binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString());

        boolean valid = phoneNumberHelper.isValidPhoneNumber(phone);

        binding.continueButton.setEnabled(valid);
    }

    public void handleButtonPress() {
        if(getContext()==null){
            return;
        }
        binding.continueButton.setEnabled(false);
        String phone = phoneNumberHelper.buildPhoneNumber(binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString());
        ConnectUserRecord user = ConnectIDManager.getInstance().getUser(getContext());
        String existing = user != null ? user.getPrimaryPhone() : existingPhone;
        if (method.equals(ConnectConstants.METHOD_CHANGE_ALTERNATE)) {
            existing = user != null ? user.getAlternatePhone() : null;
        }
        if (user != null && existing != null && !existing.equals(phone)) {
            IApiCallback callback = new IApiCallback() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    skipPhoneNumberCheck = false;
                    binding.continueButton.setEnabled(true);
                    finish(true, phone);
                }

                @Override
                public void processFailure(int responseCode, IOException e) {
                    skipPhoneNumberCheck = false;
                    binding.continueButton.setEnabled(true);
                    Toast.makeText(getContext(), getString(R.string.connect_phone_change_error),
                            Toast.LENGTH_SHORT).show();
                }

                @Override
                public void processNetworkFailure() {
                    skipPhoneNumberCheck = false;
                    binding.continueButton.setEnabled(true);
                    ConnectNetworkHelper.showNetworkError(getContext());
                }

                @Override
                public void processOldApiError() {
                    skipPhoneNumberCheck = false;
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

    public void finish(boolean success, String phone) {
        NavDirections directions = null;
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getActivity());
        switch (callingClass) {

            case ConnectConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE -> {
                if (success) {
                    user.setAlternatePhone(phone);
                    ConnectUserDatabaseUtil.storeUser(getActivity(), user);
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN);
                    directions = ConnectIDSecondaryPhoneNumberDirections.actionConnectidSecondaryPhoneFragmentToConnectidPin(ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN, phone, "").setRecover(false).setChange(false);
                } else {
                    directions = ConnectIDSecondaryPhoneNumberDirections.actionConnectidSecondaryPhoneFragmentToConnectidPin(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_PIN, phone, "").setRecover(false).setChange(true);
                }
            }
            case ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_CHANGE -> {
                directions = ConnectIDSecondaryPhoneNumberDirections.actionConnectidSecondaryPhoneFragmentToConnectidPhoneVerify(ConnectConstants.CONNECT_UNLOCK_VERIFY_ALT_PHONE, String.format(Locale.getDefault(), "%d",
                        ConnectIdPhoneVerificationFragment.MethodVerifyAlternate), null, user.getUserId(), user.getPassword(), null,false).setAllowChange(false);

            }
            case ConnectConstants.CONNECT_VERIFY_ALT_PHONE_CHANGE -> {
                if (success) {
                    directions = ConnectIDSecondaryPhoneNumberDirections.actionConnectidSecondaryPhoneFragmentToConnectidPhoneVerify(ConnectConstants.CONNECT_VERIFY_ALT_PHONE, String.format(Locale.getDefault(), "%d",
                            ConnectIdPhoneVerificationFragment.MethodVerifyAlternate), null, user.getUserId(), user.getPassword(), null,false).setAllowChange(false);
                } else {
                    directions = ConnectIDSecondaryPhoneNumberDirections.actionConnectidSecondaryPhoneFragmentToConnectidMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_VERIFY_ALT_PHONE_MESSAGE, getString(R.string.connect_password_fail_button), getString(R.string.connect_recovery_alt_change_button), null, null);
                }

            }
            default -> {
            }
        }
        if (directions == null) {
            Toast.makeText(getContext(), R.string.connect_navigation_error, Toast.LENGTH_SHORT).show();
            return;
        }
        Navigation.findNavController(binding.continueButton).navigate(directions);
    }
}