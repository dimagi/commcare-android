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
import org.commcare.connect.ConnectManager;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
            existingPhone = ConnectIdPhoneFragmentArgs.fromBundle(getArguments()).getPhone();
        }
        binding.connectPrimaryPhoneInput.addTextChangedListener(watcher);
        binding.connectPrimaryPhoneButton.setOnClickListener(v -> handleButtonPress());
        //Special case for initial reg. screen. Remembering phone number before account has been created
        ConnectUserRecord user = ConnectManager.getUser(getActivity());
        String title = getString(R.string.connect_phone_title_primary);
        String message = getString(R.string.connect_phone_message_primary);
        binding.connectPrimaryPhoneTitle.setText(title);
        binding.connectPrimaryPhoneMessage.setText(message);
        displayNumber(existingPhone);
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
                ConnectIdPhoneFragmentDirections.actionConnectidPhoneNoToConnectidPhoneVerify(ConnectConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE, String.valueOf(
                                ConnectIdPhoneVerificationFragment.MethodRegistrationPrimary), phone, user.getUserId(),
                        user.getPassword(), null, false);

        Navigation.findNavController(binding.connectPrimaryPhoneButton).navigate(directions);
    }

    void displayNumber(String fullNumber) {
        int code = PhoneNumberHelper.getInstance(getContext()).getCountryCodeFromLocale(getContext());
        if (fullNumber != null && fullNumber.length() > 0) {
            code = PhoneNumberHelper.getInstance(getContext()).getCountryCode(fullNumber);
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
        if (user != null && existing != null && !existing.equals(phone)) {
            IApiCallback callback = new IApiCallback() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    skipPhoneNumberCheck = false;
                    finish(phone);
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
            ApiConnectId.changePhone(getContext(), user.getUserId(), user.getPassword(),
                    existing, phone, callback);
        } else {
            finish(phone);
        }
    }

    private void resetPhoneNumber() {
        skipPhoneNumberCheck = false;
        binding.connectPrimaryPhoneAvailability.setText("");
    }

    public void checkPhoneNumber() {
        if (!skipPhoneNumberCheck) {
            String phone =
                    binding.countryCode.getText().toString() + binding.connectPrimaryPhoneInput.getText().toString();

            boolean valid = PhoneNumberHelper.getInstance(getContext()).isValidPhoneNumber(phone);
            ConnectUserRecord user = ConnectManager.getUser(getContext());

            if (valid) {
                String existingPrimary = user != null ? user.getPrimaryPhone() : existingPhone;
                if (existingPrimary != null && existingPrimary.equals(phone)) {
                    binding.errorTextView.setText("");
                    binding.connectPrimaryPhoneButton.setEnabled(true);
                } else {
                    binding.connectPrimaryPhoneAvailability.setText(getString(R.string.connect_phone_checking));
                    binding.connectPrimaryPhoneButton.setEnabled(false);
                    ApiConnectId.checkPhoneAvailable(getContext(), phone,
                            new IApiCallback() {
                                @Override
                                public void processSuccess(int responseCode, InputStream responseData) {
                                    resetPhoneNumber();
                                    binding.errorTextView.setText(getString(R.string.connect_phone_available));
                                    binding.connectPrimaryPhoneButton.setEnabled(true);

                                }

                                @Override
                                public void processFailure(int responseCode) {
                                    resetPhoneNumber();
                                    binding.errorTextView.setText(getString(R.string.connect_phone_unavailable));
                                    binding.connectPrimaryPhoneButton.setEnabled(false);
                                }

                                @Override
                                public void processNetworkFailure() {
                                    resetPhoneNumber();
                                    binding.errorTextView.setText(getString(R.string.recovery_network_unavailable));
                                }

                                @Override
                                public void processTokenUnavailableError() {
                                    resetPhoneNumber();
                                    binding.errorTextView.setText(getString(R.string.recovery_network_token_unavailable));
                                }

                                @Override
                                public void processTokenRequestDeniedError() {
                                    resetPhoneNumber();
                                    binding.errorTextView.setText(getString(R.string.recovery_network_token_request_rejected));
                                }

                                @Override
                                public void processOldApiError() {
                                    resetPhoneNumber();
                                    binding.errorTextView.setText(getString(R.string.recovery_network_outdated));
                                }
                            });
                }
            }

        } else {
            binding.errorTextView.setText(getString(R.string.connect_phone_invalid));
            binding.connectPrimaryPhoneButton.setEnabled(false);
        }
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }
}