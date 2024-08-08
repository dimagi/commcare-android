package org.commcare.fragments.connectId;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.connect.ConnectConstants;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.utils.KeyboardHelper;
import org.commcare.utils.PhoneNumberHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectIdRecoveryDecisionFragment#newInstance} factory method to
 * create an instance of requireActivity() fragment.
 */
public class ConnectIdRecoveryDecisionFragment extends Fragment {
    private TextView messageTextView;


    private enum ConnectRecoveryState {
        NewOrRecover,
        PhoneOrExtended
    }

    public ConnectRecoveryState state;

    private RelativeLayout phoneBlock;

    private AutoCompleteTextView countryCodeInput;
    private AutoCompleteTextView phoneInput;

    private TextView phoneMessageTextView;

    private Button button1;

    private TextView orText;

    private Button button2;

    NavController navController;

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

    public ConnectIdRecoveryDecisionFragment() {
        // Required empty public constructor
    }

    public static ConnectIdRecoveryDecisionFragment newInstance(String param1, String param2) {
        ConnectIdRecoveryDecisionFragment fragment = new ConnectIdRecoveryDecisionFragment();
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
        // Inflate the layout for requireActivity() fragment
        View view = inflater.inflate(R.layout.screen_connect_recovery_decision, container, false);
        messageTextView = view.findViewById(R.id.connect_recovery_message);
        phoneBlock = view.findViewById(R.id.connect_recovery_phone_block);
        countryCodeInput = view.findViewById(R.id.connect_recovery_phone_country_input);
        phoneInput = view.findViewById(R.id.connect_recovery_phone_input);
        orText = view.findViewById(R.id.connect_recovery_or);
        phoneMessageTextView = view.findViewById(R.id.connect_recovery_phone_message);
        button1 = view.findViewById(R.id.connect_recovery_button_1);
        button2 = view.findViewById(R.id.connect_recovery_button_2);
        countryCodeInput.addTextChangedListener(watcher);
        phoneInput.addTextChangedListener(watcher);
        button1.setOnClickListener(v -> handleButton1Press());
        button2.setOnClickListener(v -> handleButton2Press());
        messageTextView.setText(getString(R.string.connect_recovery_decision_new));
        button1.setText(getString(R.string.connect_recovery_button_new));
        button2.setText(getString(R.string.connect_recovery_button_recover));
        state = ConnectRecoveryState.NewOrRecover;
        getActivity().setTitle(getString(R.string.connect_recovery_title));
        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String phone = PhoneNumberHelper.handlePhoneNumberPickerResult(requestCode, resultCode, data, requireActivity());
        skipPhoneNumberCheck = false;
        displayNumber(phone);
    }

    void displayNumber(String fullNumber) {
        int code = PhoneNumberHelper.getCountryCode(requireActivity());
        if (fullNumber != null && fullNumber.length() > 0) {
            code = PhoneNumberHelper.getCountryCode(requireActivity(), fullNumber);
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
        countryCodeInput.setText(String.format(Locale.getDefault(), "+%d", code));
        skipPhoneNumberCheck = false;

    }

    public void finish(boolean createNew, String phone) {

        NavDirections directions;
        if (createNew) {
            directions = ConnectIdRecoveryDecisionFragmentDirections.actionConnectidRecoveryDecisionToConnectidConsent(ConnectConstants.CONNECT_REGISTRATION_CONSENT);
        } else {
            ConnectConstants.recoverPhone=phone;
            directions = ConnectIdRecoveryDecisionFragmentDirections.actionConnectidRecoveryDecisionToConnectidBiometricConfig(phone,ConnectConstants.CONNECT_RECOVERY_CONFIGURE_BIOMETRICS);
        }
        Navigation.findNavController(button1).navigate(directions);
    }

    public void handleButton1Press() {
        switch (state) {
            case NewOrRecover -> finish(true, null);
            case PhoneOrExtended ->
                    finish(false, PhoneNumberHelper.buildPhoneNumber(countryCodeInput.getText().toString(),
                            phoneInput.getText().toString()));
        }
    }

    public void handleButton2Press() {
        switch (state) {
            case NewOrRecover -> {
                state = ConnectRecoveryState.PhoneOrExtended;
                setPhoneInputVisible(true);
                requireActivity().setTitle(getString(R.string.connect_recovery_title2));
                PhoneNumberHelper.requestPhoneNumberHint(requireActivity());
                int code = PhoneNumberHelper.getCountryCode(requireActivity());
                countryCodeInput.setText(String.format(Locale.getDefault(), "+%d", code));

                requestInputFocus();
                messageTextView.setText(getString(R.string.connect_recovery_decision_phone));
                button1.setText(getString(R.string.connect_recovery_button_phone));
                setButton2Visible(false);
            }
            case PhoneOrExtended -> {
                Toast.makeText(requireActivity(), "Not ready yet!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void checkPhoneNumber() {
        if (!skipPhoneNumberCheck) {
            String phone = PhoneNumberHelper.buildPhoneNumber(countryCodeInput.getText().toString(),
                    phoneInput.getText().toString());

            boolean valid = PhoneNumberHelper.isValidPhoneNumber(requireActivity(), phone);

            if (valid) {
                phone = phone.replaceAll("\\+", "%2b");
                phoneMessageTextView.setText(getString(R.string.connect_phone_checking));
                button1.setEnabled(false);

                boolean isBusy = !ApiConnectId.checkPhoneAvailable(requireActivity(), phone,
                        new IApiCallback() {
                            @Override
                            public void processSuccess(int responseCode, InputStream responseData) {
                                phoneMessageTextView.setText(getString(R.string.connect_phone_not_found));
                                button1.setEnabled(false);
                                skipPhoneNumberCheck = false;
                            }

                            @Override
                            public void processFailure(int responseCode, IOException e) {
                                skipPhoneNumberCheck = false;
                                phoneMessageTextView.setText("");
                                button1.setEnabled(true);
                            }

                            @Override
                            public void processNetworkFailure() {
                                skipPhoneNumberCheck = false;
                                phoneMessageTextView.setText(getString(R.string.recovery_network_unavailable));
                                button1.setEnabled(false);
                            }

                            @Override
                            public void processOldApiError() {
                                skipPhoneNumberCheck = false;
                                phoneMessageTextView.setText(getString(R.string.recovery_network_outdated));
                                button1.setEnabled(false);
                            }
                        });

                if (isBusy) {
                    Toast.makeText(requireActivity(), R.string.busy_message, Toast.LENGTH_SHORT).show();
                }
            } else {
                phoneMessageTextView.setText(getString(R.string.connect_phone_invalid));
                button1.setEnabled(false);
            }
        }
    }

    public void setButton2Visible(boolean visible) {
        button2.setVisibility(visible ? View.VISIBLE : View.GONE);
        orText.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void requestInputFocus() {
        KeyboardHelper.showKeyboardOnInput(requireActivity(), phoneInput);
    }

    public void setPhoneInputVisible(boolean visible) {
        phoneBlock.setVisibility(visible ? View.VISIBLE : View.GONE);
        phoneMessageTextView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

}