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

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;

import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentSignupBinding;
import org.commcare.utils.ConnectIdAppBarUtils;
import org.commcare.utils.PhoneNumberHelper;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Random;

public class ConnectIDSignupFragment extends Fragment {
    private String existingPhone = "";
    private int callingClass = ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE;
    protected boolean skipPhoneNumberCheck = false;
    private FragmentSignupBinding binding;
    private boolean showhPhoneDialog = true;
    private ConnectUserRecord user;
    NavDirections directions = null;

    public ConnectIDSignupFragment() {
        // Required empty public constructor
    }

    public static ConnectIDSignupFragment newInstance() {
        ConnectIDSignupFragment fragment = new ConnectIDSignupFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentSignupBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        binding.connectConsentCheck.setOnClickListener(v -> updateButtonEnabled());
        if (getArguments() != null) {
            callingClass = ConnectIDSignupFragmentArgs.fromBundle(getArguments()).getCallingClass();
            existingPhone = ConnectIDSignupFragmentArgs.fromBundle(getArguments()).getPhone();
        }

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
                            Toast.makeText(getContext(), R.string.error_occured, Toast.LENGTH_SHORT).show();
                            throw new RuntimeException(e);
                        }

                    }
                }
        );

        binding.countryCode.setText(PhoneNumberHelper.setDefaultCountryCode(getContext()));
        binding.connectPrimaryPhoneInput.setOnFocusChangeListener(listener);
        binding.countryCode.setOnFocusChangeListener(listener);

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
        updateButtonEnabled();
        setupUi();
        if (!existingPhone.isEmpty()) {
            displayNumber(existingPhone);
        }

        handleAppBar(view);
        return view;
    }

    private void handleAppBar(View view) {
        View appBarView = view.findViewById(R.id.commonAppBar);
        ConnectIdAppBarUtils.setTitle(appBarView, getString(R.string.connect_registration_title));
        ConnectIdAppBarUtils.setBackButtonWithCallBack(appBarView, R.drawable.ic_connect_arrow_back, true, click -> {
            getActivity().finish();
        });
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

        boolean valid = PhoneNumberHelper.isValidPhoneNumber(getContext(), phone);

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

    void handleContinueButtonPress() {
        user = ConnectManager.getUser(getActivity());
        checkPhoneNumber();
    }

    void handleRecoverButtonPress() {
        ConnectManager.forgetUser("Initiating account recovery");
        directions = ConnectIDSignupFragmentDirections.actionConnectidSignupFragmentSelf().setCallingClass(ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE);
        Navigation.findNavController(binding.continueButton).navigate(directions);
    }

    void handleSignupButtonPress() {
        directions = ConnectIDSignupFragmentDirections.actionConnectidSignupFragmentSelf().setCallingClass(ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE);
        Navigation.findNavController(binding.continueButton).navigate(directions);
    }

    public void checkPhoneNumber() {
        if (!skipPhoneNumberCheck) {
            String phone = PhoneNumberHelper.buildPhoneNumber(binding.countryCode.getText().toString(),
                    binding.connectPrimaryPhoneInput.getText().toString());

            boolean valid = PhoneNumberHelper.isValidPhoneNumber(getContext(), phone);
            ConnectUserRecord user = ConnectManager.getUser(getContext());

            if (valid) {
                String existingPrimary = user != null ? user.getPrimaryPhone() : existingPhone;
                String existingAlternate = user != null ? user.getAlternatePhone() : null;
                String finalPhone = phone;
                switch (callingClass) {
                    case ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE,
                         ConnectConstants.CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE,
                         ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE -> {
                        if (existingPrimary != null && existingPrimary.equals(phone)) {
                            binding.errorTextView.setVisibility(View.GONE);
                            binding.errorTextView.setText("");
                        } else if (existingAlternate != null && existingAlternate.equals(phone)) {
                            binding.errorTextView.setVisibility(View.VISIBLE);
                            binding.errorTextView.setText(getString(R.string.connect_phone_not_alt));
                        } else {
                            //Make sure the number isn't already in use
                            phone = phone.replaceAll("\\+", "%2b");
                            binding.errorTextView.setVisibility(View.VISIBLE);
                            binding.errorTextView.setText(getString(R.string.connect_phone_checking));
                            boolean isBusy = !ApiConnectId.checkPhoneAvailable(getContext(), phone,
                                    new IApiCallback() {
                                        @Override
                                        public void processSuccess(int responseCode, InputStream responseData) {
                                            skipPhoneNumberCheck = false;
                                            if (callingClass == ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE) {
                                                binding.errorTextView.setVisibility(View.GONE);
                                                updateButtonEnabled();
                                                createAccount();
                                            } else if (callingClass == ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE) {
                                                binding.errorTextView.setVisibility(View.VISIBLE);
                                                binding.errorTextView.setText(getString(R.string.connect_phone_not_found));
                                            }
                                        }

                                        @Override
                                        public void processFailure(int responseCode, IOException e) {
                                            skipPhoneNumberCheck = false;
                                            if (e != null) {
                                                Logger.exception("Checking phone number", e);
                                            }
                                            if (callingClass == ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE) {
                                                updateButtonEnabled();
                                                binding.errorTextView.setVisibility(View.VISIBLE);
                                                binding.errorTextView.setText(getString(R.string.connect_phone_unavailable));
                                                directions = ConnectIDSignupFragmentDirections.actionConnectidPhoneFragmentToConnectidPhoneNotAvailable(finalPhone, ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE);
                                                Navigation.findNavController(binding.continueButton).navigate(directions);
                                            } else if (callingClass == ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE) {
                                                ConnectIdActivity.recoverPhone = finalPhone;
                                                updateButtonEnabled();
                                                directions = ConnectIDSignupFragmentDirections.actionConnectidPhoneFragmentToConnectidBiometricConfig(ConnectConstants.CONNECT_RECOVERY_CONFIGURE_BIOMETRICS);
                                                Navigation.findNavController(binding.continueButton).navigate(directions);
                                            }
                                        }

                                        @Override
                                        public void processNetworkFailure() {
                                            skipPhoneNumberCheck = false;
                                            updateButtonEnabled();
                                            binding.errorTextView.setVisibility(View.VISIBLE);
                                            binding.errorTextView.setText(getString(R.string.recovery_network_unavailable));
                                        }

                                        @Override
                                        public void processOldApiError() {
                                            skipPhoneNumberCheck = false;
                                            updateButtonEnabled();
                                            binding.errorTextView.setVisibility(View.VISIBLE);
                                            binding.errorTextView.setText(getString(R.string.recovery_network_outdated));
                                        }
                                    });

                            if (isBusy) {
                                Toast.makeText(getContext(), R.string.busy_message, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                    case ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_CHANGE -> {
                        if (existingPrimary != null && existingPrimary.equals(phone)) {
                            binding.errorTextView.setVisibility(View.VISIBLE);
                            binding.errorTextView.setText(getString(R.string.connect_phone_not_primary));
                        } else {
                            binding.errorTextView.setVisibility(View.GONE);
                            binding.errorTextView.setText("");
                        }
                    }
                }
            } else {
                binding.errorTextView.setVisibility(View.VISIBLE);
                binding.errorTextView.setText(getString(R.string.connect_phone_invalid));
            }
        }
    }

    public void createAccount() {
        binding.errorTextView.setText(null);
        binding.errorTextView.setVisibility(View.GONE);
        String phoneNo = binding.countryCode.getText().toString() + binding.connectPrimaryPhoneInput.getText().toString();
        ConnectUserRecord tempUser = new ConnectUserRecord(phoneNo, generateUserId(), ConnectManager.generatePassword(),
                binding.nameTextValue.getText().toString(), "");

        final Context context = getActivity();
        boolean isBusy = !ApiConnectId.registerUser(requireActivity(), tempUser.getUserId(), tempUser.getPassword(),
                tempUser.getName(), phoneNo, new IApiCallback() {
                    @Override
                    public void processSuccess(int responseCode, InputStream responseData) {
                        user = tempUser;
                        try {
                            String responseAsString = new String(
                                    StreamsUtil.inputStreamToByteArray(responseData));
                            JSONObject json = new JSONObject(responseAsString);
                            String key = ConnectConstants.CONNECT_KEY_DB_KEY;
                            if (json.has(key)) {
                                ConnectDatabaseHelper.handleReceivedDbPassphrase(context, json.getString(key));
                            }

                            key = ConnectConstants.CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY;
                            user.setSecondaryPhoneVerified(!json.has(key) || json.isNull(key));
                            if (!user.getSecondaryPhoneVerified()) {
                                user.setSecondaryPhoneVerifyByDate(DateUtils.parseDate(json.getString(key)));
                            }

                            ConnectDatabaseHelper.storeUser(context, user);

                            //            ConnectUserRecord dbUser = ConnectDatabaseHelper.getUser(getActivity());
                            ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS);
                            directions = ConnectIDSignupFragmentDirections.actionConnectidPhoneFragmentToConnectidBiometricConfig(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS);
                            Navigation.findNavController(binding.continueButton).navigate(directions);
                        } catch (IOException | JSONException e) {
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

    private String generateUserId() {
        int idLength = 20;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder userId = new StringBuilder();
        for (int i = 0; i < idLength; i++) {
            userId.append(charSet.charAt(new Random().nextInt(charSet.length())));
        }

        return userId.toString();
    }
}