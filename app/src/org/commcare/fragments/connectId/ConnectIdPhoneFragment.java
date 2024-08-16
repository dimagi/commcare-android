package org.commcare.fragments.connectId;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.tasks.OnSuccessListener;

import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.ConnectTask;
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
        String existing;
        binding = ScreenConnectPrimaryPhoneBinding.inflate(inflater,container,false);
        View view = binding.getRoot();
        binding.connectPrimaryPhoneCountryInput.addTextChangedListener(watcher);
        binding.connectPrimaryPhoneInput.addTextChangedListener(watcher);
        binding.connectPrimaryPhoneButton.setOnClickListener(v -> handleButtonPress());
        requireActivity().setTitle(getString(R.string.connect_phone_page_title));
        if (getArguments() != null) {
            method = ConnectIdPhoneFragmentArgs.fromBundle(getArguments()).getMethod();
            existingPhone = ConnectIdPhoneFragmentArgs.fromBundle(getArguments()).getPhone();
            callingClass = ConnectIdPhoneFragmentArgs.fromBundle(getArguments()).getCallingClass();
        }
        //Special case for initial reg. screen. Remembering phone number before account has been created

        ConnectUserRecord user = ConnectManager.getUser(getActivity());
        String title = getString(R.string.connect_phone_title_primary);
        String message = getString(R.string.connect_phone_message_primary);

        if (!method.equals(ConnectConstants.METHOD_CHANGE_ALTERNATE))
            requestPhoneNumberHint();

        if (method.equals(ConnectConstants.METHOD_CHANGE_ALTERNATE)) {
            title = getString(R.string.connect_phone_title_alternate);
            message = getString(R.string.connect_phone_message_alternate);

            existing = user != null ? user.getAlternatePhone() : null;
        } else {
            existing = user != null ? user.getPrimaryPhone() : existingPhone;
        }

        binding.connectPrimaryPhoneTitle.setText(title);
        binding.connectPrimaryPhoneMessage.setText(message);
        displayNumber(existing);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        checkPhoneNumber();

        KeyboardHelper.showKeyboardOnInput(requireActivity(), binding.connectPrimaryPhoneInput);

    }

    public void requestPhoneNumberHint() {
        GetPhoneNumberHintIntentRequest hintRequest = GetPhoneNumberHintIntentRequest.builder().build();
        Identity.getSignInClient(requireActivity()).getPhoneNumberHintIntent(hintRequest)
                .addOnSuccessListener(new OnSuccessListener<PendingIntent>() {
                    @Override
                    public void onSuccess(PendingIntent pendingIntent) {
                        try {
                            startIntentSenderForResult(pendingIntent.getIntentSender(), ConnectConstants.CREDENTIAL_PICKER_REQUEST, null, 0, 0, 0, null);
                        } catch (IntentSender.SendIntentException e) {
                            e.printStackTrace();
                        }
                    }
                });
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
        ConnectUserRecord user = ConnectDatabaseHelper.getUser(getActivity());
        switch (callingClass) {
            case ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE -> {
                if (success) {
                    directions = ConnectIdPhoneFragmentDirections.actionConnectidPhoneToConnectidRegistration(ConnectConstants.CONNECT_REGISTRATION_MAIN, phone);
                    if (user != null) {
                        user.setPrimaryPhone(phone);
                        ConnectDatabaseHelper.storeUser(getActivity(), user);
                    }
                } else {
                    directions = ConnectIdPhoneFragmentDirections.actionConnectidPhoneToConnectidConsent(ConnectConstants.CONNECT_REGISTRATION_CONSENT);
                }
            }
            case ConnectConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE -> {
                if (success) {
                    user.setAlternatePhone(phone);
                    ConnectDatabaseHelper.storeUser(getActivity(), user);
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectTask.CONNECT_REGISTRATION_CONFIRM_PIN);
                    directions = ConnectIdPhoneFragmentDirections.actionConnectidPhoneToConnectidPin(ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN, phone, "").setRecover(false).setChange(false);
                } else {
                    directions = ConnectIdPhoneFragmentDirections.actionConnectidPhoneToConnectidPin(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_PIN, phone, "").setRecover(false).setChange(true);
                }
            }
            case ConnectConstants.CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE -> {
                if (success) {
                    user.setPrimaryPhone(phone);
                    ConnectDatabaseHelper.storeUser(getActivity(), user);
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectTask.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE);
                }
                directions = ConnectIdPhoneFragmentDirections.actionConnectidPhoneToConnectidPhoneVerify(ConnectConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE, String.format(Locale.getDefault(), "%d",
                        ConnectIdPhoneVerificationFragmnet.MethodRegistrationPrimary), phone, user.getUserId(), user.getPassword(), null).setAllowChange(true);
            }
            case ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE -> {
                if (success) {
                    ConnectIdActivity.recoverPhone = phone;
                    directions = ConnectIdPhoneFragmentDirections.actionConnectidPhoneToConnectidBiometricConfig(ConnectConstants.CONNECT_RECOVERY_CONFIGURE_BIOMETRICS);
                }
            }
            case ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_CHANGE -> {
                directions = ConnectIdPhoneFragmentDirections.actionConnectidPhoneToConnectidPhoneVerify(ConnectConstants.CONNECT_UNLOCK_VERIFY_ALT_PHONE, String.format(Locale.getDefault(), "%d",
                        ConnectIdPhoneVerificationFragmnet.MethodVerifyAlternate), null, user.getUserId(), user.getPassword(), null).setAllowChange(false);

            }
            case ConnectConstants.CONNECT_VERIFY_ALT_PHONE_CHANGE -> {
                if (success) {
                    directions = ConnectIdPhoneFragmentDirections.actionConnectidPhoneToConnectidPhoneVerify(ConnectConstants.CONNECT_VERIFY_ALT_PHONE, String.format(Locale.getDefault(), "%d",
                            ConnectIdPhoneVerificationFragmnet.MethodVerifyAlternate), null, user.getUserId(), user.getPassword(), null).setAllowChange(false);
                } else {
                    directions = ConnectIdPhoneFragmentDirections.actionConnectidPhoneToConnectidMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_VERIFY_ALT_PHONE_MESSAGE, getString(R.string.connect_password_fail_button), getString(R.string.connect_recovery_alt_change_button));
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
        }

        if (fullNumber != null && fullNumber.startsWith("+" + codeText)) {
            fullNumber = fullNumber.substring(codeText.length() + 1);
        }
        skipPhoneNumberCheck = false;
        binding.connectPrimaryPhoneInput.setText(fullNumber);
        skipPhoneNumberCheck = true;
        binding.connectPrimaryPhoneCountryInput.setText(codeText);
        skipPhoneNumberCheck = false;
    }

    public void handleButtonPress() {
        String phone = PhoneNumberHelper.buildPhoneNumber("+" + binding.connectPrimaryPhoneCountryInput.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString());
        ConnectUserRecord user = ConnectManager.getUser(getContext());
        String existing = user != null ? user.getPrimaryPhone() : existingPhone;
        if (method.equals(ConnectConstants.METHOD_CHANGE_ALTERNATE)) {
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
                public void processFailure(int responseCode, IOException e) {
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
                public void processOldApiError() {
                    skipPhoneNumberCheck = false;
                    ConnectNetworkHelper.showOutdatedApiError(getContext());
                }
            };

            //Update the phone number with the server
            boolean isBusy;
            if (method.equals(ConnectConstants.METHOD_CHANGE_ALTERNATE)) {
                isBusy = !ApiConnectId.updateUserProfile(getContext(), user.getUserId(), user.getPassword(),
                        null, phone, callback);
            } else {
                isBusy = !ApiConnectId.changePhone(getContext(), user.getUserId(), user.getPassword(),
                        existing, phone, callback);
            }

            if (isBusy) {
                Toast.makeText(getContext(), R.string.busy_message, Toast.LENGTH_SHORT).show();
            }
        } else {
            finish(true, phone);
        }
    }

    public void checkPhoneNumber() {
        if (!skipPhoneNumberCheck) {
            String phone = PhoneNumberHelper.buildPhoneNumber("+" + binding.connectPrimaryPhoneCountryInput.getText().toString(),
                    binding.connectPrimaryPhoneInput.getText().toString());

            boolean valid = PhoneNumberHelper.isValidPhoneNumber(getContext(), phone);
            ConnectUserRecord user = ConnectManager.getUser(getContext());

            if (valid) {
                String existingPrimary = user != null ? user.getPrimaryPhone() : existingPhone;
                String existingAlternate = user != null ? user.getAlternatePhone() : null;
                switch (method) {
                    case ConnectConstants.METHOD_REGISTER_PRIMARY,
                            ConnectConstants.METHOD_CHANGE_PRIMARY -> {
                        if (existingPrimary != null && existingPrimary.equals(phone)) {
                            binding.connectPrimaryPhoneAvailability.setText("");
                            binding.connectPrimaryPhoneButton.setEnabled(true);
                        } else if (existingAlternate != null && existingAlternate.equals(phone)) {
                            binding.connectPrimaryPhoneAvailability.setText(getString(R.string.connect_phone_not_alt));
                            binding.connectPrimaryPhoneButton.setEnabled(false);
                        } else {
                            //Make sure the number isn't already in use
                            phone = phone.replaceAll("\\+", "%2b");
                            binding.connectPrimaryPhoneAvailability.setText(getString(R.string.connect_phone_checking));
                            binding.connectPrimaryPhoneButton.setEnabled(false);

                            boolean isBusy = !ApiConnectId.checkPhoneAvailable(getContext(), phone,
                                    new IApiCallback() {
                                        @Override
                                        public void processSuccess(int responseCode, InputStream responseData) {
                                            skipPhoneNumberCheck = false;
                                            binding.connectPrimaryPhoneAvailability.setText(getString(R.string.connect_phone_available));
                                            binding.connectPrimaryPhoneButton.setEnabled(true);

                                        }

                                        @Override
                                        public void processFailure(int responseCode, IOException e) {
                                            skipPhoneNumberCheck = false;
                                            if (e != null) {
                                                Logger.exception("Checking phone number", e);
                                            }
                                            binding.connectPrimaryPhoneAvailability.setText(getString(R.string.connect_phone_unavailable));
                                            binding.connectPrimaryPhoneButton.setEnabled(false);
                                        }

                                        @Override
                                        public void processNetworkFailure() {
                                            skipPhoneNumberCheck = false;
                                            binding.connectPrimaryPhoneAvailability.setText(getString(R.string.recovery_network_unavailable));
                                        }

                                        @Override
                                        public void processOldApiError() {
                                            skipPhoneNumberCheck = false;
                                            binding.connectPrimaryPhoneAvailability.setText(getString(R.string.recovery_network_outdated));
                                        }
                                    });

                            if (isBusy) {
                                Toast.makeText(getContext(), R.string.busy_message, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                    case ConnectConstants.METHOD_CHANGE_ALTERNATE -> {
                        if (existingPrimary != null && existingPrimary.equals(phone)) {
                            binding.connectPrimaryPhoneAvailability.setText(getString(R.string.connect_phone_not_primary));
                            binding.connectPrimaryPhoneButton.setEnabled(false);
                        } else {
                            binding.connectPrimaryPhoneAvailability.setText("");
                            binding.connectPrimaryPhoneButton.setEnabled(true);
                        }
                    }
                    case ConnectConstants.METHOD_RECOVER_PRIMARY -> {
                        binding.connectPrimaryPhoneAvailability.setText("");
                        binding.connectPrimaryPhoneButton.setEnabled(true);
                    }
                }
            } else {
                binding.connectPrimaryPhoneAvailability.setText(getString(R.string.connect_phone_invalid));
                binding.connectPrimaryPhoneButton.setEnabled(false);
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }
}