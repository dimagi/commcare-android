package org.commcare.activities.connectId.fragments;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.utils.KeyboardHelper;
import org.commcare.utils.PhoneNumberHelper;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link PhoneFragment#newInstance} factory method to
 * create an instance of getContext() fragment.
 */
public class PhoneFragment extends Fragment {
    private TextView titleTextView;
    private TextView messageTextView;
    private AutoCompleteTextView countryCodeInput;
    private AutoCompleteTextView phoneInput;
    private TextView availabilityTextView;
    private Button button;
    private String method;
    private String existingPhone;

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

    public PhoneFragment() {
        // Required empty public constructor
    }

    public static PhoneFragment newInstance() {
        PhoneFragment fragment = new PhoneFragment();
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
        View view = inflater.inflate(R.layout.screen_connect_primary_phone, container, false);
        titleTextView = view.findViewById(R.id.connect_primary_phone_title);
        messageTextView = view.findViewById(R.id.connect_primary_phone_message);
        countryCodeInput = view.findViewById(R.id.connect_primary_phone_country_input);
        phoneInput = view.findViewById(R.id.connect_primary_phone_input);
        availabilityTextView = view.findViewById(R.id.connect_primary_phone_availability);
        button = view.findViewById(R.id.connect_primary_phone_button);
        countryCodeInput.addTextChangedListener(watcher);
        phoneInput.addTextChangedListener(watcher);
        button.setOnClickListener(v -> handleButtonPress());
        requireActivity().setTitle(getString(R.string.connect_phone_page_title));
//        method = getIntent().getStringExtra(ConnectConstants.METHOD);
        //Special case for initial reg. screen. Remembering phone number before account has been created
//        existingPhone = getIntent().getStringExtra(ConnectConstants.PHONE);
        ConnectUserRecord user = ConnectManager.getUser(getActivity());
        String title = getString(R.string.connect_phone_title_primary);
        String message = getString(R.string.connect_phone_message_primary);

        if (!method.equals(ConnectConstants.METHOD_CHANGE_ALTERNATE))
            PhoneNumberHelper.requestPhoneNumberHint(getActivity());

        if (method.equals(ConnectConstants.METHOD_CHANGE_ALTERNATE)) {
            title = getString(R.string.connect_phone_title_alternate);
            message = getString(R.string.connect_phone_message_alternate);

            existing = user != null ? user.getAlternatePhone() : null;
        } else {
            existing = user != null ? user.getPrimaryPhone() : existingPhone;
        }

        titleTextView.setText(title);
        messageTextView.setText(message);
        displayNumber(existing);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        checkPhoneNumber();

        KeyboardHelper.showKeyboardOnInput(requireActivity(), phoneInput);

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String phone = PhoneNumberHelper.handlePhoneNumberPickerResult(requestCode, resultCode, data, getActivity());
        skipPhoneNumberCheck = false;
        displayNumber(phone);
    }

    public void finish(boolean success, String phone) {
//        Intent intent = new Intent(getIntent());
//
//        intent.putExtra(ConnectConstants.PHONE, phone);
//
//        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
//        finish();
    }

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
        phoneInput.setText(fullNumber);
        skipPhoneNumberCheck = true;
        countryCodeInput.setText(codeText);
        skipPhoneNumberCheck = false;
    }

    public void handleButtonPress() {
        String phone = PhoneNumberHelper.buildPhoneNumber(countryCodeInput.getText().toString(),
                phoneInput.getText().toString());
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
            String phone = PhoneNumberHelper.buildPhoneNumber(countryCodeInput.getText().toString(),
                    phoneInput.getText().toString());

            boolean valid = PhoneNumberHelper.isValidPhoneNumber(getContext(), phone);
            ConnectUserRecord user = ConnectManager.getUser(getContext());

            if (valid) {
                String existingPrimary = user != null ? user.getPrimaryPhone() : existingPhone;
                String existingAlternate = user != null ? user.getAlternatePhone() : null;
                switch (method) {
                    case ConnectConstants.METHOD_REGISTER_PRIMARY,
                            ConnectConstants.METHOD_CHANGE_PRIMARY -> {
                        if (existingPrimary != null && existingPrimary.equals(phone)) {
                            availabilityTextView.setText("");
                            button.setEnabled(true);
                        } else if (existingAlternate != null && existingAlternate.equals(phone)) {
                            availabilityTextView.setText(getString(R.string.connect_phone_not_alt));
                            button.setEnabled(false);
                        } else {
                            //Make sure the number isn't already in use
                            phone = phone.replaceAll("\\+", "%2b");
                            availabilityTextView.setText(getString(R.string.connect_phone_checking));
                            button.setEnabled(false);

                            boolean isBusy = !ApiConnectId.checkPhoneAvailable(getContext(), phone,
                                    new IApiCallback() {
                                        @Override
                                        public void processSuccess(int responseCode, InputStream responseData) {
                                            skipPhoneNumberCheck = false;
                                            availabilityTextView.setText(getString(R.string.connect_phone_available));
                                            button.setEnabled(true);

                                        }

                                        @Override
                                        public void processFailure(int responseCode, IOException e) {
                                            skipPhoneNumberCheck = false;
                                            if (e != null) {
                                                Logger.exception("Checking phone number", e);
                                            }
                                            availabilityTextView.setText(getString(R.string.connect_phone_unavailable));
                                            button.setEnabled(false);
                                        }

                                        @Override
                                        public void processNetworkFailure() {
                                            skipPhoneNumberCheck = false;
                                            availabilityTextView.setText(getString(R.string.recovery_network_unavailable));
                                        }

                                        @Override
                                        public void processOldApiError() {
                                            skipPhoneNumberCheck = false;
                                            availabilityTextView.setText(getString(R.string.recovery_network_outdated));
                                        }
                                    });

                            if (isBusy) {
                                Toast.makeText(getContext(), R.string.busy_message, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                    case ConnectConstants.METHOD_CHANGE_ALTERNATE -> {
                        if (existingPrimary != null && existingPrimary.equals(phone)) {
                            availabilityTextView.setText(getString(R.string.connect_phone_not_primary));
                            button.setEnabled(false);
                        } else {
                            availabilityTextView.setText("");
                            button.setEnabled(true);
                        }
                    }
                    case ConnectConstants.METHOD_RECOVER_PRIMARY -> {
                        availabilityTextView.setText("");
                        button.setEnabled(true);
                    }
                }
            } else {
                availabilityTextView.setText(getString(R.string.connect_phone_invalid));
                button.setEnabled(false);
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }
}