package org.commcare.fragments.connectId;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.os.Bundle;

import androidx.databinding.adapters.TextViewBindingAdapter;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.tasks.OnSuccessListener;

import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentPhoneBinding;
import org.commcare.utils.PhoneNumberHelper;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Locale;
import java.util.Random;

public class ConnectIDSignupFragment extends Fragment {
    private String existingPhone = "";
    private int callingClass = 1003;
    protected boolean skipPhoneNumberCheck = false;
    private FragmentPhoneBinding binding;
    private boolean isValidNo;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentPhoneBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        binding.connectConsentCheck.setOnClickListener(v -> updateState());
        if (getArguments() != null) {
            existingPhone = ConnectIDSignupFragmentArgs.fromBundle(getArguments()).getPhone();
            callingClass = ConnectIDSignupFragmentArgs.fromBundle(getArguments()).getCallingClass();
        }

        View.OnFocusChangeListener listener = (v, hasFocus) -> {
            if(hasFocus) {
                PhoneNumberHelper.requestPhoneNumberHint(getActivity());
            }
        };

        binding.connectPrimaryPhoneInput.setOnFocusChangeListener(listener);
        binding.countryCode.setOnFocusChangeListener(listener);
        binding.countryCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(!s.toString().contains("+")){
                    binding.countryCode.setText("+"+binding.countryCode.getText());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        enableButton();
        setupUi();
        if (!existingPhone.isEmpty()) {
            displayNumber(existingPhone);
        }

        getActivity().setTitle("ConnectID Signup");
        return view;
    }

    void setupUi() {
        if (callingClass == ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE) {
            binding.nameLayout.setVisibility(View.GONE);
            binding.phoneTitle.setText("ConnectID Recovery");
            binding.buttonTitle.setText("Don’t have Connect ID?");
            binding.recoverButton.setText("Signup");
            binding.connectConsentCheck.setVisibility(View.GONE);
            binding.recoverButton.setOnClickListener(v -> handleSignupButtonPress());
            binding.phoneSubText.setVisibility(View.GONE);
            binding.continueButton.setOnClickListener(v -> handleContinueButtonPress());

        } else {
            binding.nameLayout.setVisibility(View.VISIBLE);
            binding.phoneTitle.setText("ConnectID SignUp");
            binding.checkText.setText(getString(R.string.connect_consent_message_1));
            binding.checkText.setMovementMethod(LinkMovementMethod.getInstance());
            binding.buttonTitle.setText("Already have an account?");
            binding.recoverButton.setText("Recover");
            binding.phoneSubText.setVisibility(View.GONE);
            binding.connectConsentCheck.setVisibility(View.VISIBLE);
            binding.recoverButton.setOnClickListener(v -> handleRecoverButtonPress());
            binding.continueButton.setOnClickListener(v -> handleContinueButtonPress());
        }
    }

    public void enableButton() {
//        binding.continueButton.setEnabled(isEnabled);
//        binding.continueButton.setBackgroundColor(isEnabled?getResources().getColor(R.color.connect_blue_color):Color.GRAY);
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
        directions = ConnectIDSignupFragmentDirections.actionConnectidPhoneFragmentSelf().setCallingClass(ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE);
        Navigation.findNavController(binding.continueButton).navigate(directions);
    }

    void handleSignupButtonPress() {
        directions = ConnectIDSignupFragmentDirections.actionConnectidPhoneFragmentSelf().setCallingClass(ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE);
        Navigation.findNavController(binding.continueButton).navigate(directions);
    }

    public void updateState() {
        binding.continueButton.setEnabled(binding.connectConsentCheck.isChecked());
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
                            binding.errorTextView.setText("");
                        } else if (existingAlternate != null && existingAlternate.equals(phone)) {
                            binding.errorTextView.setText(getString(R.string.connect_phone_not_alt));
                        } else {
                            //Make sure the number isn't already in use
                            phone = phone.replaceAll("\\+", "%2b");
                            binding.errorTextView.setText(getString(R.string.connect_phone_checking));
                            boolean isBusy = !ApiConnectId.checkPhoneAvailable(getContext(), phone,
                                    new IApiCallback() {
                                        @Override
                                        public void processSuccess(int responseCode, InputStream responseData) {
                                            skipPhoneNumberCheck = false;
                                            if (callingClass == ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE) {
                                                isValidNo=true;
                                                enableButton();
                                                createAccount();
                                            } else if (callingClass == ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE) {
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
                                                isValidNo=false;
                                                enableButton();
                                                binding.errorTextView.setText(getString(R.string.connect_phone_unavailable));
                                                directions = ConnectIDSignupFragmentDirections.actionConnectidPhoneFragmentToConnectidPhoneNotAvailable(finalPhone, ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE);
                                                Navigation.findNavController(binding.continueButton).navigate(directions);
                                            } else if (callingClass == ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE) {
                                                ConnectIdActivity.recoverPhone = finalPhone;
                                                isValidNo=true;
                                                enableButton();
                                                directions = ConnectIDSignupFragmentDirections.actionConnectidPhoneFragmentToConnectidBiometricConfig(ConnectConstants.CONNECT_RECOVERY_CONFIGURE_BIOMETRICS);
                                                Navigation.findNavController(binding.continueButton).navigate(directions);
                                            }
                                        }

                                        @Override
                                        public void processNetworkFailure() {
                                            skipPhoneNumberCheck = false;
                                            isValidNo=false;
                                            enableButton();
                                            binding.errorTextView.setText(getString(R.string.recovery_network_unavailable));
                                        }

                                        @Override
                                        public void processOldApiError() {
                                            skipPhoneNumberCheck = false;
                                            isValidNo=false;
                                            enableButton();
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
                            binding.errorTextView.setText(getString(R.string.connect_phone_not_primary));
                        } else {
                            binding.errorTextView.setText("");
                        }
                    }
                }
            } else {
                binding.errorTextView.setText(getString(R.string.connect_phone_invalid));
            }
        }
    }

    public void createAccount() {
        binding.errorTextView.setText(null);
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
                                user.setSecondaryPhoneVerifyByDate(ConnectNetworkHelper.parseDate(json.getString(key)));
                            }

                            ConnectDatabaseHelper.storeUser(context, user);

                            //            ConnectUserRecord dbUser = ConnectDatabaseHelper.getUser(getActivity());
                            ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS);
                            directions = ConnectIDSignupFragmentDirections.actionConnectidPhoneFragmentToConnectidBiometricConfig(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS);
                            Navigation.findNavController(binding.continueButton).navigate(directions);
                        } catch (IOException | JSONException | ParseException e) {
                            Logger.exception("Parsing return from confirm_secondary_otp", e);
                        }

                    }

                    @Override
                    public void processFailure(int responseCode, IOException e) {
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