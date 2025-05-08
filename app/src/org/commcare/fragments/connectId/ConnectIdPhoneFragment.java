package org.commcare.fragments.connectId;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
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
import org.commcare.dalvik.databinding.ScreenConnectPrimaryPhoneBinding;
import org.commcare.utils.KeyboardHelper;
import org.commcare.utils.PhoneNumberHelper;

import java.io.InputStream;
import java.util.Locale;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectIdPhoneFragment#newInstance} factory method to
 * create an instance of getContext() fragment.
 */
public class ConnectIdPhoneFragment extends Fragment {

    private String existingPhone;
    private ScreenConnectPrimaryPhoneBinding binding;

    TextWatcher watcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            validatePhoneAndUpdateButton();
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for getContext() fragment
        binding = ScreenConnectPrimaryPhoneBinding.inflate(inflater, container, false);

        requireActivity().setTitle(getString(R.string.connect_phone_page_title));
        if (getArguments() != null) {
            existingPhone = ConnectIdPhoneFragmentArgs.fromBundle(getArguments()).getPhone();
        }
        binding.connectPrimaryPhoneInput.addTextChangedListener(watcher);
        binding.connectPrimaryPhoneButton.setOnClickListener(v -> isPhoneNoValidAndAvailable());
        //Special case for initial reg. screen. Remembering phone number before account has been created
        ConnectUserRecord user = ConnectIDManager.getInstance().getUser(getActivity());
        String title = getString(R.string.connect_phone_title_primary);
        String message = getString(R.string.connect_phone_message_primary);
        binding.connectPrimaryPhoneTitle.setText(title);
        binding.connectPrimaryPhoneMessage.setText(message);
        displayNumber();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        KeyboardHelper.showKeyboardOnInput(requireActivity(), binding.connectPrimaryPhoneInput);
    }

    public void finish(String phone) {
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getActivity());
        user.setPrimaryPhone(phone);
        ConnectUserDatabaseUtil.storeUser(getActivity(), user);
        ConnectDatabaseHelper.setRegistrationPhase(getActivity(),
                ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS);
        NavDirections directions =
                ConnectIdPhoneFragmentDirections.actionConnectidPhoneNoToConnectidPhoneVerify(
                        ConnectConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE,
                        String.valueOf(ConnectIdPhoneVerificationFragment.MethodRegistrationPrimary), phone,
                        user.getUserId(), user.getPassword(), null, false);
        Navigation.findNavController(binding.connectPrimaryPhoneButton).navigate(directions);
    }

    private void validatePhoneAndUpdateButton() {
        String phone = PhoneNumberHelper.buildPhoneNumber(binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString());

        boolean valid = PhoneNumberHelper.getInstance(requireActivity()).isValidPhoneNumber(phone);
        binding.connectPrimaryPhoneButton.setEnabled(valid);
    }

    private void displayNumber() {
        if (TextUtils.isEmpty(existingPhone)) {
            return;
        }
        PhoneNumberHelper phoneNumberHelper = PhoneNumberHelper.getInstance(getContext());
        int code = phoneNumberHelper.getCountryCode(existingPhone);
        String codeText = phoneNumberHelper.formatCountryCode(code);

        String displayPhoneNumber = existingPhone;
        if (displayPhoneNumber.startsWith(codeText)) {
            displayPhoneNumber = displayPhoneNumber.substring(codeText.length());
        }
        binding.connectPrimaryPhoneInput.setText(displayPhoneNumber);
        binding.countryCode.setText(codeText);
    }

    private void requestPhoneNumberUpdate() {
        String newPhoneNo = PhoneNumberHelper.buildPhoneNumber(binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString());
        if (!existingPhone.equals(newPhoneNo)) {
            IApiCallback callback = new IApiCallback() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    finish(newPhoneNo);
                }

                @Override
                public void processFailure(int responseCode) {
                    Toast.makeText(getContext(), getString(R.string.connect_phone_change_error),
                            Toast.LENGTH_SHORT).show();
                }

                @Override
                public void processNetworkFailure() {
                    ConnectNetworkHelper.showNetworkError(requireContext());
                }

                @Override
                public void processTokenUnavailableError() {
                    ConnectNetworkHelper.handleTokenUnavailableException(requireContext());
                }

                @Override
                public void processTokenRequestDeniedError() {
                    ConnectNetworkHelper.handleTokenDeniedException(requireContext());
                }

                @Override
                public void processOldApiError() {
                    ConnectNetworkHelper.showOutdatedApiError(requireContext());
                }
            };

            //Update the phone number with the server
            ConnectUserRecord user = ConnectIDManager.getInstance().getUser(requireContext());
            ApiConnectId.changePhone(getContext(), user.getUserId(), user.getPassword(),
                    existingPhone, newPhoneNo, callback);
        } else {
            finish(newPhoneNo);
        }
    }

    private void resetPhoneNumber() {
        binding.connectPrimaryPhoneAvailability.setText("");
    }

    private void displayMessage(String displayText, boolean isButtonEnabled) {
        binding.errorTextView.setText(displayText);
        binding.connectPrimaryPhoneButton.setEnabled(isButtonEnabled);
    }

    private void isPhoneNoValidAndAvailable() {
        String phone =
                binding.countryCode.getText().toString()
                        + binding.connectPrimaryPhoneInput.getText().toString();

        boolean valid = PhoneNumberHelper.getInstance(getContext()).isValidPhoneNumber(phone);
        ConnectUserRecord user = ConnectIDManager.getInstance().getUser(getContext());

        if (valid) {
            String existingPrimary = user != null ? user.getPrimaryPhone() : existingPhone;
            if (existingPrimary != null && existingPrimary.equals(phone)) {
                displayMessage(getString(R.string.connect_phone_invalid), false);
            } else {
                displayMessage(getString(R.string.connect_phone_checking), false);
                ApiConnectId.checkPhoneAvailable(getContext(), phone,
                        new IApiCallback() {
                            @Override
                            public void processSuccess(int responseCode, InputStream responseData) {
                                resetPhoneNumber();
                                displayMessage(getString(R.string.connect_phone_available), true);
                                requestPhoneNumberUpdate();
                            }

                            @Override
                            public void processFailure(int responseCode) {
                                resetPhoneNumber();
                                displayMessage(getString(R.string.connect_phone_unavailable), false);
                            }

                            @Override
                            public void processNetworkFailure() {
                                resetPhoneNumber();
                                displayMessage(getString(R.string.recovery_network_unavailable), false);
                            }

                            @Override
                            public void processTokenUnavailableError() {
                                resetPhoneNumber();
                                displayMessage(getString(R.string.recovery_network_token_unavailable), false);
                            }

                            @Override
                            public void processTokenRequestDeniedError() {
                                resetPhoneNumber();
                                displayMessage(getString(R.string.recovery_network_token_request_rejected),
                                        false);
                            }

                            @Override
                            public void processOldApiError() {
                                resetPhoneNumber();
                                displayMessage(getString(R.string.recovery_network_outdated), false);
                            }
                        });
            }

        } else {
            displayMessage(getString(R.string.connect_phone_invalid), false);
        }
    }
}
