package org.commcare.fragments.connect;

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
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;

import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectPaymentSetupBinding;
import org.commcare.utils.PhoneNumberHelper;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class ConnectPaymentSetupFragment extends Fragment {

    private FragmentConnectPaymentSetupBinding binding;
    protected boolean skipPhoneNumberCheck = false;
    private boolean showhPhoneDialog = true;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentConnectPaymentSetupBinding.inflate(inflater, container, false);
        getActivity().setTitle(getString(R.string.connect_payment_info));
        clickListeners();

        View.OnFocusChangeListener listener = (v, hasFocus) -> {
            if (hasFocus && showhPhoneDialog) {
                PhoneNumberHelper.requestPhoneNumberHint(getActivity());
                showhPhoneDialog = false;
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
                            Logger.exception("Populating phone number from hint", e);
                        }
                    }
                }
        );

        binding.connectPrimaryPhoneInput.setOnFocusChangeListener(listener);
        binding.countryCode.setOnFocusChangeListener(listener);

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
        updateButtonEnabled();

        return binding.getRoot();
    }

    private void clickListeners() {
        binding.continueButton.setOnClickListener(view -> {
            submitPaymentDetail();
        });
    }

    private void submitPaymentDetail() {
        String phone = PhoneNumberHelper.buildPhoneNumber(binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString());
        ConnectUserRecord user = ConnectDatabaseHelper.getUser(getActivity());
        boolean isBusy = !ApiConnectId.paymentInfo(requireActivity(), phone, user.getUserId(), user.getPassword(), binding.nameTextValue.getText().toString(), new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    Navigation.findNavController(binding.continueButton).navigate(
                            ConnectPaymentSetupFragmentDirections.actionConnectPaymentSetupFragmentToConnectPaymentSetupPhoneVerificationFragment(phone,binding.nameTextValue.getText().toString(),user.getUserId(),user.getPassword()));
                } catch (Exception e) {
                    Logger.exception("Parsing return from confirm_secondary_otp", e);
                }
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                binding.errorTextView.setVisibility(View.VISIBLE);
                binding.errorTextView.setText(String.format(Locale.getDefault(), "Registration error: %d",
                        responseCode));
            }

            @Override
            public void processNetworkFailure() {
                Toast.makeText(requireActivity(), R.string.recovery_network_unavailable, Toast.LENGTH_SHORT).show();

            }

            @Override
            public void processOldApiError() {
                Toast.makeText(requireActivity(), R.string.recovery_network_outdated, Toast.LENGTH_SHORT).show();
            }
        });

        if (isBusy) {
            Toast.makeText(requireActivity(), R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    public void updateButtonEnabled() {
        String phone = PhoneNumberHelper.buildPhoneNumber(binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString());

        boolean valid = PhoneNumberHelper.isValidPhoneNumber(getContext(), phone);

        boolean isEnabled = valid &&
                binding.nameTextValue.getText().toString().length() > 0;
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
